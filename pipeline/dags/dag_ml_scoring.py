"""
DAG : dag_ml_scoring
Fréquence : quotidien à 07h30 (après dag_recouvrement et dag_donnees_externes)

Pipeline MCRS (Multi-Criteria Recovery Scoring) :
  1. preparer_features        — dbt run ml.feat_client_comportemental + feat_client_externe
  2. assembler_feature_store  — jointure toutes features → ml.features_client
  3. charger_modele           — charge le dernier modèle actif depuis ml.model_runs
  4. scorer_clients           — scoring MCRS par batch, INSERT ml.client_scores
  5. calculer_shap            — SHAP values → ml.shap_explanations
  6. generer_alertes_ml       — alertes RISQUE_DEFAUT_IMMINENT, TENDANCE_NEGATIVE, etc.
  7. maj_priorites_dossiers   — mise à jour priorité dans app.dossiers_recouvrement
  8. retrain_si_necessaire    — déclenchement retrain si drift détecté (PSI > 0.2)
  9. notifier_responsables    — push FCM + SSE alertes ML critiques
 10. log_journal

Retrain séparé (hebdomadaire) : dag_ml_training
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.empty import EmptyOperator
from airflow.operators.python import BranchPythonOperator, PythonOperator
from airflow.operators.trigger_dagrun import TriggerDagRunOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.dbt_utils import dbt_run_select
from scripts.ingestion_utils import log_journal
from scripts.ml_alertes_utils import generer_alertes_predictives
from scripts.ml_scoring_utils import (
    calculer_shap_values,
    charger_modele_actif,
    detecter_drift_psi_segmente,
    maj_priorites_dossiers_recouvrement,
    scorer_clients_batch,
)
from scripts.notification_utils import (
    notifier_directeurs_fcm,
    notifier_responsables_sse,
)

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 2,
    "retry_delay": timedelta(minutes=10),
    "email_on_failure": False,
}


def _brancher_retrain(**ctx):
    psi = ctx["ti"].xcom_pull(task_ids="detecter_drift")
    return "declencher_retrain" if psi and psi > 0.20 else "skip_retrain"


with DAG(
    dag_id="dag_ml_scoring",
    description="Scoring MCRS quotidien — CRS+RPS+CSI, SHAP, alertes prédictives, drift",
    schedule_interval="30 7 * * *",  # 07h30
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["ml", "scoring", "mcrs", "shap", "xgboost"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    feat_comportemental = PythonOperator(
        task_id="feat_comportemental",
        python_callable=dbt_run_select,
        op_kwargs={
            # Les 3 modèles granulaires précédents (feat_collecte_regularite,
            # feat_remboursement_historique, feat_client_anciennete)
            # n'avaient jamais existé dans le projet dbt réel — la logique
            # comportementale est en fait dans ce seul modèle intermediate.
            # Pas de "+" : stg_creances/stg_collectes_epargne (ses refs)
            # dépendent de raw.export_cbs/raw.collectes_terrain, jamais
            # alimentées (aucune ingestion CBS réelle configurée) — elles
            # existent déjà comme tables (build antérieur) et ne doivent pas
            # être reconstruites tant que cette source n'existe pas.
            "select": "intermediate.int_profil_recouvrement_client",
        },
        doc="Profil comportemental recouvrement (créances CBS + historique collectes)",
    )

    feat_externe = PythonOperator(
        task_id="feat_externe",
        python_callable=dbt_run_select,
        op_kwargs={
            # cf. feat_comportemental : les 4 modèles granulaires précédents
            # n'avaient jamais existé — un seul modèle réel les regroupe.
            # "+" inclus : ses refs (stg_meteo, stg_indicateurs_macro)
            # dépendent uniquement de app.donnees_meteo/app.facteurs_macro
            # (données réelles, pas de raw.*) — reconstruction sûre.
            "select": "+ml.feat_client_externe",
        },
        doc="Features externes : prix produit principal (NULL tant que non ingéré), inflation, météo, distance marché",
    )

    assembler = PythonOperator(
        task_id="assembler_feature_store",
        python_callable=dbt_run_select,
        op_kwargs={"select": "ml.features_client", "full_refresh": False},
        doc="Jointure finale toutes features → ml.features_client (feature store)",
    )

    charger_modele = PythonOperator(
        task_id="charger_modele",
        python_callable=charger_modele_actif,
        doc="Charge le modèle XGBoost MCRS actif (est_modele_actif=TRUE dans ml.model_runs)",
    )

    scorer = PythonOperator(
        task_id="scorer_clients",
        python_callable=scorer_clients_batch,
        op_kwargs={
            "batch_size": 500,
            "poids_crs": 0.35,  # Collection Reliability Score
            "poids_rps": 0.45,  # Recovery Prediction Score
            "poids_csi": 0.20,  # Client Solvency Index
        },
        doc="Score MCRS composite = 0.35*CRS + 0.45*RPS + 0.20*CSI, avec CI à 90%",
    )

    shap = PythonOperator(
        task_id="calculer_shap",
        python_callable=calculer_shap_values,
        op_kwargs={"top_n_features": 10},
        doc="SHAP TreeExplainer — top 10 features par client dans ml.shap_explanations",
    )

    alertes_ml = PythonOperator(
        task_id="generer_alertes_ml",
        python_callable=generer_alertes_predictives,
        op_kwargs={
            "seuil_defaut_critique": 0.75,
            "seuil_baisse_collecte_pct": -20.0,
            "seuil_degradation_score": -0.15,
        },
    )

    maj_dossiers = PythonOperator(
        task_id="maj_priorites_dossiers",
        python_callable=maj_priorites_dossiers_recouvrement,
        doc="Met à jour app.dossiers_recouvrement.priorite_scoring avec score MCRS du jour",
    )

    detecter_drift = PythonOperator(
        task_id="detecter_drift",
        python_callable=detecter_drift_psi_segmente,
        op_kwargs={"fenetre_reference_jours": 90, "fenetre_courante_jours": 7},
        doc="PSI segmenté zone×produit — détecte drifts localisés, déclenche retrain si max(PSI) > 0.20",
    )

    brancher = BranchPythonOperator(
        task_id="brancher_retrain",
        python_callable=_brancher_retrain,
    )

    retrain = TriggerDagRunOperator(
        task_id="declencher_retrain",
        trigger_dag_id="dag_ml_training",
        wait_for_completion=False,
        reset_dag_run=True,
        doc="Déclenche dag_ml_training si PSI > 0.20 (drift significatif)",
    )

    skip_retrain = EmptyOperator(task_id="skip_retrain")

    notif_sse = PythonOperator(
        task_id="notifier_responsables_sse",
        python_callable=notifier_responsables_sse,
        op_kwargs={"event": "scoring_updated"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    notif_fcm = PythonOperator(
        task_id="notifier_directeurs_alertes",
        python_callable=notifier_directeurs_fcm,
        op_kwargs={"type_notif": "ALERTE_ML_CRITIQUE"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={"dag_id": "dag_ml_scoring", "table_cible": "ml.client_scores"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    # Graphe
    debut >> [feat_comportemental, feat_externe]
    [feat_comportemental, feat_externe] >> assembler >> [charger_modele]
    charger_modele >> scorer >> shap
    shap >> [alertes_ml, maj_dossiers, detecter_drift]
    detecter_drift >> brancher >> [retrain, skip_retrain]
    [alertes_ml, maj_dossiers, retrain, skip_retrain] >> notif_sse
    alertes_ml >> notif_fcm
    [notif_sse, notif_fcm] >> journal >> fin
