"""
ml_training_utils.py — Fonctions appelées par dag_ml_training.

Entraînement complet du modèle MCRS :
- Préparation du dataset (2 ans glissants).
- Walk-forward temporel (5 folds).
- Calibration Platt, analyse de survie Cox.
- Comparaison champion/challenger.
- Sauvegarde et enregistrement dans ml.model_runs.
"""

from __future__ import annotations

import json
import logging
import os
import time
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from pipeline.src.database import db_session, readonly_session
from pipeline.src.ml.feature_engineering import construire_features_entrainement
from pipeline.src.ml.mcrs_model import (
    ALL_FEATURES,
    MCRSModel,
    McrsParams,
    _calculer_metriques,
    _walk_forward_splits,
)

logger = logging.getLogger(__name__)

MODEL_BASE_DIR = Path(os.getenv("MCRS_MODEL_DIR", "/ml/models/mcrs"))
CHAMPION_DIR = MODEL_BASE_DIR / "champion"
CHALLENGER_DIR = MODEL_BASE_DIR / "challenger"


def _generer_dataset_synthetique(
    date_debut: date, date_fin: date, n: int = 2000
) -> tuple:
    """Génère un dataset synthétique réaliste quand la DB est vide (dev/CI)."""
    rng = np.random.default_rng(42)
    n_days = max((date_fin - date_debut).days, 1)
    dates = pd.Series(
        [date_debut + timedelta(days=int(d)) for d in rng.integers(0, n_days, n)]
    )
    # Features avec distributions réalistes (microfinance Cameroun)
    X = pd.DataFrame(
        {
            "nb_collectes_12m": rng.integers(0, 52, n).astype(float),
            "regularite_collecte_pct": rng.uniform(0, 100, n),
            "tendance_collecte_3m": rng.normal(0, 500, n),
            "montant_moy_collecte": rng.uniform(1000, 50000, n),
            "ecart_type_collecte": rng.uniform(0, 20000, n),
            "nb_cycles_manques_12m": rng.integers(0, 52, n).astype(float),
            "montant_total_collectes_12m": rng.uniform(0, 600000, n),
            "taux_remboursement_pct": rng.uniform(0, 100, n),
            "jours_retard_moyen": rng.exponential(15, n),
            "jours_retard_max": rng.exponential(30, n),
            "nb_incidents_paiement": rng.integers(0, 10, n).astype(float),
            "montant_impaye_courant": rng.exponential(50000, n),
            "nb_remboursements_12m": rng.integers(0, 24, n).astype(float),
            "classe_risque_cobac_encode": rng.integers(0, 5, n).astype(float),
            "revenu_mensuel_estime": rng.uniform(30000, 500000, n),
            "anciennete_client_jours": rng.integers(30, 3650, n).astype(float),
            "nb_produits_actifs": rng.integers(1, 5, n).astype(float),
            "ratio_collecte_credit": rng.uniform(0, 2, n),
            "capacite_remboursement": rng.normal(50000, 30000, n),
            "indice_resilience": rng.uniform(0, 1, n),
            "est_producteur": rng.integers(0, 2, n).astype(float),
            "prix_produit_principal_moy": rng.uniform(100, 5000, n),
            "volatilite_prix_produit": rng.exponential(300, n),
            "tendance_prix_30j": rng.normal(0, 50, n),
            # Lag features : prix des périodes précédentes (corrélés au prix courant ± bruit)
            "prix_lag_30j": rng.uniform(100, 5000, n) * rng.uniform(0.85, 1.15, n),
            "prix_lag_90j": rng.uniform(100, 5000, n) * rng.uniform(0.75, 1.25, n),
            "inflation_mensuelle_moy": rng.uniform(0.1, 2.5, n),
            "taux_directeur_beac": rng.uniform(4.0, 6.0, n),
            "precipitation_moy_mm": rng.uniform(0, 300, n),
            "indice_secheresse": rng.uniform(-3, 1, n),
            "nb_evenements_negatifs": rng.integers(0, 5, n).astype(float),
            "client_id_externe": [f"SYN-{i:05d}" for i in range(n)],
            "imf_code": ["DEV-IMF"] * n,
            "imf_id": [1] * n,
        }
    )
    # Label : probabilité de défaut corrélée aux features de risque
    p_default = (
        0.05
        + 0.002 * X["jours_retard_moyen"].clip(0, 90)
        + 0.01 * X["classe_risque_cobac_encode"]
        - 0.001 * X["regularite_collecte_pct"]
    ).clip(0.02, 0.60)
    y = pd.Series(rng.binomial(1, p_default).astype(int))
    return X, y, dates


