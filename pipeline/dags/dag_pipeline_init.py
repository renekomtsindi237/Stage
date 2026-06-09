"""
DAG : dag_pipeline_init
========================
Déclenché automatiquement après chaque déploiement CI/CD via :
  airflow dags trigger dag_pipeline_init --run-id "ci_<sha>"

Chaîne post-déploiement :
  1. dag_collectes       — valide et ingère les collectes terrain
  2. dag_kpis_quotidien  — recalcule les KPI du jour
  3. dag_ml_training     — entraîne/met à jour le modèle MCRS (non-bloquant)
  4. dag_ml_scoring      — score les clients actifs (non-bloquant)

Les étapes 3 et 4 sont déclenchées en parallèle après l'étape 2.
Elles sont non-bloquantes (wait_for_completion=False) pour ne pas
prolonger la fenêtre de déploiement.

dag_ml_training s'exécute aussi automatiquement tous les dimanches à 02h00.
"""

from __future__ import annotations

from datetime import datetime

from airflow import DAG
from airflow.operators.trigger_dagrun import TriggerDagRunOperator

with DAG(
    dag_id="dag_pipeline_init",
    description="Pipeline post-déploiement CI/CD : collectes → KPI → scoring + training",
    schedule_interval=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["init", "ci", "pipeline"],
) as dag:

    trigger_collectes = TriggerDagRunOperator(
        task_id="trigger_collectes",
        trigger_dag_id="dag_collectes",
        wait_for_completion=True,
        poke_interval=60,
        allowed_states=["success", "skipped"],
        failed_states=["failed"],
        reset_dag_run=True,
    )

    trigger_kpis = TriggerDagRunOperator(
        task_id="trigger_kpis_quotidien",
        trigger_dag_id="dag_kpis_quotidien",
        wait_for_completion=True,
        poke_interval=30,
        allowed_states=["success", "skipped"],
        failed_states=["failed"],
        reset_dag_run=True,
    )

    # Entraînement et scoring déclenchés en parallèle, non-bloquants.
    # Le training échouera gracieusement s'il n'y a pas encore assez de données.
    trigger_training = TriggerDagRunOperator(
        task_id="trigger_ml_training",
        trigger_dag_id="dag_ml_training",
        wait_for_completion=False,
        reset_dag_run=True,
    )

    trigger_scoring = TriggerDagRunOperator(
        task_id="trigger_ml_scoring",
        trigger_dag_id="dag_ml_scoring",
        wait_for_completion=False,
        reset_dag_run=True,
    )

    trigger_collectes >> trigger_kpis >> [trigger_training, trigger_scoring]
