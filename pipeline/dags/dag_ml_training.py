"""
DAG : dag_ml_training
Fréquence : hebdomadaire (dimanche 02h00) ou déclenché par dag_ml_scoring (drift PSI > 0.20)

Entraînement complet du modèle MCRS avec walk-forward temporelle :
  1. preparer_dataset         — extraction features_client sur fenêtre historique
  2. split_temporal           — k folds temporels walk-forward (pas de fuite future)
  3. entrainer_xgboost        — XGBoost avec hyperparamètres configurables
  4. validation_croisee       — AUC-ROC, Gini, KS, Brier score par fold
  5. calibrer_platt           — calibration isotonique (Platt scaling) pour P(défaut)
  6. analyse_survie           — Cox PH pour temps_survie_median_jours
  7. evaluer_interpretabilite — SHAP global, importance features
  8. comparer_challenger      — compare au modèle champion actuel
  9. promouvoir_si_meilleur   — bascule est_modele_actif si challenger > champion
 10. sauvegarder_artefacts    — pickle + JSON params dans /ml/models/
 11. log_mlflow               — insert ml.model_runs avec toutes les métriques
"""
from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator, BranchPythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.ml_training_utils import (
    preparer_dataset_entrainement,
    split_walk_forward_temporel,
    entrainer_xgboost_mcrs,
    valider_cross_validation,
    calibrer_platt_scaling,
    tracer_reliability_diagram,
    ajuster_analyse_survie_cox,
    evaluer_shap_global,
    comparer_champion_challenger,
    promouvoir_challenger,
    sauvegarder_artefacts_modele,
    enregistrer_run_mlflow,
)
from scripts.ingestion_utils import log_journal

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 1,
    "retry_delay": timedelta(minutes=30),
    "email_on_failure": False,
}


def _brancher_promotion(**ctx):
    meilleur = ctx["ti"].xcom_pull(task_ids="comparer_challenger")
    return "promouvoir_challenger" if meilleur else "garder_champion"


with DAG(
    dag_id="dag_ml_training",
    description="Entraînement hebdomadaire MCRS — walk-forward, XGBoost, Platt, Cox, SHAP",
    schedule_interval="0 2 * * 0",         # dimanche 02h00
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["ml", "training", "mcrs", "xgboost", "walk-forward"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin   = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    dataset = PythonOperator(
        task_id="preparer_dataset",
        python_callable=preparer_dataset_entrainement,
        op_kwargs={"fenetre_historique_jours": 730},   # 2 ans
    )

    split = PythonOperator(
        task_id="split_temporal",
        python_callable=split_walk_forward_temporel,
        op_kwargs={
            "n_folds": 5,
            "taille_train_mois": 12,
            "taille_test_mois": 3,
            "gap_mois": 3,   # 3 mois : évite la fuite sur créances COBAC C/D/E (horizon ≥ 90j)
        },
    )

    entrainer = PythonOperator(
        task_id="entrainer_xgboost",
        python_callable=entrainer_xgboost_mcrs,
        op_kwargs={
            "params": {
                "n_estimators":       500,
                "max_depth":          6,
                "learning_rate":      0.05,
                "subsample":          0.8,
                "colsample_bytree":   0.8,
                "scale_pos_weight":   "auto",           # déséquilibre classes
                "objective":          "binary:logistic",
                "eval_metric":        ["auc", "logloss"],
                "early_stopping_rounds": 50,
            },
            "composantes": {
                "crs_features": [
                    "nb_collectes_12m", "regularite_collecte_pct",
                    "tendance_collecte_3m", "nb_cycles_manques_12m",
                    "montant_moy_collecte", "ecart_type_collecte",
                ],
                "rps_features": [
                    "taux_remboursement_pct", "jours_retard_moyen",
                    "jours_retard_max", "nb_incidents_paiement",
                    "montant_impaye_courant", "categorie_par",
                    "classe_risque_cobac",
                ],
                "csi_features": [
                    "revenu_mensuel_estime", "anciennete_client_jours",
                    "ratio_collecte_credit", "capacite_remboursement",
                    "indice_resilience", "nb_produits_vendus",
                    "prix_produit_principal_moy", "volatilite_prix_produit",
                    "tendance_prix_30j", "inflation_mensuelle_moy",
                    "taux_directeur_beac", "precipitation_moy_mm",
                    "indice_secheresse_max", "nb_evenements_negatifs",
                    "distance_agence_km", "distance_marche_km",
                ],
            },
            "poids_composantes": {"crs": 0.35, "rps": 0.45, "csi": 0.20},
        },
    )

    cv = PythonOperator(
        task_id="validation_croisee",
        python_callable=valider_cross_validation,
        op_kwargs={"metriques": ["auc_roc", "gini", "ks_statistic", "brier_score", "f1"]},
    )

    platt = PythonOperator(
        task_id="calibrer_platt",
        python_callable=calibrer_platt_scaling,
        doc="Calibration isotonique pour que P(défaut) soit bien calibrée",
    )

    reliability = PythonOperator(
        task_id="tracer_reliability_diagram",
        python_callable=tracer_reliability_diagram,
        doc="Reliability diagram : compare Brier avant/après Platt — artefact sauvegardé dans challenger/",
    )

    survie = PythonOperator(
        task_id="analyse_survie_cox",
        python_callable=ajuster_analyse_survie_cox,
        doc="Cox Proportional Hazards — estime temps_survie_median avant défaut",
    )

    shap_global = PythonOperator(
        task_id="evaluer_interpretabilite",
        python_callable=evaluer_shap_global,
        op_kwargs={"n_samples_shap": 1000},
    )

    comparer = PythonOperator(
        task_id="comparer_challenger",
        python_callable=comparer_champion_challenger,
        op_kwargs={"metrique_comparaison": "auc_roc", "seuil_amelioration_min": 0.005},
        doc="Challenger doit battre le champion d'au moins 0.5% AUC-ROC pour être promu",
    )

    brancher = BranchPythonOperator(
        task_id="brancher_promotion",
        python_callable=_brancher_promotion,
    )

    promouvoir = PythonOperator(
        task_id="promouvoir_challenger",
        python_callable=promouvoir_challenger,
    )

    garder = EmptyOperator(task_id="garder_champion")

    sauvegarder = PythonOperator(
        task_id="sauvegarder_artefacts",
        python_callable=sauvegarder_artefacts_modele,
        op_kwargs={"dossier": "/ml/models/mcrs"},
        trigger_rule=TriggerRule.ONE_SUCCESS,
    )

    mlflow_log = PythonOperator(
        task_id="log_mlflow",
        python_callable=enregistrer_run_mlflow,
        trigger_rule=TriggerRule.ALL_DONE,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={"dag_id": "dag_ml_training", "table_cible": "ml.model_runs"},
        trigger_rule=TriggerRule.ALL_DONE,
    )

    debut >> dataset >> split >> entrainer >> cv >> platt >> reliability
    reliability >> [survie, shap_global]
    survie >> shap_global >> comparer >> brancher
    brancher >> [promouvoir, garder]
    [promouvoir, garder] >> sauvegarder >> mlflow_log >> journal >> fin
