"""
feature_engineering.py — Assemblage du feature store ML.

Ce module construit le DataFrame de features pour le modèle MCRS depuis PostgreSQL.
Il est appelé par les tâches Airflow du dag_ml_scoring et dag_ml_training.

Les features sont organisées en trois groupes :
- CRS : comportement de collecte d'épargne
- RPS : historique de remboursement et créances
- CSI : facteurs externes (prix produits, météo, macro)

Conception
----------
- Toutes les requêtes sont paramétrées (protection injection SQL).
- Les imf_ids sont isolées — jamais de données cross-tenant.
- Les features sont normalisées à la source (pas dans le modèle) pour cohérence.
- Les features manquantes sont explicitement signalées dans les logs.
"""

from __future__ import annotations

import logging
from datetime import date

import pandas as pd

from pipeline.src.database import readonly_session

logger = logging.getLogger(__name__)


# ─── Features CRS ─────────────────────────────────────────────────────────────

CRS_QUERY = """
-- staging.stg_clients dépend de raw.export_cbs (jamais alimenté, aucune
-- ingestion CBS réelle configurée) : ne peut jamais servir de table client
-- pilote. app.clients_informels est la table client réelle ; jointe à
-- app.imf pour imf_code (clients_informels ne porte que imf_id). De même,
-- staging.stg_collectes_epargne existe réellement (alimentée via
-- app.collectes_terrain) mais avec des noms de colonnes différents :
-- client_id_externe (pas client_id), imf_code (pas imf_id),
-- montant_collecte (pas montant), statut_validation='VALIDE' (pas
-- statut='VALIDEE').
SELECT
    ci.client_id_externe,
    i.code                                                       AS imf_code,
    COUNT(ce.hash_sha256)                                        AS nb_collectes_12m,
    ROUND(
        COUNT(DISTINCT DATE_TRUNC('week', ce.date_collecte)) * 100.0
        / NULLIF(52, 0), 2
    )                                                           AS regularite_collecte_pct,
    COALESCE(
        REGR_SLOPE(ce.montant_collecte, EXTRACT(EPOCH FROM ce.date_collecte)::FLOAT) * 86400,
        0
    )                                                           AS tendance_collecte_3m,
    ROUND(AVG(ce.montant_collecte)::NUMERIC, 2)                 AS montant_moy_collecte,
    ROUND(STDDEV(ce.montant_collecte)::NUMERIC, 2)              AS ecart_type_collecte,
    52 - COUNT(DISTINCT DATE_TRUNC('week', ce.date_collecte))   AS nb_cycles_manques_12m,
    ROUND(SUM(ce.montant_collecte)::NUMERIC, 2)                 AS montant_total_collectes_12m
FROM app.clients_informels ci
JOIN app.imf i ON i.id = ci.imf_id
LEFT JOIN staging.stg_collectes_epargne ce
    ON ce.client_id_externe = ci.client_id_externe
    AND ce.imf_code          = i.code
    AND ce.statut_validation = 'VALIDE'
    AND ce.date_collecte    >= (CURRENT_DATE - INTERVAL '12 months')
    AND NOT ce.est_doublon
WHERE ci.imf_id = %(imf_id)s
GROUP BY ci.client_id_externe, i.code
"""

# Tendance 3 mois (sous-requête ciblée)
CRS_TENDANCE_3M_QUERY = """
WITH recent AS (
    SELECT
        client_id_externe,
        imf_code,
        EXTRACT(EPOCH FROM date_collecte)::FLOAT AS ts,
        montant_collecte
    FROM staging.stg_collectes_epargne
    WHERE date_collecte    >= (CURRENT_DATE - INTERVAL '3 months')
      AND statut_validation = 'VALIDE'
      AND NOT est_doublon
)
SELECT
    ci.client_id_externe,
    COALESCE(REGR_SLOPE(r.montant_collecte, r.ts) * 86400, 0) AS tendance_collecte_3m
FROM app.clients_informels ci
JOIN app.imf i ON i.id = ci.imf_id
LEFT JOIN recent r ON r.client_id_externe = ci.client_id_externe AND r.imf_code = i.code
WHERE ci.imf_id = %(imf_id)s
GROUP BY ci.client_id_externe
"""


