"""
collecte_utils.py — Synchronisation, validation et KPIs des collectes d'épargne.

Appelé par dag_collecte_epargne (toutes les 2h) pour :
1. Ingérer les collectes créées dans app.collectes_epargne depuis la dernière exécution.
2. Valider (montant, GPS, doublon) et déposer en staging.
3. Enrichir avec le cycle actif et l'agent.
4. Calculer les KPIs (taux de réalisation, progression du cycle).
5. Vérifier les objectifs de fin de cycle.
"""
from __future__ import annotations

import logging
import math
from datetime import datetime, timezone

from pipeline.src.database import db_session, readonly_session

logger = logging.getLogger(__name__)

# Seuils de validation par défaut
_MONTANT_MIN_DEFAULT     = 500.0       # FCFA
_MONTANT_MAX_DEFAULT     = 5_000_000.0 # FCFA
_RAYON_GPS_MAX_DEFAULT   = 50.0        # km (zone de collecte max)
_EARTH_RADIUS_KM         = 6_371.0


def sync_collectes_depuis_app(**ctx) -> dict:
    """
    Copie les nouvelles collectes de app.collectes_epargne → staging.stg_collectes_epargne.

    Seules les collectes créées depuis la dernière synchronisation réussie sont traitées
    (basé sur le MAX(created_at) de staging).

    Returns
    -------
    dict avec 'lignes_lues', 'lignes_inserees', 'lignes_rejetees'
    """
    sql_last_sync = """
        SELECT COALESCE(MAX(created_at), '1970-01-01'::TIMESTAMPTZ)
        FROM staging.stg_collectes_epargne
    """
    sql_select = """
        SELECT
            ce.id                   AS app_id,
            ce.imf_id,
            ce.agence_id,
            ce.agent_id,
            ce.client_id,
            ce.montant,
            ce.date_collecte,
            ce.statut,
            ce.latitude,
            ce.longitude,
            ce.uuid_mobile,
            ce.created_at,
            c.client_id_externe,
            im.code                 AS imf_code
        FROM app.collectes_epargne ce
        JOIN app.clients c  ON c.id = ce.client_id AND c.imf_id = ce.imf_id
        JOIN app.imfs   im  ON im.id = ce.imf_id
        WHERE ce.created_at > %(last_sync)s
        ORDER BY ce.created_at
    """
    sql_insert = """
        INSERT INTO staging.stg_collectes_epargne (
            app_id, imf_id, imf_code, agence_id, agent_id, client_id,
            client_id_externe, montant, date_collecte,
            statut, latitude, longitude, uuid_mobile, created_at
        ) VALUES (
            %(app_id)s, %(imf_id)s, %(imf_code)s, %(agence_id)s, %(agent_id)s,
            %(client_id)s, %(client_id_externe)s, %(montant)s, %(date_collecte)s,
            %(statut)s, %(latitude)s, %(longitude)s, %(uuid_mobile)s, %(created_at)s
        )
        ON CONFLICT (uuid_mobile) DO NOTHING
    """

    with readonly_session() as cur:
        cur.execute(sql_last_sync)
        last_sync = cur.fetchone()[0]

        cur.execute(sql_select, {"last_sync": last_sync})
        rows = cur.fetchall()

    n_lu = len(rows)
    n_ins = 0
    with db_session() as cur:
        for row in rows:
            cur.execute(sql_insert, dict(row))
            n_ins += cur.rowcount

    n_rej = n_lu - n_ins
    logger.info("sync_collectes : lu=%d, inséré=%d, rejeté=%d", n_lu, n_ins, n_rej)

    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="lignes_lues",     value=n_lu)
        ti.xcom_push(key="lignes_inserees", value=n_ins)
        ti.xcom_push(key="lignes_rejetees", value=n_rej)

    return {"lignes_lues": n_lu, "lignes_inserees": n_ins, "lignes_rejetees": n_rej}