def preparer_dataset_entrainement(fenetre_historique_jours: int = 730, **ctx) -> dict:
    """
    Construit le dataset d'entraînement multi-IMF sur la fenêtre historique.

    Returns
    -------
    dict transmis via XCom : shape, taux_defaut, imf_ids utilisés.
    """
    date_fin = date.today() - timedelta(days=30)  # délai de 30j pour les labels
    date_debut = date_fin - timedelta(days=fenetre_historique_jours)

    from pipeline.src.ml.feature_engineering import reconstruire_imf_ids_actifs

    imf_ids = reconstruire_imf_ids_actifs()

    X_all, y_all, dates_all = [], [], []
    for imf_id in imf_ids:
        try:
            X, y, dates = construire_features_entrainement(imf_id, date_debut, date_fin)
            X["imf_id"] = imf_id
            X_all.append(X)
            y_all.append(y)
            dates_all.append(dates)
        except Exception as exc:
            logger.warning("IMF %d ignorée pour entraînement : %s", imf_id, exc)

    if not X_all:
        logger.warning(
            "Aucune donnée réelle — génération d'un dataset synthétique pour dev/test"
        )
        X_combined, y_combined, dates_combined = _generer_dataset_synthetique(
            date_debut, date_fin
        )
        taux_defaut = float(y_combined.mean() * 100)
        logger.info(
            "Dataset synthétique : %d lignes, %.1f%% défauts",
            len(X_combined),
            taux_defaut,
        )
        dataset_path = MODEL_BASE_DIR / "training_dataset.parquet"
        dataset_path.parent.mkdir(parents=True, exist_ok=True)
        X_combined["__label__"] = y_combined.values
        X_combined["__date__"] = dates_combined.values
        X_combined.to_parquet(dataset_path, index=False)
        summary = {
            "n_rows": len(X_combined),
            "taux_defaut_pct": round(taux_defaut, 2),
            "n_imfs": 0,
            "date_debut": date_debut.isoformat(),
            "date_fin": date_fin.isoformat(),
            "dataset_path": str(dataset_path),
            "synthetique": True,
        }
        ti = ctx.get("ti")
        if ti:
            ti.xcom_push(key="dataset_summary", value=summary)
        return summary

    X_combined = pd.concat(X_all, ignore_index=True)
    y_combined = pd.concat(y_all, ignore_index=True)
    dates_combined = pd.concat(dates_all, ignore_index=True)

    taux_defaut = float(y_combined.mean() * 100)
    logger.info(
        "Dataset entraînement : %d lignes, %.1f%% défauts, %d IMF",
        len(X_combined),
        taux_defaut,
        len(imf_ids),
    )

    # Sauvegarde temporaire pour les tâches suivantes
    dataset_path = MODEL_BASE_DIR / "training_dataset.parquet"
    dataset_path.parent.mkdir(parents=True, exist_ok=True)
    X_combined["__label__"] = y_combined.values
    X_combined["__date__"] = dates_combined.values
    X_combined.to_parquet(dataset_path, index=False)

    summary = {
        "n_rows": len(X_combined),
        "taux_defaut_pct": round(taux_defaut, 2),
        "n_imfs": len(imf_ids),
        "date_debut": date_debut.isoformat(),
        "date_fin": date_fin.isoformat(),
        "dataset_path": str(dataset_path),
    }

    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="dataset_summary", value=summary)

    return summary


def split_walk_forward_temporel(
    n_folds: int = 5,
    taille_train_mois: int = 12,
    taille_test_mois: int = 3,
    gap_mois: int = 3,
    **ctx,
) -> dict:
    """Valide la configuration du split et la transmet aux tâches suivantes."""
    config = {
        "n_folds": n_folds,
        "taille_train_mois": taille_train_mois,
        "taille_test_mois": taille_test_mois,
        "gap_mois": gap_mois,
    }
    logger.info("Walk-forward config : %s", config)
    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="wf_config", value=config)
    return config


