"""
dag_alertes_impayes.py — Génération ciblée des alertes impayés.

Peut être déclenché manuellement ou par un autre DAG via TriggerDagRunOperator.
Génère :
  - Alertes opérationnelles PAR (staging.alertes_operationnelles)
  - Alertes prédictives ML (ml.alertes_predictives)
  - Notifications FCM + SSE aux responsables recouvrement

En fonctionnement normal ces alertes sont intégrées dans :
  - dag_recouvrement  (alertes PAR)
  - dag_ml_scoring    (alertes prédictives MCRS)
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.empty import EmptyOperator
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.ingestion_utils import generer_alertes_operationnelles, log_journal
from scripts.ml_alertes_utils import generer_alertes_predictives
from scripts.notification_utils import (
    envoyer_email_resume_quotidien,
    notifier_directeurs_fcm,
    notifier_responsables_sse,
)

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_alertes_impayes",
    description="Alertes impayés PAR30+ et prédictives MCRS — peut être déclenché manuellement",
    schedule_interval=None,  # Déclenché par dag_recouvrement ou manuellement
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["alertes", "recouvrement", "ml", "par"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    # Alertes réglementaires (PAR COBAC, dossiers sans action)
    alertes_ops = PythonOperator(
        task_id="alertes_operationnelles",
        python_callable=generer_alertes_operationnelles,
        op_kwargs={
            "types": [
                "PAR_SEUIL_DEPASSE",
                "DOSSIER_SANS_ACTION",
                "PROMESSE_ECHEANCE",
            ],
            "seuil_par90_pct": 5.0,
        },
    )

    # Alertes prédictives ML (risque défaut, détérioration score)
    alertes_ml = PythonOperator(
        task_id="alertes_predictives_ml",
        python_callable=generer_alertes_predictives,
        op_kwargs={
            "seuil_defaut_critique": 0.75,
            "seuil_baisse_collecte_pct": -20.0,
            "seuil_degradation_score": -0.15,
        },
    )

    # Notifications
    notif_sse = PythonOperator(
        task_id="notifier_responsables_sse",
        python_callable=notifier_responsables_sse,
        op_kwargs={"event": "alertes_impayes_updated"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    notif_fcm = PythonOperator(
        task_id="notifier_directeurs_fcm",
        python_callable=notifier_directeurs_fcm,
        op_kwargs={"type_notif": "PAR_SEUIL_DEPASSE"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    email = PythonOperator(
        task_id="email_resume_alertes",
        python_callable=envoyer_email_resume_quotidien,
        op_kwargs={"destinataires_role": ["RESPONSABLE_RECOUVREMENT", "DIRECTEUR"]},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={
            "dag_id": "dag_alertes_impayes",
            "table_cible": "app.alertes_operationnelles",
        },
        trigger_rule=TriggerRule.ALL_DONE,
    )

    debut >> [alertes_ops, alertes_ml]
    alertes_ops >> [notif_sse, notif_fcm, email]
    alertes_ml >> [notif_sse, notif_fcm, email]
    [notif_sse, notif_fcm, email] >> journal >> fin