def valider_et_dedupliquer(
    seuil_montant_min: float = _MONTANT_MIN_DEFAULT,
    seuil_montant_max: float = _MONTANT_MAX_DEFAULT,
    rayon_gps_max_km:  float = _RAYON_GPS_MAX_DEFAULT,
    **ctx,
) -> dict:
    """
    Valide les collectes en staging et marque les enregistrements invalides.

    Règles de validation :
    - Montant ∈ [seuil_montant_min, seuil_montant_max]
    - GPS cohérent avec l'agence (distance ≤ rayon_gps_max_km) si coordonnées fournies
    - uuid_mobile unique (doublons mobiles ignorés via ON CONFLICT)
    - Statut ∈ {'VALIDEE', 'EN_ATTENTE', 'REJETEE'}

    Les collectes hors-seuil sont marquées statut='REJETEE' avec motif_rejet.
    """
    # 1) Montant hors bornes
    sql_montant = """
        UPDATE staging.stg_collectes_epargne
        SET statut = 'REJETEE',
            motif_rejet = %(motif)s
        WHERE statut = 'EN_ATTENTE'
          AND (montant < %(min)s OR montant > %(max)s)
        RETURNING id
    """
    # 2) GPS trop loin de l'agence (utilise la formule haversine en SQL)
    sql_gps = """
        UPDATE staging.stg_collectes_epargne ce
        SET statut = 'REJETEE',
            motif_rejet = 'GPS hors zone agence'
        FROM app.agences ag
        WHERE ag.id = ce.agence_id
          AND ce.statut = 'EN_ATTENTE'
          AND ce.latitude  IS NOT NULL
          AND ce.longitude IS NOT NULL
          AND ag.latitude  IS NOT NULL
          AND ag.longitude IS NOT NULL
          AND (
              2 * 6371 * ASIN(SQRT(
                  POWER(SIN(RADIANS(ce.latitude  - ag.latitude)  / 2), 2) +
                  COS(RADIANS(ag.latitude)) * COS(RADIANS(ce.latitude)) *
                  POWER(SIN(RADIANS(ce.longitude - ag.longitude) / 2), 2)
              ))
          ) > %(rayon)s
        RETURNING ce.id
    """
    # 3) Valider le reste
    sql_valider = """
        UPDATE staging.stg_collectes_epargne
        SET statut = 'VALIDEE'
        WHERE statut = 'EN_ATTENTE'
        RETURNING id
    """

    n_rej_montant = 0
    n_rej_gps     = 0
    n_valides     = 0

    with db_session() as cur:
        cur.execute(sql_montant, {
            "motif": f"Montant hors bornes [{seuil_montant_min:.0f}, {seuil_montant_max:.0f}] FCFA",
            "min":   seuil_montant_min,
            "max":   seuil_montant_max,
        })
        n_rej_montant = cur.rowcount

        cur.execute(sql_gps, {"rayon": rayon_gps_max_km})
        n_rej_gps = cur.rowcount

        cur.execute(sql_valider)
        n_valides = cur.rowcount

    logger.info(
        "Validation collectes : valides=%d, rejetés montant=%d, rejetés GPS=%d",
        n_valides, n_rej_montant, n_rej_gps,
    )
    return {
        "valides":       n_valides,
        "rejetes_montant": n_rej_montant,
        "rejetes_gps":     n_rej_gps,
        "rejetes_total":   n_rej_montant + n_rej_gps,
    }