# ─── Features RPS ─────────────────────────────────────────────────────────────

RPS_QUERY = """
-- Même remède que CRS_QUERY : app.clients_informels comme pilote (pas
-- staging.stg_clients). staging.stg_creances existe réellement (alimentée
-- via des données CBS déjà en base) mais avec id_client/imf_code (pas
-- client_id/imf_id) et montant_initial/montant_impaye/classe_risque_cobac
-- (pas montant_decaisse/montant_encours/classe_cobac) — mêmes noms que
-- int_profil_recouvrement_client.sql (dbt), qui traite déjà id_client comme
-- équivalent à client_id_externe. app.kpi_recouvrement_snapshots est un
-- agrégat de PORTEFEUILLE (imf_id/agence_id/date_calcul) sans client_id —
-- ne peut structurellement pas fournir un nb_incidents_paiement par client,
-- mis à 0 plutôt que de référencer une jointure impossible.
-- app.promesses_paiement se rattache par creance_id -> app.creances
-- (client_id_externe réel), pas par un client_id direct.
SELECT
    ci.client_id_externe,
    ROUND(AVG(cr.montant_rembourse / NULLIF(cr.montant_initial, 0)) * 100, 2) AS taux_remboursement_pct,
    COALESCE(AVG(cr.jours_retard), 0)          AS jours_retard_moyen,
    COALESCE(MAX(cr.jours_retard), 0)          AS jours_retard_max,
    0                                           AS nb_incidents_paiement,
    COALESCE(SUM(cr.montant_impaye), 0)         AS montant_impaye_courant,
    COALESCE(MAX(prom.nb_respectees), 0)        AS nb_remboursements_12m,
    CASE MAX(cr.classe_risque_cobac)
        WHEN 'A' THEN 0
        WHEN 'B' THEN 1
        WHEN 'C' THEN 2
        WHEN 'D' THEN 3
        WHEN 'E' THEN 4
        ELSE 0
    END                                         AS classe_risque_cobac_encode
FROM app.clients_informels ci
JOIN app.imf i ON i.id = ci.imf_id
LEFT JOIN staging.stg_creances cr
    ON cr.id_client = ci.client_id_externe
    AND cr.imf_code  = i.code
LEFT JOIN (
    -- Promesses honorées comme proxy de remboursements réguliers
    SELECT cr2.client_id_externe, cr2.imf_id, COUNT(*) AS nb_respectees
    FROM app.promesses_paiement pp
    JOIN app.creances cr2 ON cr2.id = pp.creance_id
    WHERE pp.statut = 'HONOREE'
      AND pp.date_reglement >= (CURRENT_DATE - INTERVAL '12 months')
    GROUP BY cr2.client_id_externe, cr2.imf_id
) prom ON prom.client_id_externe = ci.client_id_externe AND prom.imf_id = ci.imf_id
WHERE ci.imf_id = %(imf_id)s
GROUP BY ci.client_id_externe
"""


# ─── Features CSI ─────────────────────────────────────────────────────────────

