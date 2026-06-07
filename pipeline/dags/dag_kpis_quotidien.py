"""
dag_kpis_quotidien.py — Recalcul forcé des KPIs collecte + recouvrement.

Déclenché manuellement pour recalculer les KPIs d'une date donnée
après correction de données ou incident.

En fonctionnement normal les KPIs sont calculés par :
  - dag_collecte_epargne  (KPIs collecte, toutes les 2h)
  - dag_recouvrement      (KPIs PAR, provisions, benchmarks — 06h00)
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.empty import EmptyOperator
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.collecte_utils import calculer_kpis_collecte, verifier_objectifs_cycle
from scripts.dbt_utils import dbt_run_select
from scripts.ingestion_utils import generer_alertes_operationnelles, log_journal
from scripts.recouvrement_utils import (
    calculer_benchmarks_agences,
    calculer_kpis_recouvrement,
    calculer_par_et_provisions,
)

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 1,
    "retry_delay": timedelta(minutes=10),
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_kpis_quotidien",
    description="Recalcul forcé KPIs collecte + recouvrement — déclenchement manuel",
    schedule_interval=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["kpis", "manuel", "recouvrement", "collecte"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    # ── Transformations dbt ──────────────────────────────────────────────────
    dbt_stg = PythonOperator(
        task_id="dbt_staging",
        python_callable=dbt_run_select,
        op_kwargs={"select": "staging", "full_refresh": False},
    )

    dbt_dw = PythonOperator(
        task_id="dbt_dw",
        python_callable=dbt_run_select,
        op_kwargs={"select": "dw"},
    )

    # ── KPIs collecte ────────────────────────────────────────────────────────
    kpis_collecte = PythonOperator(
        task_id="kpis_collecte",
        python_callable=calculer_kpis_collecte,
        op_kwargs={"periodes": ["jour", "semaine", "mois"]},
    )

    objectifs = PythonOperator(
        task_id="verifier_objectifs",
        python_callable=verifier_objectifs_cycle,
    )

    # ── KPIs recouvrement ────────────────────────────────────────────────────
    par = PythonOperator(
        task_id="calculer_par",
        python_callable=calculer_par_et_provisions,
    )

    kpis_recouv = PythonOperator(
        task_id="kpis_recouvrement",
        python_callable=calculer_kpis_recouvrement,
    )

    benchmarks = PythonOperator(
        task_id="benchmarks_agences",
        python_callable=calculer_benchmarks_agences,
    )

    # ── Alertes opérationnelles ──────────────────────────────────────────────
    alertes = PythonOperator(
        task_id="alertes_operationnelles",
        python_callable=generer_alertes_operationnelles,
        op_kwargs={
            "types": [
                "PAR_SEUIL_DEPASSE",
                "OBJECTIF_NON_ATTEINT",
                "DOSSIER_SANS_ACTION",
            ],
            "seuil_par90_pct": 5.0,
        },
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={
            "dag_id": "dag_kpis_quotidien",
            "table_cible": "dw.fait_collectes_journalieres",
        },
        trigger_rule=TriggerRule.ALL_DONE,
    )

    debut >> dbt_stg >> dbt_dw
    dbt_dw >> [kpis_collecte, par]
    kpis_collecte >> objectifs
    par >> [kpis_recouv, benchmarks]
    [objectifs, kpis_recouv, benchmarks] >> alertes >> journal >> fin
