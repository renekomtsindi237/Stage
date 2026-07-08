"""
ml_alertes_utils.py — Génération des alertes prédictives ML.

Appelé par dag_ml_scoring après le scoring journalier.
Insère des alertes dans ml.alertes_predictives pour les clients
dont le score MCRS dépasse les seuils configurés.
"""

from __future__ import annotations

import logging

from pipeline.src.database import db_session, readonly_session

logger = logging.getLogger(__name__)

TYPES_ALERTE_ML = {
    "RISQUE_DEFAUT_IMMINENT",  # score MCRS ≥ seuil_critique
    "BAISSE_COLLECTE_PERSISTANTE",  # variation collecte < -20% sur 4 semaines consécutives
    "DETERIORATION_RAPIDE",  # hausse du score MCRS > 0.15 en 7 jours
    "CLASSE_COBAC_AGGRAVEE",  # passage de classe B→C ou C→D ce jour
}


def generer_alertes_predictives(
    seuil_defaut_critique: float = 0.75,
    seuil_baisse_collecte_pct: float = -20.0,
    seuil_degradation_score: float = -0.15,
    **ctx,
) -> int:
    """
    Génère les alertes ML depuis les scores du jour et les tendances.

    Returns
    -------
    Nombre total d'alertes générées.
    """
    n_total = 0
    n_total += _alertes_risque_critique(seuil_defaut_critique)
    n_total += _alertes_deterioration_rapide(seuil_degradation_score)
    n_total += _alertes_baisse_collecte(seuil_baisse_collecte_pct)
    n_total += _alertes_cobac_aggravee()

    logger.info("Alertes ML générées : %d", n_total)
    return n_total


def _alertes_risque_critique(seuil: float) -> int:
    """Alerte pour les clients dont le MCRS dépasse le seuil critique."""
    # ml.client_scores porte imf_id (BIGINT), pas imf_code, et n'a pas de
    # colonne shap_top_features (les valeurs SHAP vivent dans
    # ml.shap_explanations, liée par score_id — pas rejointe ici pour rester
    # simple). Depuis V29, client_scores est un upsert par client (un seul
    # enregistrement courant) : plus de notion de "date_score = aujourd'hui",
    # le score EST déjà le plus récent par construction.
    sql_select = """
        SELECT cs.client_id_externe, cs.imf_id, cs.score_mcrs,
               cs.probabilite_defaut_90j, cs.action_recommandee
        FROM ml.client_scores cs
        WHERE cs.score_mcrs >= %(seuil)s
          AND NOT EXISTS (
              SELECT 1 FROM ml.alertes_predictives ap
              WHERE ap.client_id_externe = cs.client_id_externe
                AND ap.imf_id            = cs.imf_id
                AND ap.type_alerte       = 'RISQUE_DEFAUT_IMMINENT'
                AND ap.created_at::date  = CURRENT_DATE
                AND ap.statut            = 'ACTIVE'
          )
    """
    sql_insert = """
        INSERT INTO ml.alertes_predictives (
            client_id_externe, imf_id, type_alerte, titre, description,
            valeur_declenchante, seuil_alerte, statut
        ) VALUES (
            %(client_id_externe)s, %(imf_id)s, 'RISQUE_DEFAUT_IMMINENT',
            %(titre)s, %(description)s,
            %(score_mcrs)s, %(seuil)s, 'ACTIVE'
        )
    """
    n = 0
    with readonly_session() as cur:
        cur.execute(sql_select, {"seuil": seuil})
        rows = cur.fetchall()

    with db_session() as cur:
        for row in rows:
            cur.execute(
                sql_insert,
                {
                    "client_id_externe": row["client_id_externe"],
                    "imf_id": row["imf_id"],
                    "titre": f"Risque de défaut imminent — {row['client_id_externe']}",
                    "description": (
                        f"Score MCRS={row['score_mcrs']:.3f} — "
                        f"P(défaut 90j)={row['probabilite_defaut_90j']:.1%} — "
                        f"Action recommandée : {row['action_recommandee']}"
                    ),
                    "score_mcrs": row["score_mcrs"],
                    "seuil": seuil,
                },
            )
            n += 1

    logger.info("Alertes RISQUE_DEFAUT_IMMINENT : %d", n)
    return n


def _alertes_deterioration_rapide(seuil_delta: float) -> int:
    """
    Alerte si le score MCRS s'est dégradé de plus de |seuil_delta| en 7 jours.

    Non implémentable en l'état : depuis V29, ml.client_scores ne conserve
    qu'un seul enregistrement par client (le plus récent, upsert sur
    (client_id_externe, imf_id)) — aucun historique de score n'est
    disponible pour comparer "aujourd'hui" à "il y a 7 jours". Nécessiterait
    soit une table d'historique dédiée, soit de renoncer à l'upsert V29
    (régression). Hors périmètre d'une correction de schéma — signalé
    clairement plutôt que silencieusement no-opé.
    """
    logger.warning(
        "Alertes DETERIORATION_RAPIDE non calculées : ml.client_scores ne "
        "conserve plus d'historique par client depuis V29 (upsert), "
        "impossible de comparer au score d'il y a 7 jours sans table dédiée."
    )
    return 0