CSI_QUERY = """
-- Corrections apportées (même campagne que CRS_QUERY/RPS_QUERY) :
-- - app.prix_produits n'existe pas (aucune ingestion de prix marché
--   configurée à ce jour, cf. feat_client_externe.sql) — prix_produit videe.
-- - app.facteurs_macro n'a pas de colonne imf_id (indicateurs nationaux,
--   pas par IMF) ni date_indicateur (réel: date_observation).
-- - app.donnees_meteo : date_observation (pas date_meteo),
--   indice_secheresse est un VARCHAR enum (pas numérique, non moyennable) —
--   valeur la plus récente retenue plutôt qu'une moyenne.
-- - app.clients_informels.zone_id (pas zone_geographique),
--   .secteur_principal (pas secteur_activite, enum 'AGRICOLE' pas
--   'AGRICULTURE').
-- - app.client_activites_produits.client_id/.est_produit_principal (pas
--   client_informel_id/est_activite_principale).
-- - app.evenements_exterieurs.impact_collecte (pas impact_estime),
--   zone_id singulier nullable = national (pas zone_ids ARRAY).
-- - staging.stg_creances/stg_collectes_epargne : mêmes corrections
--   id_client/imf_code/montant_collecte/statut_validation que RPS_QUERY.
--   stg_creances n'a pas de duree_mois -> capacité de remboursement
--   simplifiée (revenu - impayé), même formule que features_client.sql (dbt).
WITH macro_zone AS (
    SELECT
        AVG(valeur) FILTER (WHERE indicateur = 'TAUX_INFLATION_MENSUEL') AS inflation_mensuelle_moy,
        MAX(valeur) FILTER (WHERE indicateur = 'TAUX_DIRECTEUR_BEAC')    AS taux_directeur_beac
    FROM app.facteurs_macro
    WHERE date_observation >= (CURRENT_DATE - INTERVAL '30 days')
),
meteo_zone AS (
    SELECT DISTINCT ON (ci.id)
        ci.id AS client_informel_id,
        dm.precipitation_mm,
        dm.indice_secheresse
    FROM app.clients_informels ci
    JOIN app.donnees_meteo dm
        ON dm.zone_id = ci.zone_id
        AND dm.date_observation >= (CURRENT_DATE - INTERVAL '30 days')
    ORDER BY ci.id, dm.date_observation DESC
),
evenements AS (
    SELECT
        ci.id AS client_informel_id,
        COUNT(DISTINCT ev.id) AS nb_evenements_negatifs
    FROM app.clients_informels ci
    JOIN app.evenements_exterieurs ev
        ON ev.date_debut <= (CURRENT_DATE + INTERVAL '30 days')
        AND ev.date_fin   >= CURRENT_DATE
        AND ev.impact_collecte = 'NEGATIF'
        AND (ev.zone_id IS NULL OR ev.zone_id = ci.zone_id)
    GROUP BY ci.id
),
creances_agg AS (
    SELECT id_client, imf_code,
           SUM(montant_impaye) AS montant_impaye_total
    FROM staging.stg_creances
    GROUP BY id_client, imf_code
),
collectes_agg AS (
    SELECT client_id_externe, imf_code,
           SUM(montant_collecte) AS montant_total_collectes_12m
    FROM staging.stg_collectes_epargne
    WHERE statut_validation = 'VALIDE'
      AND date_collecte >= (CURRENT_DATE - INTERVAL '12 months')
    GROUP BY client_id_externe, imf_code
)
SELECT
    ci.client_id_externe,
    ci.revenu_mensuel_estime,
    (CURRENT_DATE - ci.created_at::DATE)                        AS anciennete_client_jours,
    COUNT(DISTINCT cap.produit_id)                              AS nb_produits_actifs,
    ROUND(
        COALESCE(kcs.montant_total_collectes_12m, 0)
        / NULLIF(cra.montant_impaye_total, 0), 4
    )                                                           AS ratio_collecte_credit,
    GREATEST(
        COALESCE(ci.revenu_mensuel_estime, 0) - COALESCE(cra.montant_impaye_total, 0),
        0
    )                                                           AS capacite_remboursement,
    LEAST(COUNT(DISTINCT cap.produit_id)::FLOAT / 5.0, 1.0)    AS indice_resilience,
    CASE WHEN ci.secteur_principal IN ('AGRICOLE','ELEVAGE','PECHE','ARTISANAT') THEN 1 ELSE 0
    END                                                         AS est_producteur,
    -- Prix produit : indisponible (pas d'ingestion prix marché configurée)
    0::NUMERIC AS prix_produit_principal_moy,
    0::NUMERIC AS volatilite_prix_produit,
    0::NUMERIC AS tendance_prix_30j,
    0::NUMERIC AS prix_lag_30j,
    0::NUMERIC AS prix_lag_90j,
    COALESCE(mz.inflation_mensuelle_moy, 4.0)                  AS inflation_mensuelle_moy,
    COALESCE(mz.taux_directeur_beac, 5.0)                      AS taux_directeur_beac,
    COALESCE(mtz.precipitation_mm, 80.0)                       AS precipitation_moy_mm,
    -- mcrs_model.py attend un indice de type Palmer DSI (float, négatif =
    -- sécheresse, cf. ALL_FEATURES/FEATURE_DEFAULTS) — pas la chaîne
    -- app.donnees_meteo.indice_secheresse (VARCHAR enum), encodée ici.
    CASE COALESCE(mtz.indice_secheresse, 'NORMAL')
        WHEN 'SECHERESSE_LEGERE'  THEN -1.0
        WHEN 'SECHERESSE_MODEREE' THEN -2.0
        WHEN 'SECHERESSE_SEVERE'  THEN -3.0
        ELSE 0.0
    END                                                         AS indice_secheresse,
    COALESCE(ev.nb_evenements_negatifs, 0)                     AS nb_evenements_negatifs
FROM app.clients_informels ci
JOIN app.imf i ON i.id = ci.imf_id
LEFT JOIN app.client_activites_produits cap
    ON cap.client_id = ci.id
LEFT JOIN meteo_zone mtz ON mtz.client_informel_id = ci.id
LEFT JOIN evenements ev ON ev.client_informel_id = ci.id
LEFT JOIN creances_agg cra
    ON cra.id_client = ci.client_id_externe AND cra.imf_code = i.code
LEFT JOIN collectes_agg kcs
    ON kcs.client_id_externe = ci.client_id_externe AND kcs.imf_code = i.code
CROSS JOIN macro_zone mz
WHERE ci.imf_id = %(imf_id)s
GROUP BY
    ci.client_id_externe, ci.revenu_mensuel_estime, ci.created_at,
    cra.montant_impaye_total,
    mz.inflation_mensuelle_moy, mz.taux_directeur_beac,
    mtz.precipitation_mm, mtz.indice_secheresse,
    ev.nb_evenements_negatifs, kcs.montant_total_collectes_12m,
    ci.secteur_principal
"""


