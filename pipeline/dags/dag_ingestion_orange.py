"""
dag_ingestion_orange.py — OBSOLÈTE / DÉSACTIVÉ

Ce DAG correspondait à l'ingestion des relevés Orange Money.
Dans l'architecture actuelle (collectes terrain via application mobile Flutter),
les transactions sont synchronisées directement via dag_collecte_epargne.

Ce fichier est conservé pour traçabilité. Aucune tâche active.
"""
from __future__ import annotations

from datetime import datetime
from airflow import DAG
from airflow.operators.empty import EmptyOperator

with DAG(
    dag_id="dag_ingestion_orange",
    description="[OBSOLÈTE] Ingestion relevés Orange Money — remplacé par dag_collecte_epargne",
    schedule_interval=None,         # Désactivé
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["obsolete"],
    is_paused_upon_creation=True,
) as dag:
    EmptyOperator(task_id="obsolete_voir_dag_collecte_epargne")