def enrichir_avec_cycle_agent(**ctx) -> int:
    """
    Enrichit staging.stg_collectes_epargne avec le cycle de collecte actif
    et l'objectif de l'agent pour ce cycle.

    Ajoute : cycle_id, objectif_id, taux_realisation_partiel.
    Retourne le nombre de lignes enrichies.
    """
    sql = """
        UPDATE staging.stg_collectes_epargne sce
        SET
            cycle_id    = cc.id,
            objectif_id = oc.id
        FROM app.cycles_collecte cc
        JOIN app.objectifs_collecte oc
            ON oc.cycle_id = cc.id
            AND oc.agent_id = sce.agent_id
            AND oc.imf_id   = sce.imf_id
        WHERE cc.statut = 'EN_COURS'
          AND cc.imf_id = sce.imf_id
          AND sce.date_collecte BETWEEN cc.date_debut AND cc.date_fin
          AND sce.cycle_id IS NULL
        RETURNING sce.id
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("Enrichissement cycle/agent : %d collectes mises à jour", n)
    return n


def calculer_kpis_collecte(periodes: list[str] | None = None, **ctx) -> dict:
    """
    Calcule les KPIs de collecte et les insère dans dw.fait_collectes_journalieres.

    periodes : liste de périodes à calculer (default: ['jour', 'semaine', 'mois']).
    Retourne un dict de métriques agrégées.
    """
    if periodes is None:
        periodes = ["jour", "semaine", "mois"]

    sql_snapshot = """
        INSERT INTO dw.fait_collectes_journalieres (
            date_collecte, imf_id, agence_id, agent_id,
            nb_collectes, montant_total, montant_moyen,
            taux_rejet_pct, nb_clients_distincts
        )
        SELECT
            CURRENT_DATE                                                AS date_collecte,
            imf_id,
            agence_id,
            agent_id,
            COUNT(*)                                                    AS nb_collectes,
            SUM(montant)                                                AS montant_total,
            AVG(montant)                                                AS montant_moyen,
            COUNT(*) FILTER (WHERE statut = 'REJETEE') * 100.0 / COUNT(*) AS taux_rejet_pct,
            COUNT(DISTINCT client_id)                                   AS nb_clients_distincts
        FROM staging.stg_collectes_epargne
        WHERE date_collecte = CURRENT_DATE
        GROUP BY imf_id, agence_id, agent_id
        ON CONFLICT (date_collecte, imf_id, agence_id, agent_id)
        DO UPDATE SET
            nb_collectes       = EXCLUDED.nb_collectes,
            montant_total      = EXCLUDED.montant_total,
            montant_moyen      = EXCLUDED.montant_moyen,
            taux_rejet_pct     = EXCLUDED.taux_rejet_pct,
            nb_clients_distincts = EXCLUDED.nb_clients_distincts
    """
    sql_totaux = """
        SELECT
            COUNT(*)                                    AS nb_total,
            SUM(montant) FILTER (WHERE statut='VALIDEE') AS montant_valide,
            COUNT(*) FILTER (WHERE statut='REJETEE') * 100.0 / NULLIF(COUNT(*),0)
                                                        AS taux_rejet_pct
        FROM staging.stg_collectes_epargne
        WHERE date_collecte = CURRENT_DATE
    """

    with db_session() as cur:
        cur.execute(sql_snapshot)
        n_rows = cur.rowcount

    with readonly_session() as cur:
        cur.execute(sql_totaux)
        totaux = dict(cur.fetchone() or {})

    logger.info(
        "KPIs collecte : %d snapshots, montant_validé=%.0f FCFA, taux_rejet=%.1f%%",
        n_rows,
        totaux.get("montant_valide") or 0,
        totaux.get("taux_rejet_pct") or 0,
    )
    return {"snapshots": n_rows, **totaux}


def verifier_objectifs_cycle(**ctx) -> dict:
    """
    Compare les collectes validées du jour aux objectifs du cycle en cours.
    Met à jour app.objectifs_collecte.taux_realisation_montant.
    Retourne les objectifs en retard (< 70% à J-3 avant fin de cycle).
    """
    sql_update = """
        UPDATE app.objectifs_collecte oc
        SET
            montant_realise = sub.montant_cumule,
            taux_realisation_montant = LEAST(
                sub.montant_cumule * 100.0 / NULLIF(oc.montant_objectif, 0),
                100.0
            ),
            updated_at = NOW()
        FROM (
            SELECT
                oc2.id AS objectif_id,
                COALESCE(SUM(ce.montant) FILTER (WHERE ce.statut = 'VALIDEE'), 0) AS montant_cumule
            FROM app.objectifs_collecte oc2
            JOIN app.cycles_collecte cc ON cc.id = oc2.cycle_id AND cc.statut = 'EN_COURS'
            LEFT JOIN app.collectes_epargne ce
                ON ce.agent_id = oc2.agent_id
               AND ce.imf_id   = oc2.imf_id
               AND ce.date_collecte BETWEEN cc.date_debut AND CURRENT_DATE
               AND ce.statut = 'VALIDEE'
            GROUP BY oc2.id
        ) sub
        WHERE oc.id = sub.objectif_id
    """
    sql_en_retard = """
        SELECT COUNT(*) AS n_en_retard
        FROM app.objectifs_collecte oc
        JOIN app.cycles_collecte cc ON cc.id = oc.cycle_id
        WHERE cc.statut = 'EN_COURS'
          AND (cc.date_fin - CURRENT_DATE) <= 3
          AND oc.taux_realisation_montant < 70
    """

    with db_session() as cur:
        cur.execute(sql_update)
        n_updated = cur.rowcount

    with readonly_session() as cur:
        cur.execute(sql_en_retard)
        n_retard = (cur.fetchone() or {}).get("n_en_retard", 0)

    logger.info(
        "Objectifs cycle : %d mis à jour, %d en retard (< 70%% à J-3)",
        n_updated, n_retard,
    )
    return {"objectifs_mis_a_jour": n_updated, "objectifs_en_retard": n_retard}
