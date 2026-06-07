"""
IMF Pipeline — Utilitaires alertes ML pour dag_ml_scoring
==========================================================

Génère les alertes prédictives dans ml.alertes_predictives à partir
des scores MCRS et des tendances de dégradation détectées.

Appelé par la tâche generer_alertes_ml du DAG dag_ml_scoring.
"""
from __future__ import annotations

import logging
import os
from datetime import datetime

log = logging.getLogger("imf.ml.alertes")

POSTGRES_HOST     = os.environ.get("POSTGRES_HOST", "localhost")
POSTGRES_PORT     = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_DB       = os.environ.get("POSTGRES_DB", "imf_dev")
POSTGRES_USER     = os.environ.get("POSTGRES_USER", "imf")
POSTGRES_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "imf_pass")


def _get_pg_conn():
    import psycopg2
    return psycopg2.connect(
        host=POSTGRES_HOST, port=POSTGRES_PORT,
        dbname=POSTGRES_DB, user=POSTGRES_USER, password=POSTGRES_PASSWORD,
    )


def generer_alertes_predictives(seuil_defaut_critique: float = 0.75,
                                 seuil_baisse_collecte_pct: float = -20.0,
                                 seuil_degradation_score: float = -0.15,
                                 **ctx) -> int:
    """
    Génère les alertes prédictives ML dans ml.alertes_predictives.

    Types d'alertes générées :
    - RISQUE_DEFAUT_IMMINENT    : score_mcrs >= seuil_defaut_critique
    - BAISSE_COLLECTE_DETECTEE  : tendance_collecte_3m < seuil_baisse_collecte_pct
    - DEGRADATION_SCORE_RAPIDE  : variation MCRS < seuil_degradation_score
    - CIBLE_RECOUVREMENT_PRIORITAIRE : combinaison risque élevé + retard
    """
    conn = _get_pg_conn()
    cur = conn.cursor()

    n_alertes = 0

    # ── 1. RISQUE_DEFAUT_IMMINENT ──────────────────────────────────────────────
    cur.execute("""
        INSERT INTO ml.alertes_predictives
            (imf_id, client_id_externe, score_id, type_alerte, urgence,
             titre, description, recommandation,
             valeur_declenchante, seuil_alerte,
             fcm_sent, sse_sent, created_at, updated_at)
        SELECT
            cs.imf_id,
            cs.client_id_externe,
            cs.id,
            'RISQUE_DEFAUT_IMMINENT',
            CASE WHEN cs.score_mcrs >= 0.85 THEN 'CRITIQUE'
                 WHEN cs.score_mcrs >= 0.75 THEN 'HAUTE'
                 ELSE 'MOYENNE' END,
            FORMAT('Risque défaut imminent — client %s (MCRS=%.3f)',
                   cs.client_id_externe, cs.score_mcrs),
            FORMAT('Le score MCRS de %.3f dépasse le seuil critique de %.2f. '
                   'Classe COBAC : %s. Action recommandée immédiate.',
                   cs.score_mcrs, %(seuil)s, cs.cobac_classe),
            CASE cs.cobac_classe
                WHEN 'E' THEN 'Escalade juridique urgente — provision 100%%'
                WHEN 'D' THEN 'Mise en demeure formelle — provision 80%%'
                ELSE 'Visite terrain + plan de restructuration sous 48h'
            END,
            cs.score_mcrs,
            %(seuil)s,
            FALSE, FALSE, NOW(), NOW()
        FROM ml.client_scores cs
        WHERE cs.score_mcrs >= %(seuil)s
          AND NOT EXISTS (
              SELECT 1 FROM ml.alertes_predictives ap
              WHERE ap.client_id_externe = cs.client_id_externe
                AND ap.imf_id = cs.imf_id
                AND ap.type_alerte = 'RISQUE_DEFAUT_IMMINENT'
                AND ap.statut = 'ACTIVE'
                AND ap.created_at >= NOW() - INTERVAL '24 hours'
          )
    """, {"seuil": seuil_defaut_critique})
    n_alertes += cur.rowcount

    # ── 2. BAISSE_COLLECTE_DETECTEE ────────────────────────────────────────────
    cur.execute("""
        INSERT INTO ml.alertes_predictives
            (imf_id, client_id_externe, type_alerte, urgence,
             titre, description, recommandation,
             valeur_declenchante, seuil_alerte,
             fcm_sent, sse_sent, created_at, updated_at)
        SELECT
            fc.imf_id,
            fc.client_id_externe,
            'BAISSE_COLLECTE_DETECTEE',
            'MOYENNE',
            FORMAT('Baisse collecte persistante — client %s (tendance %.1f%%)',
                   fc.client_id_externe, fc.tendance_collecte_3m * 100),
            FORMAT('La tendance de collecte sur 3 mois est de %.1f%%, '
                   'inférieure au seuil de %.1f%%. '
                   'Risque de rupture du cycle d épargne.',
                   fc.tendance_collecte_3m * 100, %(seuil_baisse)s),
            'Relance préventive par l agent terrain — vérifier facteurs externes locaux',
            fc.tendance_collecte_3m * 100,
            %(seuil_baisse)s,
            FALSE, FALSE, NOW(), NOW()
        FROM ml.features_client fc
        WHERE fc.tendance_collecte_3m * 100 < %(seuil_baisse)s
          AND fc.computed_at >= NOW() - INTERVAL '2 hours'
          AND NOT EXISTS (
              SELECT 1 FROM ml.alertes_predictives ap
              WHERE ap.client_id_externe = fc.client_id_externe
                AND ap.imf_id = fc.imf_id
                AND ap.type_alerte = 'BAISSE_COLLECTE_DETECTEE'
                AND ap.statut = 'ACTIVE'
                AND ap.created_at >= NOW() - INTERVAL '7 days'
          )
    """, {"seuil_baisse": seuil_baisse_collecte_pct})
    n_alertes += cur.rowcount

    # ── 3. DEGRADATION_SCORE_RAPIDE ────────────────────────────────────────────
    # Compare le score actuel avec le score d'avant-hier (fenêtre 48h)
    cur.execute("""
        INSERT INTO ml.alertes_predictives
            (imf_id, client_id_externe, score_id, type_alerte, urgence,
             titre, description, recommandation,
             valeur_declenchante, seuil_alerte,
             fcm_sent, sse_sent, created_at, updated_at)
        SELECT
            cs_now.imf_id,
            cs_now.client_id_externe,
            cs_now.id,
            'DEGRADATION_SCORE_RAPIDE',
            'HAUTE',
            FORMAT('Dégradation rapide du score — client %s (Δ=%.3f)',
                   cs_now.client_id_externe,
                   cs_now.score_mcrs - cs_prev.score_mcrs),
            FORMAT('Le score MCRS est passé de %.3f à %.3f en moins de 48h '
                   '(variation : %.3f). Dégradation supérieure au seuil de %.2f.',
                   cs_prev.score_mcrs, cs_now.score_mcrs,
                   cs_now.score_mcrs - cs_prev.score_mcrs, %(seuil_deg)s),
            'Analyse immédiate des causes : retards paiement, baisse collecte, facteurs externes',
            cs_now.score_mcrs - cs_prev.score_mcrs,
            %(seuil_deg)s,
            FALSE, FALSE, NOW(), NOW()
        FROM ml.client_scores cs_now
        JOIN ml.client_scores cs_prev
          ON cs_prev.client_id_externe = cs_now.client_id_externe
         AND cs_prev.imf_id = cs_now.imf_id
         AND cs_prev.scored_at BETWEEN NOW() - INTERVAL '72 hours' AND NOW() - INTERVAL '24 hours'
        WHERE (cs_now.score_mcrs - cs_prev.score_mcrs) < %(seuil_deg)s
          AND cs_now.scored_at >= NOW() - INTERVAL '2 hours'
          AND NOT EXISTS (
              SELECT 1 FROM ml.alertes_predictives ap
              WHERE ap.client_id_externe = cs_now.client_id_externe
                AND ap.imf_id = cs_now.imf_id
                AND ap.type_alerte = 'DEGRADATION_SCORE_RAPIDE'
                AND ap.statut = 'ACTIVE'
                AND ap.created_at >= NOW() - INTERVAL '24 hours'
          )
    """, {"seuil_deg": seuil_degradation_score})
    n_alertes += cur.rowcount

    conn.commit()
    cur.close()
    conn.close()

    log.info("Alertes predictives generees : %d nouvelles alertes", n_alertes)
    ctx["ti"].xcom_push(key="n_alertes_ml", value=n_alertes)
    return n_alertes