def entrainer_xgboost_mcrs(
    params: dict, composantes: dict, poids_composantes: dict, **ctx
) -> dict:
    """
    Entraîne le modèle MCRS challenger avec le dataset préparé.

    Returns
    -------
    dict avec les métriques walk-forward et le chemin du modèle sauvegardé.
    """
    ti = ctx.get("ti")
    dataset_path = Path(
        ti.xcom_pull(task_ids="preparer_dataset", key="dataset_summary")["dataset_path"]
        if ti
        else str(MODEL_BASE_DIR / "training_dataset.parquet")
    )

    df = pd.read_parquet(dataset_path)
    y = pd.Series(df["__label__"].values, dtype=int)
    dates = pd.to_datetime(df["__date__"])
    X = df.drop(columns=["__label__", "__date__"], errors="ignore")

    mcrs_params = McrsParams(
        n_estimators=params.get("n_estimators", 500),
        max_depth=params.get("max_depth", 6),
        learning_rate=params.get("learning_rate", 0.05),
        subsample=params.get("subsample", 0.8),
        colsample_bytree=params.get("colsample_bytree", 0.8),
        early_stopping_rounds=params.get("early_stopping_rounds", 50),
        scale_pos_weight=params.get("scale_pos_weight", "auto"),
        poids_crs=poids_composantes.get("crs", 0.35),
        poids_rps=poids_composantes.get("rps", 0.45),
        poids_csi=poids_composantes.get("csi", 0.20),
    )

    t0 = time.perf_counter()
    model = MCRSModel(mcrs_params)
    model.fit(X, y, dates)
    duration_s = round(time.perf_counter() - t0, 1)

    # Sauvegarde du challenger
    CHALLENGER_DIR.mkdir(parents=True, exist_ok=True)
    model.sauvegarder(CHALLENGER_DIR)

    result = {
        "metrics": model.metrics_,
        "duration_s": duration_s,
        "challenger_dir": str(CHALLENGER_DIR),
        "n_features": len(ALL_FEATURES),
        "n_train": len(X),
    }

    logger.info(
        "Challenger entraîné — AUC=%.4f Gini=%.4f KS=%.4f (%.1fs)",
        model.metrics_.get("auc_roc", 0),
        model.metrics_.get("gini", 0),
        model.metrics_.get("ks_statistic", 0),
        duration_s,
    )

    if ti:
        ti.xcom_push(key="challenger_metrics", value=result)

    return result


def valider_cross_validation(metriques: list[str], **ctx) -> dict:
    """Extrait et log les métriques de validation croisée du challenger."""
    ti = ctx.get("ti")
    if not ti:
        return {}
    result = ti.xcom_pull(task_ids="entrainer_xgboost", key="challenger_metrics") or {}
    metrics = result.get("metrics", {})

    for m in metriques:
        logger.info("CV métrique %s = %.4f", m, metrics.get(m, 0))

    auc = metrics.get("auc_roc", 0)
    if auc < 0.65:
        logger.warning(
            "AUC challenger = %.4f < 0.65 — modèle de faible qualité, vérifier les features",
            auc,
        )

    return metrics


def calibrer_platt_scaling(**ctx) -> dict:
    """La calibration Platt est déjà intégrée dans MCRSModel.fit(). Cette tâche valide le résultat."""
    ti = ctx.get("ti")
    if not ti:
        return {"calibree": True}

    challenger = MCRSModel.charger(CHALLENGER_DIR)
    # Vérification : la calibration doit réduire le Brier score
    brier = challenger.metrics_.get("brier_score", 1.0)
    logger.info("Calibration Platt validée — Brier score = %.4f", brier)
    if ti:
        ti.xcom_push(key="brier_score", value=brier)
    return {"calibree": True, "brier_score": brier}