def _alertes_baisse_collecte(seuil_pct: float) -> int:
    """
    Alerte si la tendance collecte est négative de façon persistante (4 semaines).

    Non fonctionnel en l'état : staging.stg_clients dépend de
    raw.export_cbs (jamais alimenté, pas d'ingestion CBS réelle configurée)
    — la requête échoue systématiquement dès le SELECT, capturé par le
    try/except ci-dessous (dégradation déjà en place, conservée telle
    quelle plutôt que réécrite pour une source qui n'existe pas encore).
    """
    sql = """
        SELECT
            c.client_id_externe,
            c.imf_code,
            AVG(ce.montant) AS montant_moy_4s,
            -- Compare avec la moyenne des 4 semaines précédentes
            LAG(AVG(ce.montant), 1) OVER (PARTITION BY c.id ORDER BY DATE_TRUNC('week', ce.date_collecte))
                AS montant_moy_4s_precedent
        FROM staging.stg_clients c
        JOIN staging.stg_collectes_epargne ce
            ON ce.client_id = c.id
            AND ce.statut   = 'VALIDEE'
            AND ce.date_collecte >= CURRENT_DATE - INTERVAL '8 weeks'
        GROUP BY c.id, c.client_id_externe, c.imf_code,
                 DATE_TRUNC('week', ce.date_collecte)
        HAVING AVG(ce.montant) < LAG(AVG(ce.montant), 1) OVER (
            PARTITION BY c.id ORDER BY DATE_TRUNC('week', ce.date_collecte)
        ) * (1 + %(seuil)s / 100.0)
    """
    # Note : requête simplifiée — la production utiliserait une CTE plus complexe
    n = 0
    try:
        with readonly_session() as cur:
            cur.execute(sql, {"seuil": seuil_pct})
            rows = cur.fetchall()

        with db_session() as cur:
            for row in rows:
                cur.execute(
                    """
                    INSERT INTO ml.alertes_predictives
                        (client_id_externe, imf_code, type_alerte, message, date_detection, statut, created_at)
                    VALUES
                        (%(client_id_externe)s, %(imf_code)s, 'BAISSE_COLLECTE_PERSISTANTE',
                         %(message)s, CURRENT_DATE, 'ACTIVE', NOW())
                    ON CONFLICT DO NOTHING
                """,
                    {
                        "client_id_externe": row["client_id_externe"],
                        "imf_code": row["imf_code"],
                        "message": "Baisse collecte persistante — tendance négative sur 4 semaines",
                    },
                )
                n += 1
    except Exception as exc:
        logger.warning("Alertes BAISSE_COLLECTE ignorées : %s", exc)

    logger.info("Alertes BAISSE_COLLECTE_PERSISTANTE : %d", n)
    return n


def _alertes_cobac_aggravee() -> int:
    """
    Alerte si un client passe d'une classe COBAC inférieure à une classe supérieure ce jour.
    Ex : B→C ou C→D (aggravation de la classification).

    Non fonctionnel en l'état : staging.stg_creances n'a ni colonne
    date_extraction ni classe_cobac (la vraie colonne est
    classe_risque_cobac, et il n'existe aucun historique quotidien par
    classe — même limite que _alertes_deterioration_rapide). Requête
    échoue systématiquement dès le SELECT, capturé par le try/except
    ci-dessous.
    """
    sql = """
        WITH hier AS (
            SELECT client_id, imf_id, classe_cobac AS classe_hier
            FROM staging.stg_creances
            WHERE date_extraction = CURRENT_DATE - INTERVAL '1 day'
        ),
        auj AS (
            SELECT client_id, imf_id, classe_cobac AS classe_auj
            FROM staging.stg_creances
            WHERE date_extraction = CURRENT_DATE
        )
        SELECT
            c.client_id_externe, c.imf_code,
            h.classe_hier, a.classe_auj
        FROM hier h
        JOIN auj a ON a.client_id = h.client_id AND a.imf_id = h.imf_id
        JOIN staging.stg_clients c ON c.id = h.client_id AND c.imf_id = h.imf_id
        WHERE
            -- Aggravation : classe COBAC monte (A < B < C < D < E)
            CASE h.classe_hier WHEN 'A' THEN 0 WHEN 'B' THEN 1 WHEN 'C' THEN 2 WHEN 'D' THEN 3 ELSE 4 END
            <
            CASE a.classe_auj  WHEN 'A' THEN 0 WHEN 'B' THEN 1 WHEN 'C' THEN 2 WHEN 'D' THEN 3 ELSE 4 END
    """
    n = 0
    try:
        with readonly_session() as cur:
            cur.execute(sql)
            rows = cur.fetchall()

        with db_session() as cur:
            for row in rows:
                cur.execute(
                    """
                    INSERT INTO ml.alertes_predictives
                        (client_id_externe, imf_code, type_alerte, message, date_detection, statut, created_at)
                    VALUES
                        (%(cie)s, %(imc)s, 'CLASSE_COBAC_AGGRAVEE', %(msg)s, CURRENT_DATE, 'ACTIVE', NOW())
                    ON CONFLICT DO NOTHING
                """,
                    {
                        "cie": row["client_id_externe"],
                        "imc": row["imf_code"],
                        "msg": f"Passage COBAC {row['classe_hier']} → {row['classe_auj']} ce jour",
                    },
                )
                n += 1
    except Exception as exc:
        logger.warning("Alertes COBAC_AGGRAVEE ignorées : %s", exc)

    logger.info("Alertes CLASSE_COBAC_AGGRAVEE : %d", n)
    return n
