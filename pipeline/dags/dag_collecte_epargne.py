"""
DAG : dag_collecte_epargne
Fréquence : toutes les 2 heures (collectes temps réel terrain)

Pipeline :
  1. sync_collectes_app     — lit raw.collectes_terrain depuis l'app
  2. valider_collectes      — déduplication UUID, contrôles GPS, montants
  3. enrichir_collectes     — lien agent/cycle/agence, calcul distance
  4. dbt_stg_collectes      — dbt run --select staging.stg_collectes_epargne
  5. dbt_int_collectes      — dbt run --select intermediate.int_collectes_*
  6. calculer_kpis           — maj app.kpi_collecte_snapshots
  7. verifier_objectifs      — comparaison réalisé vs objectifs cycle
  8. generer_alertes_ops     — alertes si taux_rejet élevé ou objectif non atteint
  9. notifier_agents         — push FCM aux agents concernés
 10. log_journal             — journal raw.journal_ingestions
"""
from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.collecte_utils import (
    sync_collectes_depuis_app,
    valider_et_dedupliquer,
    enrichir_avec_cycle_agent,
    calculer_kpis_collecte,
    verifier_objectifs_cycle,
)
from scripts.notification_utils import (
    notifier_agents_fcm,
    notifier_responsables_sse,
)
from scripts.dbt_utils import dbt_run_select
from scripts.ingestion_utils import log_journal, generer_alertes_operationnelles

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
    "retry_exponential_backoff": True,
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_collecte_epargne",
    description="Ingestion et KPI des collectes d'épargne terrain (temps réel)",
    schedule_interval="0 */2 * * *",       # toutes les 2h
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["collecte", "epargne", "terrain", "core"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin   = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    sync_app = PythonOperator(
        task_id="sync_collectes_app",
        python_callable=sync_collectes_depuis_app,
        doc="Récupère les collectes en attente depuis raw.collectes_terrain (statut=RECU)",
    )

    valider = PythonOperator(
        task_id="valider_collectes",
        python_callable=valider_et_dedupliquer,
        op_kwargs={
            "seuil_montant_min": 100,
            "seuil_montant_max": 5_000_000,
            "rayon_gps_max_km": 50,
        },
        doc="Déduplication UUID, validation montants, cohérence GPS avec zone agent",
    )

    enrichir = PythonOperator(
        task_id="enrichir_collectes",
        python_callable=enrichir_avec_cycle_agent,
        doc="Lien cycle_id, agence_id, calcul distance_agence_km, heure locale",
    )

    dbt_staging = PythonOperator(
        task_id="dbt_stg_collectes",
        python_callable=dbt_run_select,
        op_kwargs={"select": "staging.stg_collectes_epargne", "full_refresh": False},
    )

    dbt_intermediate = PythonOperator(
        task_id="dbt_int_collectes",
        python_callable=dbt_run_select,
        op_kwargs={"select": "intermediate.int_collectes_par_agent intermediate.int_collectes_par_cycle"},
    )

    kpis = PythonOperator(
        task_id="calculer_kpis",
        python_callable=calculer_kpis_collecte,
        op_kwargs={"periodes": ["QUOTIDIEN", "HEBDOMADAIRE"]},
    )

    objectifs = PythonOperator(
        task_id="verifier_objectifs",
        python_callable=verifier_objectifs_cycle,
        doc="Met à jour objectifs_collecte.realise_* et taux_realisation pour cycles actifs",
    )

    alertes = PythonOperator(
        task_id="generer_alertes_ops",
        python_callable=generer_alertes_operationnelles,
        op_kwargs={
            "types": [
                "OBJECTIF_NON_ATTEINT",
                "TAUX_REJET_ELEVE",
                "AGENT_INACTIF",
                "SYNCHRONISATION_RETARD",
            ],
            "seuil_taux_rejet_pct": 10.0,
            "seuil_inactivite_heures": 48,
        },
    )

    notif_agents = PythonOperator(
        task_id="notifier_agents",
        python_callable=notifier_agents_fcm,
        op_kwargs={"type_notif": "COLLECTE_FEEDBACK"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    notif_resp = PythonOperator(
        task_id="notifier_responsables_sse",
        python_callable=notifier_responsables_sse,
        op_kwargs={"event": "kpi_collecte_updated"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={"dag_id": "dag_collecte_epargne", "table_cible": "staging.stg_collectes_epargne"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    # Chaîne principale
    debut >> sync_app >> valider >> enrichir >> dbt_staging >> dbt_intermediate
    dbt_intermediate >> kpis >> objectifs >> alertes
    alertes >> [notif_agents, notif_resp]
    [notif_agents, notif_resp] >> journal >> fin