# ─── Requête labels (pour entraînement) ──────────────────────────────────────

LABELS_QUERY = """
SELECT
    c.client_id_externe,
    c.imf_code,
    -- Label : 1 si le client passe en COBAC C+ dans les 90 jours suivant date_reference
    CASE WHEN cr_future.classe_cobac IN ('C','D','E') THEN 1 ELSE 0 END AS defaut_90j,
    snap.date_snapshot AS date_reference
FROM staging.stg_clients c
JOIN app.kpi_recouvrement_snapshots snap
    ON snap.imf_id = c.imf_id
JOIN staging.stg_creances cr_future
    ON cr_future.client_id = c.id
    AND cr_future.imf_id   = c.imf_id
    -- Créance dans la fenêtre future de 90 jours
WHERE c.imf_id = %(imf_id)s
  AND snap.date_snapshot >= %(date_debut)s
  AND snap.date_snapshot <= %(date_fin)s
"""


# ─── Fonctions publiques ──────────────────────────────────────────────────────


def construire_features_scoring(imf_id: int) -> pd.DataFrame:
    """
    Construit le DataFrame de features complet pour le scoring journalier.

    Jointure CRS + RPS + CSI pour tous les clients actifs de l'IMF.
    Les features manquantes sont renseignées avec les médianes sectorielles.

    Returns
    -------
    DataFrame avec les colonnes : client_id_externe, imf_code, + ALL_FEATURES
    """
    logger.info("Construction features MCRS pour imf_id=%d", imf_id)

    with readonly_session() as cur:
        # CRS
        cur.execute(CRS_QUERY, {"imf_id": imf_id})
        df_crs = pd.DataFrame(cur.fetchall())

        # Tendance 3m (séparée pour performance)
        cur.execute(CRS_TENDANCE_3M_QUERY, {"imf_id": imf_id})
        df_tendance = pd.DataFrame(cur.fetchall())

        # RPS
        cur.execute(RPS_QUERY, {"imf_id": imf_id})
        df_rps = pd.DataFrame(cur.fetchall())

        # CSI
        cur.execute(CSI_QUERY, {"imf_id": imf_id})
        df_csi = pd.DataFrame(cur.fetchall())

    # Merge tendance dans CRS
    if not df_tendance.empty and not df_crs.empty:
        df_crs = df_crs.drop(columns=["tendance_collecte_3m"], errors="ignore")
        df_crs = df_crs.merge(df_tendance, on="client_id_externe", how="left")

    # Jointure finale
    df = df_crs.copy()
    if not df_rps.empty:
        df = df.merge(df_rps, on="client_id_externe", how="left")
    if not df_csi.empty:
        df = df.merge(df_csi, on="client_id_externe", how="left")

    # Ajouter imf_code si absent
    if "imf_code" not in df.columns:
        df["imf_code"] = str(imf_id)

    n_total = len(df)
    n_complet = df.dropna(
        subset=["regularite_collecte_pct", "taux_remboursement_pct"]
    ).shape[0]
    logger.info(
        "Features MCRS construites : %d clients (%d avec features complètes)",
        n_total,
        n_complet,
    )
    return df


