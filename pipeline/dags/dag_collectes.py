"""
DAG — Collectes d'épargne terrain
==================================
Ingestion, déduplication et calcul des KPI collectes.
Fréquence : quotidien à 06h00 (après les syncs mobiles de nuit).

Tâches :
  1. valider_nouvelles_collectes    — valide format, détecte doublons
  2. calculer_kpi_collecte          — KPI agence/agent/IMF du jour
  3. snapshot_kpi_collecte          — archive snapshot journalier
  4. alerter_objectifs_non_atteints — alerte si < seuil configuré
  5. notifier_fin                   — notification DSI en fin de DAG
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.utils.dates import days_ago

import logging

log = logging.getLogger(__name__)

POSTGRES_CONN = "imf_pipeline_db"

DEFAULT_ARGS = {
    "owner":            "imf-pipeline",
    "retries":          2,
    "retry_delay":      timedelta(minutes=5),
    "email_on_failure": False,
}


# ─── Tâches ──────────────────────────────────────────────────────────────────

def valider_nouvelles_collectes(**ctx) -> dict:
    """
    Valide les collectes SOUMISE depuis hier :
    - Détecte les doublons (même id_collecte_mobile déjà CONFIRMEE)
    - Marque CONFIRMEE les collectes valides
    - Marque DOUBLON les doublons détectés
    """
    pg   = PostgresHook(postgres_conn_id=POSTGRES_CONN)
    conn = pg.get_conn()
    cur  = conn.cursor()

    # Doublons : même id_collecte_mobile, statut déjà CONFIRMEE
    cur.execute("""
        UPDATE app.collectes_terrain c
        SET    statut     = 'DOUBLON',
               updated_at = NOW()
        FROM   app.collectes_terrain existing
        WHERE  c.id_collecte_mobile  = existing.id_collecte_mobile
          AND  c.statut              = 'SOUMISE'
          AND  existing.statut       = 'CONFIRMEE'
          AND  c.id                 <> existing.id
    """)
    n_doublons = cur.rowcount

    # Confirmation des collectes valides
    cur.execute("""
        UPDATE app.collectes_terrain
        SET    statut     = 'CONFIRMEE',
               updated_at = NOW()
        WHERE  statut = 'SOUMISE'
          AND  created_at >= NOW() - INTERVAL '48 hours'
          AND  montant_collecte > 0
    """)
    n_confirmees = cur.rowcount

    conn.commit()
    cur.close()
    conn.close()

    log.info("Validation collectes : %d confirmées, %d doublons", n_confirmees, n_doublons)
    return {"confirmees": n_confirmees, "doublons": n_doublons}


def calculer_kpi_collecte(**ctx) -> None:
    """
    Calcule et insère (ou met à jour) les KPI collectes dans app.kpi_collecte_snapshots.
    Calculés par IMF, agence, agent et jour courant.
    """
    date_j = ctx["ds"]   # YYYY-MM-DD
    pg     = PostgresHook(postgres_conn_id=POSTGRES_CONN)
    conn   = pg.get_conn()
    cur    = conn.cursor()

    cur.execute("""
        INSERT INTO app.kpi_collecte_snapshots
            (imf_id, agence_id, agent_id, date_snapshot,
             montant_total_jour, nb_collectes_jour,
             montant_especes, montant_mobile_money, montant_virement,
             nb_agents_actifs, created_at)
        SELECT
            c.imf_id,
            u.agence_id,
            c.agent_id,
            %(date_j)s::date,
            SUM(c.montant_collecte)                             AS montant_total_jour,
            COUNT(*)                                             AS nb_collectes_jour,
            SUM(CASE WHEN canal_paiement = 'ESPECES'        THEN montant_collecte ELSE 0 END),
            SUM(CASE WHEN canal_paiement IN ('MTN_MOBILE_MONEY','ORANGE_MONEY')
                                                             THEN montant_collecte ELSE 0 END),
            SUM(CASE WHEN canal_paiement = 'VIREMENT'       THEN montant_collecte ELSE 0 END),
            COUNT(DISTINCT c.agent_id),
            NOW()
        FROM  app.collectes_terrain c
        JOIN  app.utilisateurs u ON u.id = c.agent_id
        WHERE c.date_collecte = %(date_j)s::date
          AND c.statut        = 'CONFIRMEE'
        GROUP BY c.imf_id, u.agence_id, c.agent_id
        ON CONFLICT (imf_id, agence_id, agent_id, date_snapshot)
        DO UPDATE SET
            montant_total_jour = EXCLUDED.montant_total_jour,
            nb_collectes_jour  = EXCLUDED.nb_collectes_jour,
            montant_especes    = EXCLUDED.montant_especes,
            montant_mobile_money = EXCLUDED.montant_mobile_money,
            montant_virement   = EXCLUDED.montant_virement,
            nb_agents_actifs   = EXCLUDED.nb_agents_actifs
    """, {"date_j": date_j})

    conn.commit()
    cur.close()
    conn.close()
    log.info("KPI collecte calculés pour %s", date_j)


def alerter_objectifs_non_atteints(**ctx) -> None:
    """
    Détecte les agences dont le taux de réalisation est inférieur au seuil
    configuré (défaut 70%) à J-3 de la fin de cycle (EF-C06).
    Insère une alerte dans app.alertes_impayes.
    """
    pg   = PostgresHook(postgres_conn_id=POSTGRES_CONN)
    conn = pg.get_conn()
    cur  = conn.cursor()

    cur.execute("""
        INSERT INTO app.alertes_impayes
            (imf_id, client_id, type_alerte, message, statut, created_at)
        SELECT DISTINCT
            ks.imf_id,
            NULL::bigint,
            'OBJECTIF_COLLECTE_NON_ATTEINT',
            FORMAT('Agence %s : taux réalisation %.1f%% (seuil 70%%) à J-3 fin cycle',
                   a.code, taux * 100),
            'OUVERTE',
            NOW()
        FROM (
            SELECT
                ks.imf_id,
                ks.agence_id,
                SUM(ks.montant_total_jour)::numeric /
                    NULLIF(cc.objectif_montant_cycle, 0)  AS taux
            FROM  app.kpi_collecte_snapshots ks
            JOIN  app.cycles_collecte cc
                  ON cc.agence_id = ks.agence_id
                 AND cc.statut    = 'EN_COURS'
            WHERE ks.date_snapshot BETWEEN cc.date_debut AND CURRENT_DATE
            GROUP BY ks.imf_id, ks.agence_id, cc.objectif_montant_cycle, cc.date_fin
            HAVING cc.date_fin - CURRENT_DATE <= 3
               AND SUM(ks.montant_total_jour) / NULLIF(cc.objectif_montant_cycle, 0) < 0.70
        ) sub
        JOIN app.agences a ON a.id = sub.agence_id
    """)
    n = cur.rowcount
    conn.commit()
    cur.close()
    conn.close()
    if n > 0:
        log.warning("Alertes objectifs non atteints : %d agence(s)", n)
    else:
        log.info("Tous les objectifs de collecte sont sur track")


# ─── DAG ─────────────────────────────────────────────────────────────────────

with DAG(
    dag_id="dag_collectes",
    description="Ingestion et KPI collectes d'épargne terrain (quotidien)",
    default_args=DEFAULT_ARGS,
    start_date=days_ago(1),
    schedule_interval="0 6 * * *",
    catchup=False,
    tags=["collectes", "kpi", "terrain"],
) as dag:

    t1 = PythonOperator(
        task_id="valider_nouvelles_collectes",
        python_callable=valider_nouvelles_collectes,
    )
    t2 = PythonOperator(
        task_id="calculer_kpi_collecte",
        python_callable=calculer_kpi_collecte,
    )
    t3 = PythonOperator(
        task_id="alerter_objectifs_non_atteints",
        python_callable=alerter_objectifs_non_atteints,
    )

    t1 >> t2 >> t3
