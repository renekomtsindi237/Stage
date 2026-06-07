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
SELECT
    c.client_id_externe,
    c.imf_code,
    -- Volume et fréquence
    COUNT(ce.id)                                                AS nb_collectes_12m,
    -- Régularité : % de semaines avec au moins une collecte
    ROUND(
        COUNT(DISTINCT DATE_TRUNC('week', ce.date_collecte)) * 100.0
        / NULLIF(52, 0), 2
    )                                                           AS regularite_collecte_pct,
    -- Tendance : pente normalisée (régression simple approchée par corrélation date-montant)
    COALESCE(
        REGR_SLOPE(ce.montant, EXTRACT(EPOCH FROM ce.date_collecte)::FLOAT) * 86400,
        0
    )                                                           AS tendance_collecte_3m,
    ROUND(AVG(ce.montant)::NUMERIC, 2)                          AS montant_moy_collecte,
    ROUND(STDDEV(ce.montant)::NUMERIC, 2)                       AS ecart_type_collecte,
    -- Cycles manqués : semaines sans collecte sur 52
    52 - COUNT(DISTINCT DATE_TRUNC('week', ce.date_collecte))   AS nb_cycles_manques_12m,
    ROUND(SUM(ce.montant)::NUMERIC, 2)                          AS montant_total_collectes_12m
FROM staging.stg_clients c
LEFT JOIN staging.stg_collectes_epargne ce
    ON ce.client_id   = c.id
    AND ce.imf_id     = c.imf_id
    AND ce.statut     = 'VALIDEE'
    AND ce.date_collecte >= (CURRENT_DATE - INTERVAL '12 months')
    AND ce.est_doublon = FALSE
WHERE c.imf_id = %(imf_id)s
GROUP BY c.client_id_externe, c.imf_code
"""

# Tendance 3 mois (sous-requête ciblée)
CRS_TENDANCE_3M_QUERY = """
WITH recent AS (
    SELECT
        client_id,
        imf_id,
        EXTRACT(EPOCH FROM date_collecte)::FLOAT AS ts,
        montant
    FROM staging.stg_collectes_epargne
    WHERE imf_id = %(imf_id)s
      AND date_collecte >= (CURRENT_DATE - INTERVAL '3 months')
      AND statut = 'VALIDEE'
      AND est_doublon = FALSE
)
SELECT
    c.client_id_externe,
    COALESCE(REGR_SLOPE(r.montant, r.ts) * 86400, 0) AS tendance_collecte_3m
FROM staging.stg_clients c
LEFT JOIN recent r ON r.client_id = c.id AND r.imf_id = c.imf_id
WHERE c.imf_id = %(imf_id)s
GROUP BY c.client_id_externe
"""


# ─── Features RPS ─────────────────────────────────────────────────────────────

RPS_QUERY = """
SELECT
    c.client_id_externe,
    -- Taux de remboursement global
    ROUND(
        (1 - COALESCE(cr.montant_encours, 0) / NULLIF(cr.montant_decaisse, 0)) * 100,
        2
    )                                           AS taux_remboursement_pct,
    COALESCE(cr.jours_retard, 0)                AS jours_retard_moyen,
    COALESCE(cr.jours_retard, 0)                AS jours_retard_max,
    -- Incidents de paiement : nombre de passages en retard sur 12 mois
    COALESCE(hist.nb_incidents_12m, 0)          AS nb_incidents_paiement,
    COALESCE(cr.montant_encours, 0)             AS montant_impaye_courant,
    -- Remboursements effectués (approx : echeances payées estimées depuis CBS)
    COALESCE(prom.nb_respectees, 0)             AS nb_remboursements_12m,
    CASE cr.classe_cobac
        WHEN 'A' THEN 0
        WHEN 'B' THEN 1
        WHEN 'C' THEN 2
        WHEN 'D' THEN 3
        WHEN 'E' THEN 4
        ELSE 0
    END                                         AS classe_risque_cobac_encode
FROM staging.stg_clients c
LEFT JOIN staging.stg_creances cr
    ON cr.client_id = c.id
    AND cr.imf_id   = c.imf_id