def construire_features_entrainement(
    imf_id: int,
    date_debut: date,
    date_fin: date,
) -> tuple[pd.DataFrame, pd.Series, pd.Series]:
    """
    Construit le dataset d'entraînement avec les labels défaut.

    Returns
    -------
    X     : DataFrame features (sans client_id, imf_code, date)
    y     : Série binaire (1 = défaut 90j)
    dates : Série datetime (pour le split walk-forward)
    """
    logger.info(
        "Construction dataset entraînement imf_id=%d, période=%s→%s",
        imf_id,
        date_debut,
        date_fin,
    )

    with readonly_session() as cur:
        cur.execute(
            LABELS_QUERY,
            {
                "imf_id": imf_id,
                "date_debut": date_debut.isoformat(),
                "date_fin": date_fin.isoformat(),
            },
        )
        df_labels = pd.DataFrame(cur.fetchall())

    if df_labels.empty:
        raise ValueError(
            f"Aucune donnée d'entraînement pour imf_id={imf_id} sur la période donnée"
        )

    # Construire les features pour les clients dans les labels
    df_features = construire_features_scoring(imf_id)

    df = df_labels.merge(df_features, on="client_id_externe", how="left")
    y = df["defaut_90j"].astype(int)
    dates = pd.to_datetime(df["date_reference"])

    taux = y.mean() * 100
    logger.info(
        "Dataset entraînement prêt : %d lignes, %.1f%% défauts",
        len(df),
        taux,
    )

    meta_cols = {"client_id_externe", "imf_code", "defaut_90j", "date_reference"}
    X = df.drop(columns=[c for c in meta_cols if c in df.columns])
    return X, y, dates


def reconstruire_imf_ids_actifs() -> list[int]:
    """Retourne la liste des imf_ids avec des créances actives."""
    with readonly_session() as cur:
        cur.execute(
            "SELECT DISTINCT imf_id FROM app.creances WHERE statut NOT IN ('SOLDEE', 'RADIEE', 'IRRECOVERABLE')"
        )
        return [row["imf_id"] for row in cur.fetchall()]
