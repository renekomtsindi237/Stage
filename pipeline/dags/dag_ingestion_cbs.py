"""
dag_ingestion_cbs.py — Ingestion ponctuelle des exports CBS hors-cycle.

Ce DAG permet de déclencher manuellement l'ingestion d'un export CBS
(FinancialEdge, Mambu, Excel) déposé dans le dossier entrant.

En fonctionnement normal, l'ingestion CBS est intégrée dans dag_recouvrement
(exécuté quotidiennement à 06h00). Ce DAG est réservé aux imports ad hoc.
"""
from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.recouvrement_utils import (
    ingerer_export_cbs,
    valider_donnees_cbs,
    synchroniser_creances_app,
    calculer_par_et_provisions,
)
from scripts.ingestion_utils import log_journal

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 1,
    "retry_delay": timedelta(minutes=10),
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_ingestion_cbs",
    description="Import ad hoc d'un export CBS — déclenchement manuel uniquement",
    schedule_interval=None,         # Déclenché manuellement
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["cbs", "recouvrement", "manuel"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin   = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    ingerer = PythonOperator(
        task_id="ingerer_export_cbs",
        python_callable=ingerer_export_cbs,
        op_kwargs={"dossier_entrant": "/data/cbs/incoming"},
    )

    valider = PythonOperator(
        task_id="valider_donnees_cbs",
        python_callable=valider_donnees_cbs,
    )

    par = PythonOperator(
        task_id="calculer_par_et_provisions",
        python_callable=calculer_par_et_provisions,
    )

    sync = PythonOperator(
        task_id="synchroniser_creances_app",
        python_callable=synchroniser_creances_app,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={"dag_id": "dag_ingestion_cbs", "table_cible": "staging.stg_creances"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    debut >> ingerer >> valider >> par >> sync >> journal >> fin