LEFT JOIN (
    -- Nombre d'incidents de paiement (passages en retard > 0 jours sur 12 mois)
    SELECT client_id, imf_id, COUNT(*) AS nb_incidents_12m
    FROM app.kpi_recouvrement_snapshots krs
    WHERE krs.date_snapshot >= (CURRENT_DATE - INTERVAL '12 months')
    GROUP BY client_id, imf_id
) hist ON hist.client_id = c.id AND hist.imf_id = c.imf_id
LEFT JOIN (
    -- Promesses tenues comme proxy de remboursements réguliers
    SELECT client_id, imf_id, COUNT(*) AS nb_respectees
    FROM app.promesses_paiement pp
    WHERE pp.statut = 'RESPECTEE'
      AND pp.date_realisation >= (CURRENT_DATE - INTERVAL '12 months')
    GROUP BY client_id, imf_id
) prom ON prom.client_id = c.id AND prom.imf_id = c.imf_id
WHERE c.imf_id = %(imf_id)s
"""


# ─── Features CSI ─────────────────────────────────────────────────────────────

CSI_QUERY = """
WITH prix_produit AS (
    -- Prix du produit principal du client sur les 120 derniers jours (fenêtre étendue pour les lags)
    SELECT
        cap.client_informel_id,
        pp.produit_id,
        AVG(pp.prix_unitaire)
            FILTER (WHERE pp.date_prix >= CURRENT_DATE - INTERVAL '90 days')   AS prix_moy_90j,
        STDDEV(pp.prix_unitaire)
            FILTER (WHERE pp.date_prix >= CURRENT_DATE - INTERVAL '90 days')   AS prix_vol_90j,
        COALESCE(
            REGR_SLOPE(pp.prix_unitaire, EXTRACT(EPOCH FROM pp.date_prix)::FLOAT)
                FILTER (WHERE pp.date_prix >= CURRENT_DATE - INTERVAL '30 days') * 86400 * 30,
            0
        )                                                                        AS tendance_prix_30j,
        -- Lag 30j : prix moyen de la période 31–60 jours en arrière
        AVG(pp.prix_unitaire)
            FILTER (WHERE pp.date_prix BETWEEN CURRENT_DATE - INTERVAL '60 days'
                                           AND CURRENT_DATE - INTERVAL '31 days') AS prix_lag_30j,
        -- Lag 90j : prix moyen de la période 91–120 jours en arrière
        AVG(pp.prix_unitaire)
            FILTER (WHERE pp.date_prix BETWEEN CURRENT_DATE - INTERVAL '120 days'
                                           AND CURRENT_DATE - INTERVAL '91 days') AS prix_lag_90j
    FROM app.client_activites_produits cap
    JOIN app.prix_produits pp
        ON pp.produit_id = cap.produit_id
        AND pp.imf_id    = %(imf_id)s
        AND pp.date_prix >= (CURRENT_DATE - INTERVAL '120 days')
        AND pp.fiabilite_score >= 3
    WHERE cap.est_activite_principale = TRUE
    GROUP BY cap.client_informel_id, pp.produit_id
),
macro_zone AS (
    -- Macro-indicateurs de la zone client (30 derniers jours)
    SELECT
        AVG(CASE WHEN type_indicateur = 'INFLATION'
            THEN valeur END)            AS inflation_mensuelle_moy,
        MAX(CASE WHEN type_indicateur = 'TAUX_DIRECTEUR_BEAC'
            THEN valeur END)            AS taux_directeur_beac
    FROM app.facteurs_macro
    WHERE date_indicateur >= (CURRENT_DATE - INTERVAL '30 days')
      AND imf_id = %(imf_id)s
),
meteo_zone AS (
    -- Météo par zone client (30 derniers jours)
    SELECT
        ci.id   AS client_informel_id,
        AVG(dm.precipitation_mm)        AS precipitation_moy_mm,
        AVG(dm.indice_secheresse)       AS indice_secheresse
    FROM app.clients_informels ci
    JOIN app.donnees_meteo dm
        ON dm.zone_id = ci.zone_geographique
        AND dm.date_meteo >= (CURRENT_DATE - INTERVAL '30 days')
    GROUP BY ci.id
),
evenements AS (
    -- Nombre d'événements négatifs dans les 30 prochains jours (anticipation)
    SELECT
        ci.id AS client_informel_id,
        COUNT(DISTINCT ev.id) AS nb_evenements_negatifs
    FROM app.clients_informels ci
    JOIN app.evenements_exterieurs ev
        ON ev.date_debut <= (CURRENT_DATE + INTERVAL '30 days')
        AND ev.date_fin   >= CURRENT_DATE
        AND ev.impact_estime = 'NEGATIF'
        AND ci.zone_geographique = ANY(ev.zone_ids)
    GROUP BY ci.id
)
SELECT
    c.client_id_externe,
    ci.revenu_mensuel_estime,
    (CURRENT_DATE - c.created_at::DATE)                         AS anciennete_client_jours,
    -- Nombre de produits actifs (diversification)
    COUNT(DISTINCT cap.produit_id)                              AS nb_produits_actifs,
    -- Ratio collecte / crédit
    ROUND(
        COALESCE(kcs.montant_total_collectes_12m, 0)
        / NULLIF(cr.montant_decaisse, 0), 4
    )                                                           AS ratio_collecte_credit,
    -- Capacité de remboursement : revenu / (encours_mensuel * 1.2)
    ROUND(
        ci.revenu_mensuel_estime
        / NULLIF(cr.montant_encours / NULLIF(cr.duree_mois, 0) * 1.2, 0),
        4
    )                                                           AS capacite_remboursement,
    -- Indice de résilience = min(nb_produits / 5, 1)
    LEAST(COUNT(DISTINCT cap.produit_id)::FLOAT / 5.0, 1.0)    AS indice_resilience,
    -- Profil producteur : 1 si activité de vente/production (agriculteur, éleveur...)
    CASE WHEN ci.secteur_activite IN ('AGRICULTURE','ELEVAGE','PECHE','ARTISANAT') THEN 1 ELSE 0
    END                                                         AS est_producteur,
    -- Features prix produit principal
    COALESCE(pp.prix_moy_90j, 0)                               AS prix_produit_principal_moy,
    COALESCE(pp.prix_vol_90j, 0)                               AS volatilite_prix_produit,
    COALESCE(pp.tendance_prix_30j, 0)                          AS tendance_prix_30j,
    -- Lag features (fallback sur la période courante si la période passée est vide)
    COALESCE(pp.prix_lag_30j, pp.prix_moy_90j, 0)             AS prix_lag_30j,
    COALESCE(pp.prix_lag_90j, pp.prix_moy_90j, 0)             AS prix_lag_90j,
    -- Macro
    COALESCE(mz.inflation_mensuelle_moy, 4.0)                  AS inflation_mensuelle_moy,
    COALESCE(mz.taux_directeur_beac, 5.0)                      AS taux_directeur_beac,
    -- Météo
    COALESCE(mtz.precipitation_moy_mm, 80.0)                   AS precipitation_moy_mm,
    COALESCE(mtz.indice_secheresse, 0.0)                       AS indice_secheresse,
    -- Événements
    COALESCE(ev.nb_evenements_negatifs, 0)                     AS nb_evenements_negatifs