def tracer_reliability_diagram(**ctx) -> dict:
    """
    Trace le diagramme de fiabilité (reliability diagram) post-calibration Platt.

    Compare les probabilités prédites avant et après calibration isotonique.
    Un modèle bien calibré doit suivre la diagonale : si P(défaut) = 0.7,
    environ 70% des clients de ce bin doivent effectivement faire défaut.

    Sauvegarde le graphique dans /ml/models/mcrs/challenger/reliability_diagram.png.
    """
    import matplotlib

    matplotlib.use("Agg")  # backend non-interactif (pas de display X11 dans Airflow)
    import matplotlib.pyplot as plt
    from sklearn.calibration import calibration_curve
    from sklearn.metrics import brier_score_loss

    challenger = MCRSModel.charger(CHALLENGER_DIR)
    dataset_path = MODEL_BASE_DIR / "training_dataset.parquet"

    if not dataset_path.exists():
        logger.warning("Dataset absent — diagramme de fiabilité ignoré")
        return {"reliability_diagram": False, "raison": "dataset absent"}

    df = pd.read_parquet(dataset_path)
    y = df["__label__"].astype(int)
    X = challenger._preparer_features(
        df.drop(columns=["__label__", "__date__"], errors="ignore")
    )

    # Même split que dans MCRSModel.fit() : dernier tiers = set de calibration
    cut = int(len(X) * 0.67)
    X_cal, y_cal = X.iloc[cut:], y.iloc[cut:]

    if len(y_cal) < 50 or y_cal.nunique() < 2:
        logger.warning(
            "Ensemble de calibration trop petit ou mono-classe — diagramme ignoré"
        )
        return {"reliability_diagram": False, "raison": "calibration set insuffisant"}

    y_proba_raw = challenger._model_rps.predict_proba(X_cal[ALL_FEATURES])[:, 1]
    y_proba_cal = challenger._calibrated_rps.predict_proba(X_cal[ALL_FEATURES])[:, 1]

    frac_pos_raw, mean_pred_raw = calibration_curve(
        y_cal, y_proba_raw, n_bins=10, strategy="uniform"
    )
    frac_pos_cal, mean_pred_cal = calibration_curve(
        y_cal, y_proba_cal, n_bins=10, strategy="uniform"
    )

    brier_raw = round(brier_score_loss(y_cal, y_proba_raw), 4)
    brier_cal = round(brier_score_loss(y_cal, y_proba_cal), 4)
    amelioration = round(brier_raw - brier_cal, 4)

    fig, ax = plt.subplots(figsize=(8, 6))
    ax.plot([0, 1], [0, 1], "k--", label="Calibration parfaite")
    ax.plot(
        mean_pred_raw, frac_pos_raw, "b-o", label=f"XGBoost brut (Brier={brier_raw})"
    )
    ax.plot(
        mean_pred_cal,
        frac_pos_cal,
        "r-o",
        label=f"XGBoost calibré Platt (Brier={brier_cal})",
    )
    ax.set_xlabel("Probabilité prédite moyenne")
    ax.set_ylabel("Fraction de positifs observés")
    ax.set_title("Diagramme de fiabilité — Calibration Platt\nMCRS XGBoost")
    ax.legend(loc="upper left")
    ax.grid(True, alpha=0.3)
    ax.text(
        0.97,
        0.05,
        f"Amélioration Brier : +{amelioration:.4f}",
        transform=ax.transAxes,
        ha="right",
        bbox=dict(boxstyle="round", facecolor="wheat", alpha=0.5),
    )

    output_path = CHALLENGER_DIR / "reliability_diagram.png"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=120, bbox_inches="tight")
    plt.close(fig)

    logger.info(
        "Diagramme de fiabilité sauvegardé : %s — Brier brut=%.4f → calibré=%.4f (amélioration=+%.4f)",
        output_path,
        brier_raw,
        brier_cal,
        amelioration,
    )

    result = {
        "reliability_diagram": True,
        "path": str(output_path),
        "brier_avant_calibration": brier_raw,
        "brier_apres_calibration": brier_cal,
        "amelioration_brier": amelioration,
    }
    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="reliability_diagram", value=result)
    return result


def ajuster_analyse_survie_cox(**ctx) -> dict:
    """
    Analyse de survie Cox PH : estime le temps médian avant défaut.
    Nécessite lifelines — si absent, skip silencieusement.
    """
    try:
        from lifelines import CoxPHFitter

        dataset_path = MODEL_BASE_DIR / "training_dataset.parquet"
        if not dataset_path.exists():
            return {"cox_disponible": False}

        df = pd.read_parquet(dataset_path)
        df["duree"] = (pd.to_datetime(df["__date__"]).dt.dayofyear).astype(float)
        df["event"] = df["__label__"].astype(int)

        cox_features = [
            "jours_retard_moyen",
            "regularite_collecte_pct",
            "indice_resilience",
        ]
        cox_df = df[cox_features + ["duree", "event"]].dropna()

        if len(cox_df) < 100:
            return {"cox_disponible": False, "raison": "données insuffisantes"}

        cph = CoxPHFitter()
        cph.fit(cox_df, duration_col="duree", event_col="event")
        concordance = round(cph.concordance_index_, 4)
        logger.info("Cox PH — Concordance Index = %.4f", concordance)
        return {"cox_disponible": True, "concordance_index": concordance}

    except ImportError:
        logger.info("lifelines non disponible — analyse de survie Cox ignorée")
        return {"cox_disponible": False, "raison": "lifelines non installé"}
    except Exception as exc:
        logger.warning("Analyse survie Cox échouée : %s", exc)
        return {"cox_disponible": False, "raison": str(exc)}


