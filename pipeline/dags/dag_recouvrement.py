"""
DAG : dag_recouvrement
Fréquence : quotidien à 06h00

Pipeline :
  1. ingerer_export_cbs       — parse fichier CBS du jour (CSV/Excel)
  2. valider_cbs              — contrôles qualité, typage, doublons
  3. dbt_stg_creances         — dbt run --select staging.stg_creances
  4. calculer_par             — calcul PAR30/60/90/180, provisions COBAC
  5. synchroniser_creances    — upsert app.creances depuis staging
  6. creer_dossiers           — création auto dossiers si créance PAR30+
  7. prioriser_dossiers       — tri par score MCRS si disponible
  8. verifier_promesses       — alertes promesses de paiement échues
  9. calculer_kpis_recap      — maj app.kpi_recouvrement_snapshots
 10. benchmarks               — calcul benchmarks inter-agences
 11. alertes_par              — alerte si PAR90 > seuil configurable
 12. notifier_directeurs      — push FCM + SSE DIRECTEUR / RESPONSABLE
 13. log_journal
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.empty import EmptyOperator
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.dbt_utils import dbt_run_select
from scripts.ingestion_utils import generer_alertes_operationnelles, log_journal
from scripts.notification_utils import (
    envoyer_email_resume_quotidien,
    notifier_directeurs_fcm,
    notifier_responsables_sse,
)
from scripts.recouvrement_utils import (
    calculer_benchmarks_agences,
    calculer_kpis_recouvrement,
    calculer_par_et_provisions,
    creer_dossiers_automatiques,
    ingerer_export_cbs,
    prioriser_dossiers_par_score,
    synchroniser_creances_app,
    valider_donnees_cbs,
    verifier_promesses_echeues,
)

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 2,
    "retry_delay": timedelta(minutes=10),
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_recouvrement",
    description="Pipeline quotidien recouvrement créances — CBS, PAR, provisions COBAC, benchmarks",
    schedule_interval="0 6 * * *",  # 06h00 chaque jour
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["recouvrement", "cbs", "par", "cobac", "core"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    ingerer_cbs = PythonOperator(
        task_id="ingerer_export_cbs",
        python_callable=ingerer_export_cbs,
        op_kwargs={"dossier_entrant": "/data/cbs/incoming"},
        doc="Scanne le dossier CBS et insère les lignes nouvelles dans raw.export_cbs",
    )

    valider = PythonOperator(
        task_id="valider_cbs",
        python_callable=valider_donnees_cbs,
        doc="Contrôles intégrité, montants > 0, cohérence dates, typage",
    )

    dbt_staging = PythonOperator(
        task_id="dbt_stg_creances",
        python_callable=dbt_run_select,
        op_kwargs={"select": "staging.stg_creances staging.stg_clients"},
    )

    par = PythonOperator(
        task_id="calculer_par",
        python_callable=calculer_par_et_provisions,
        op_kwargs={
            "seuils_cobac": {
                "B": {"jours_min": 30, "jours_max": 89, "taux": 20},
                "C": {"jours_min": 90, "jours_max": 179, "taux": 50},
                "D": {"jours_min": 180, "jours_max": 359, "taux": 80},
                "E": {"jours_min": 360, "jours_max": None, "taux": 100},
            }
        },
        doc="Calcule PAR30/60/90/180, classe COBAC A-E et montant provision réglementaire",
    )

    sync_creances = PythonOperator(
        task_id="synchroniser_creances",
        python_callable=synchroniser_creances_app,
        doc="Upsert app.creances depuis staging.stg_creances — préserve les données recouvrement",
    )

    creer_dossiers = PythonOperator(
        task_id="creer_dossiers",
        python_callable=creer_dossiers_automatiques,
        op_kwargs={"seuil_par_jours": 30},
        doc="Crée automatiquement app.dossiers_recouvrement pour nouvelles créances PAR30+",
    )

    prioriser = PythonOperator(
        task_id="prioriser_dossiers",
        python_callable=prioriser_dossiers_par_score,
        doc="Classe les dossiers ouverts par score MCRS décroissant pour assignation agents",
    )

    promesses = PythonOperator(
        task_id="verifier_promesses",
        python_callable=verifier_promesses_echeues,
        doc="Marque ROMPUE les promesses échues sans règlement, génère alertes PROMESSE_ECHEANCE",
    )

    kpis = PythonOperator(
        task_id="calculer_kpis_recap",
        python_callable=calculer_kpis_recouvrement,
        op_kwargs={"periodes": ["MENSUEL"]},
    )

    benchmarks = PythonOperator(
        task_id="benchmarks",
        python_callable=calculer_benchmarks_agences,
        doc="Calcule z-scores et rangs relatifs inter-agences sur collecte et recouvrement",
    )

    alertes_par = PythonOperator(
        task_id="alertes_par",
        python_callable=generer_alertes_operationnelles,
        op_kwargs={
            "types": [
                "PAR_SEUIL_DEPASSE",
                "PROVISION_INSUFFISANTE",
                "DOSSIER_SANS_ACTION",
            ],
            "seuil_par90_pct": 5.0,
        },
    )

    notif_dir = PythonOperator(
        task_id="notifier_directeurs",
        python_callable=notifier_directeurs_fcm,
        op_kwargs={"type_notif": "RECAP_RECOUVREMENT_QUOTIDIEN"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    notif_sse = PythonOperator(
        task_id="notifier_responsables_sse",
        python_callable=notifier_responsables_sse,
        op_kwargs={"event": "recouvrement_updated"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    email = PythonOperator(
        task_id="email_resume",
        python_callable=envoyer_email_resume_quotidien,
        op_kwargs={"destinataires_role": ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT"]},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={"dag_id": "dag_recouvrement", "table_cible": "staging.stg_creances"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    # Chaîne principale
    debut >> ingerer_cbs >> valider >> dbt_staging >> par >> sync_creances
    sync_creances >> creer_dossiers >> prioriser >> promesses
    promesses >> kpis >> benchmarks >> alertes_par
    alertes_par >> [notif_dir, notif_sse, email]
    [notif_dir, notif_sse, email] >> journal >> fin
