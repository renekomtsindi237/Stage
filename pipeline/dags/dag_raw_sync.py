"""
DAG : dag_raw_sync
Fréquence : quotidien à 06h45 (avant dag_ml_scoring, 07h30)

Alimente raw.export_cbs / raw.collectes_terrain depuis app.creances,
app.clients_informels et app.collectes_terrain — cf.
scripts/raw_sync_utils.py pour le détail et les limites (ce n'est pas un
export CBS ni une synchronisation mobile externe réelle, aucune des deux
n'est connectée à ce jour). raw.prix_marche/transactions_mtn/
transactions_orange restent hors périmètre : aucune source de données
n'existe pour elles, ni dans raw ni dans app.
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator

from scripts.raw_sync_utils import sync_collectes_terrain, sync_export_cbs

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 2,
    "retry_delay": timedelta(minutes=10),
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_raw_sync",
    description="Synchronise app.* -> raw.* (miroir, pas une ingestion externe réelle)",
    schedule_interval="45 6 * * *",  # 06h45, avant dag_ml_scoring (07h30)
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["raw", "sync", "cbs", "collectes"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    sync_cbs = PythonOperator(
        task_id="sync_export_cbs",
        python_callable=sync_export_cbs,
        doc="app.creances + app.clients_informels -> raw.export_cbs",
    )

    sync_collectes = PythonOperator(
        task_id="sync_collectes_terrain",
        python_callable=sync_collectes_terrain,
        doc="app.collectes_terrain (statut CONFIRMEE) -> raw.collectes_terrain",
    )

    [sync_cbs, sync_collectes]