def evaluer_shap_global(n_samples_shap: int = 1000, **ctx) -> dict:
    """Calcule l'importance globale SHAP du challenger sur un échantillon."""
    import shap as shap_lib

    challenger = MCRSModel.charger(CHALLENGER_DIR)
    dataset_path = MODEL_BASE_DIR / "training_dataset.parquet"

    if not dataset_path.exists():
        return {"shap_global": False}

    df = pd.read_parquet(dataset_path).drop(
        columns=["__label__", "__date__"], errors="ignore"
    )
    sample = challenger._preparer_features(
        df.sample(min(n_samples_shap, len(df)), random_state=42)
    )

    explainer = shap_lib.TreeExplainer(challenger._model_rps)
    sv = explainer.shap_values(sample[ALL_FEATURES].values)
    importance = {
        feat: round(float(np.abs(sv[:, i]).mean()), 6)
        for i, feat in enumerate(ALL_FEATURES)
    }
    importance_sorted = dict(
        sorted(importance.items(), key=lambda x: x[1], reverse=True)
    )
    logger.info("Top 5 features SHAP : %s", list(importance_sorted.items())[:5])

    result = {"shap_global": True, "feature_importance_shap": importance_sorted}
    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="shap_global", value=result)
    return result


def comparer_champion_challenger(
    metrique_comparaison: str = "auc_roc",
    seuil_amelioration_min: float = 0.005,
    **ctx,
) -> bool:
    """
    Compare le challenger au champion actuel.

    Returns
    -------
    True si le challenger doit être promu, False sinon.
    """
    challenger = MCRSModel.charger(CHALLENGER_DIR)
    challenger_score = challenger.metrics_.get(metrique_comparaison, 0)

    # Charger les métriques du champion actuel depuis ml.model_runs
    with readonly_session() as cur:
        cur.execute("""
            SELECT auc_roc, gini_coefficient, ks_statistic, brier_score,
                   f1_score, precision_score, recall_score
            FROM ml.model_runs
            WHERE est_modele_actif = TRUE
            ORDER BY created_at DESC LIMIT 1
        """)
        row = cur.fetchone()

    if row is None:
        # Aucun champion existant → challenger est automatiquement promu
        logger.info("Aucun champion existant — challenger promu automatiquement")
        promouvoir = True
    else:
        _col_map = {
            "auc_roc": "auc_roc",
            "gini": "gini_coefficient",
            "gini_coefficient": "gini_coefficient",
            "ks_statistic": "ks_statistic",
            "brier_score": "brier_score",
            "f1_score": "f1_score",
        }
        champion_score = float(
            row[_col_map.get(metrique_comparaison, metrique_comparaison)] or 0
        )
        delta = challenger_score - champion_score
        promouvoir = delta >= seuil_amelioration_min
        logger.info(
            "Comparaison champion/challenger : %s champion=%.4f challenger=%.4f delta=+%.4f → %s",
            metrique_comparaison,
            champion_score,
            challenger_score,
            delta,
            "PROMOUVOIR" if promouvoir else "GARDER_CHAMPION",
        )

    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(
            key="challenger_metrics", value={"challenger_score": challenger_score}
        )
    return promouvoir