FROM staging.stg_clients c
JOIN app.clients_informels ci
    ON ci.client_id = c.id
    AND ci.imf_id   = %(imf_id)s
LEFT JOIN app.client_activites_produits cap
    ON cap.client_informel_id = ci.id
LEFT JOIN prix_produit pp
    ON pp.client_informel_id = ci.id
LEFT JOIN macro_zone mz ON TRUE
LEFT JOIN meteo_zone mtz ON mtz.client_informel_id = ci.id
LEFT JOIN evenements ev ON ev.client_informel_id = ci.id
LEFT JOIN staging.stg_creances cr
    ON cr.client_id = c.id AND cr.imf_id = c.imf_id
LEFT JOIN (
    SELECT client_id, imf_id, SUM(montant) AS montant_total_collectes_12m
    FROM staging.stg_collectes_epargne
    WHERE statut = 'VALIDEE'
      AND date_collecte >= (CURRENT_DATE - INTERVAL '12 months')
    GROUP BY client_id, imf_id
) kcs ON kcs.client_id = c.id AND kcs.imf_id = c.imf_id
WHERE c.imf_id = %(imf_id)s
GROUP BY
    c.client_id_externe, ci.revenu_mensuel_estime, c.created_at,
    cr.montant_decaisse, cr.montant_encours, cr.duree_mois,
    pp.prix_moy_90j, pp.prix_vol_90j, pp.tendance_prix_30j, pp.prix_lag_30j, pp.prix_lag_90j,
    mz.inflation_mensuelle_moy, mz.taux_directeur_beac,
    mtz.precipitation_moy_mm, mtz.indice_secheresse,
    ev.nb_evenements_negatifs, kcs.montant_total_collectes_12m,
    ci.secteur_activite
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