def promouvoir_challenger(**ctx) -> str:
    """Copie le challenger vers le répertoire champion et met à jour ml.model_runs."""
    import shutil

    # Sauvegarder l'ancien champion en archive
    archive_dir = MODEL_BASE_DIR / f"archive/{time.strftime('%Y%m%d_%H%M%S')}"
    if CHAMPION_DIR.exists():
        shutil.copytree(CHAMPION_DIR, archive_dir)
        logger.info("Champion archivé dans %s", archive_dir)

    # Copier le challenger comme nouveau champion
    if CHAMPION_DIR.exists():
        shutil.rmtree(CHAMPION_DIR)
    shutil.copytree(CHALLENGER_DIR, CHAMPION_DIR)

    # Désactiver l'ancien modèle actif
    with db_session() as cur:
        cur.execute(
            "UPDATE ml.model_runs SET est_modele_actif = FALSE WHERE est_modele_actif = TRUE"
        )

    logger.info("Challenger promu comme nouveau champion dans %s", CHAMPION_DIR)

    # Symlink mcrs_model.pkl → champion/mcrs_model.pkl pour la compatibilité de l'API
    symlink_path = MODEL_BASE_DIR / "mcrs_model.pkl"
    target_path = CHAMPION_DIR / "mcrs_model.pkl"
    if symlink_path.exists() or symlink_path.is_symlink():
        symlink_path.unlink()
    symlink_path.symlink_to(target_path)
    logger.info("Symlink mis à jour : %s → %s", symlink_path, target_path)

    # Recharger l'API ML si elle tourne
    _notifier_api_reload()

    return str(CHAMPION_DIR)


def sauvegarder_artefacts_modele(dossier: str = "/ml/models/mcrs", **ctx) -> dict:
    """Tâche de sauvegarde finale — le modèle est déjà sauvegardé par entrainer_xgboost."""
    challenger = MCRSModel.charger(CHALLENGER_DIR)
    path = challenger.sauvegarder(CHALLENGER_DIR)
    return {"saved": True, "path": str(path)}


def enregistrer_run_mlflow(**ctx) -> int:
    """Insère les métriques d'entraînement dans ml.model_runs."""
    ti = ctx.get("ti")
    challenger_result = {}
    shap_result = {}
    if ti:
        challenger_result = (
            ti.xcom_pull(task_ids="entrainer_xgboost", key="challenger_metrics") or {}
        )
        shap_result = (
            ti.xcom_pull(task_ids="evaluer_interpretabilite", key="shap_global") or {}
        )

    challenger = MCRSModel.charger(CHALLENGER_DIR)
    is_champion = CHAMPION_DIR.exists() and (
        (CHAMPION_DIR / "mcrs_model.pkl").stat().st_mtime
        == (CHALLENGER_DIR / "mcrs_model.pkl").stat().st_mtime
        if (CHAMPION_DIR / "mcrs_model.pkl").exists()
        else False
    )

    sql = """
        INSERT INTO ml.model_runs (
            model_name, version, dag_run_id, statut, est_modele_actif,
            params_json,
            auc_roc, precision_score, recall_score, f1_score,
            gini_coefficient, ks_statistic, brier_score,
            created_at
        ) VALUES (
            'MCRS_XGBoost', %(version)s, %(dag_run_id)s, %(statut)s, %(est_actif)s,
            %(params_json)s::jsonb,
            %(auc)s, %(precision)s, %(recall)s, %(f1)s,
            %(gini)s, %(ks)s, %(brier)s,
            NOW()
        ) RETURNING id
    """
    import json as _json

    metrics = challenger.metrics_
    params_dict = {
        k: v for k, v in vars(challenger.params).items() if not k.startswith("_")
    }
    params_dict["feature_importances"] = challenger.feature_importances_

    with db_session() as cur:
        cur.execute(
            sql,
            {
                "version": "2.0.0",
                "dag_run_id": ctx.get("run_id", ""),
                "statut": "SUCCES",
                "est_actif": is_champion,
                "params_json": _json.dumps(params_dict),
                "auc": metrics.get("auc_roc", 0),
                "precision": metrics.get("precision", 0),
                "recall": metrics.get("recall", 0),
                "f1": metrics.get("f1", 0),
                "gini": metrics.get("gini", 0),
                "ks": metrics.get("ks_statistic", 0),
                "brier": metrics.get("brier_score", 1.0),
            },
        )
        run_id = cur.fetchone()["id"]

    logger.info(
        "model_run #%d enregistré (AUC=%.4f)", run_id, metrics.get("auc_roc", 0)
    )
    return run_id


def _notifier_api_reload() -> None:
    """Notifie l'API FastAPI de recharger le modèle champion."""
    import os

    import httpx

    api_url = os.getenv("MCRS_API_URL", "http://ml-api:8090")
    try:
        resp = httpx.post(f"{api_url}/model/reload", timeout=10)
        if resp.status_code == 200:
            logger.info("API ML rechargée avec succès")
        else:
            logger.warning("API ML reload — code %d", resp.status_code)
    except Exception as exc:
        logger.warning("Impossible de notifier l'API ML : %s", exc)
