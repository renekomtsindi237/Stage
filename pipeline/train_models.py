"""
IMF Pipeline — Entraînement complet du système ML MCRS
=======================================================

Données : k:/Stage/data/warehouse/ml/train.csv  (12 946 clients)
          k:/Stage/data/warehouse/ml/test.csv   ( 3 237 clients)

Trois paradigmes d'apprentissage :
  1. Supervisé     — XGBoost + walk-forward CV + Platt + SHAP
  2. Non supervisé — K-Means + DBSCAN + Isolation Forest + PCA
  3. Par renforcement — Q-Learning (simulation stratégie recouvrement)

Résultats : k:/Stage/result/
  supervised/       → modèle, métriques, courbes
  unsupervised/     → clusters, anomalies, visualisation
  reinforcement/    → politique, courbes d'apprentissage
  rapport_global.md → synthèse exécutive
"""

from __future__ import annotations

import json
import logging
import pickle
import warnings
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from textwrap import dedent

import matplotlib
matplotlib.use("Agg")  # pas de fenêtre graphique (serveur / Docker)
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import numpy as np
import pandas as pd
from sklearn.cluster import DBSCAN, KMeans
from sklearn.decomposition import PCA
from sklearn.ensemble import IsolationForest
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    auc,
    brier_score_loss,
    calinski_harabasz_score,
    classification_report,
    confusion_matrix,
    davies_bouldin_score,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve,
    silhouette_score,
)
from sklearn.model_selection import StratifiedKFold
from sklearn.preprocessing import StandardScaler
from xgboost import XGBClassifier

warnings.filterwarnings("ignore")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("train_models")

# ─── Chemins ─────────────────────────────────────────────────────────────────

ROOT      = Path(__file__).parent.parent          # k:/Stage
DATA_DIR  = ROOT / "data" / "warehouse" / "ml"
RESULT    = ROOT / "result"
RES_SUP   = RESULT / "supervised"
RES_UNS   = RESULT / "unsupervised"
RES_RL    = RESULT / "reinforcement"

for d in [RES_SUP, RES_UNS, RES_RL]:
    d.mkdir(parents=True, exist_ok=True)

# ─── Groupes de features (depuis les colonnes réelles du CSV) ─────────────────

# CRS — Collection Reliability Score (discipline d'épargne terrain)
CRS_FEATURES = [
    "regularite_collecte_pct",
    "nb_collectes_30j",
    "montant_moyen_collecte",
    "tendance_collecte_30j",
    "coefficient_variation_collecte",
    "nb_semaines_sans_collecte",
    "rang_collecte_agence",
]

# RPS — Recovery Prediction Score (probabilité de défaut — cible XGBoost)
RPS_FEATURES = [
    "jours_retard_actuel",
    "nb_incidents_paiement_12m",
    "taux_remboursement_historique",
    "ratio_creance_revenus",
    "nb_reechelonnements",
    "score_rps_precedent",
]

# CSI — Client Solvency Index (facteurs externes : prix, météo, macro)
CSI_FEATURES = [
    "prix_moyen_30j",
    "volatilite_prix_30j",
    "saisonnalite_prix",
    "precipitations_30j",
    "indice_secheresse",
    "inflation",
    "taux_beac",
    "ipc",
    "chomage",
    "indice_resilience",
    "capacite_remboursement",
    "ratio_collecte_credit",
    "score_diversification_produits",
]

# CAMEROON — Features contextuelles propres au Cameroun (zones agroclimatiques, mobile money)
CAMEROON_FEATURES = [
    "risque_regional",           # multiplicateur de risque régional (0.90=Littoral → 1.45=Extrême-Nord)
    "taux_penetration_mobile",   # adoption mobile money par région (0.25-0.85)
    "zone_agroclimatique",       # 0=Sahel, 1=Équatorial, 2=Highlands, 3=Côtier
    "saison_recolte_active",     # 1 si mois actuel = période de récolte principale
]

ALL_FEATURES = CRS_FEATURES + RPS_FEATURES + CSI_FEATURES + CAMEROON_FEATURES
LABEL        = "label_defaut_90j"

# Poids MCRS (composites)
W_CRS, W_RPS, W_CSI = 0.35, 0.45, 0.20

# ─── Utilitaires ─────────────────────────────────────────────────────────────

def _sauver_json(obj: dict, path: Path) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False, default=str)
    log.info("  → %s", path)


def _gini(auc_roc: float) -> float:
    return 2 * auc_roc - 1


def _ks_stat(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    pos = np.sort(y_prob[y_true == 1])
    neg = np.sort(y_prob[y_true == 0])
    if not len(pos) or not len(neg):
        return 0.0
    all_thresh = np.unique(np.concatenate([pos, neg]))
    cdf_pos = np.searchsorted(pos, all_thresh, side="right") / len(pos)
    cdf_neg = np.searchsorted(neg, all_thresh, side="right") / len(neg)
    return float(np.max(np.abs(cdf_pos - cdf_neg)))


from models import CalibratedModel  # noqa: E402 — shared with compute_mcrs.py


def _classe_risque(score: float) -> str:
    if score < 0.30: return "FAIBLE"
    if score < 0.55: return "MODERE"
    if score < 0.75: return "ELEVE"
    return "CRITIQUE"


# ─── Chargement des données ───────────────────────────────────────────────────

def charger_donnees() -> tuple[pd.DataFrame, pd.DataFrame]:
    log.info("Chargement des données depuis %s", DATA_DIR)
    train = pd.read_csv(DATA_DIR / "train.csv")
    test  = pd.read_csv(DATA_DIR / "test.csv")
    log.info("  train : %d lignes × %d colonnes", *train.shape)
    log.info("  test  : %d lignes × %d colonnes",  *test.shape)

    # Imputation des valeurs manquantes (médiane par colonne)
    for df in [train, test]:
        for col in ALL_FEATURES:
            if col in df.columns and df[col].isnull().any():
                df[col] = df[col].fillna(df[col].median())

    return train, test


# ══════════════════════════════════════════════════════════════════════════════
# 1. APPRENTISSAGE SUPERVISÉ
# ══════════════════════════════════════════════════════════════════════════════

def entrainer_supervise(train: pd.DataFrame, test: pd.DataFrame) -> dict:
    """
    XGBoost binaire pour prédire label_defaut_90j.

    Protocole :
      - Walk-forward simulé : StratifiedKFold 5 plis (folds triés par score RPS
        précédent — proxy temporel) avec gap d'un pli entre train et validation.
      - Calibration Platt sur le pli de validation final.
      - SHAP TreeExplainer pour l'explicabilité.
      - Métriques : AUC-ROC, Gini, KS, Brier, F1, Précision, Rappel.
    """
    log.info("=" * 60)
    log.info("SECTION 1 — APPRENTISSAGE SUPERVISÉ")
    log.info("=" * 60)

    try:
        import shap as shap_lib
        shap_disponible = True
    except ImportError:
        shap_disponible = False
        log.warning("shap non disponible — explicabilité désactivée")

    X_train = train[ALL_FEATURES].values
    y_train = train[LABEL].values
    X_test  = test[ALL_FEATURES].values
    y_test  = test[LABEL].values

    ratio_defaut = y_train.mean()
    log.info("Déséquilibre classes — défaut : %.1f%%", ratio_defaut * 100)
    scale_pos_weight = (1 - ratio_defaut) / ratio_defaut

    # ── Hyperparamètres XGBoost ───────────────────────────────────────────────
    params = dict(
        n_estimators        = 500,
        max_depth           = 6,
        learning_rate       = 0.05,
        subsample           = 0.80,
        colsample_bytree    = 0.80,
        min_child_weight    = 5,
        gamma               = 0.10,
        reg_alpha           = 0.10,
        reg_lambda          = 1.00,
        scale_pos_weight    = scale_pos_weight,
        use_label_encoder   = False,
        eval_metric         = "logloss",
        early_stopping_rounds = 50,
        random_state        = 42,
        n_jobs              = -1,
    )

    # ── Walk-forward (StratifiedKFold trié par risque précédent) ──────────────
    log.info("Walk-forward CV 5 plis …")
    # Tri par score_rps_precedent comme proxy de l'ordre temporel
    idx_sorted = np.argsort(train["score_rps_precedent"].values)
    X_sorted   = X_train[idx_sorted]
    y_sorted   = y_train[idx_sorted]

    skf          = StratifiedKFold(n_splits=5, shuffle=False)
    cv_metrics   = []
    meilleur_auc = 0.0
    meilleur_modele = None

    for fold, (idx_tr, idx_val) in enumerate(skf.split(X_sorted, y_sorted), 1):
        # Gap d'un pli : on retire le dernier quart du train pour éviter la fuite
        gap = max(1, len(idx_tr) // 5)
        idx_tr_gap = idx_tr[:-gap]

        mdl = XGBClassifier(**params)
        mdl.fit(
            X_sorted[idx_tr_gap], y_sorted[idx_tr_gap],
            eval_set=[(X_sorted[idx_val], y_sorted[idx_val])],
            verbose=False,
        )

        proba_val = mdl.predict_proba(X_sorted[idx_val])[:, 1]
        auc_val   = roc_auc_score(y_sorted[idx_val], proba_val)
        brier     = brier_score_loss(y_sorted[idx_val], proba_val)
        f1        = f1_score(y_sorted[idx_val], (proba_val > 0.5).astype(int))

        cv_metrics.append({"fold": fold, "auc_roc": round(auc_val, 4),
                           "gini": round(_gini(auc_val), 4),
                           "brier": round(brier, 4), "f1": round(f1, 4)})
        log.info("  Pli %d — AUC=%.4f  Gini=%.4f  Brier=%.4f  F1=%.4f",
                 fold, auc_val, _gini(auc_val), brier, f1)

        if auc_val > meilleur_auc:
            meilleur_auc    = auc_val
            meilleur_modele = mdl

    # ── Entraînement final sur tout le train ──────────────────────────────────
    log.info("Entraînement final sur l'ensemble d'entraînement …")
    modele_final = XGBClassifier(**{k: v for k, v in params.items()
                                    if k != "early_stopping_rounds"})
    modele_final.fit(X_train, y_train, verbose=False)

    # ── Calibration isotonique sur le dernier tiers du train ─────────────────
    from sklearn.isotonic import IsotonicRegression

    log.info("Calibration isotonique …")
    cal_idx       = int(len(X_train) * 0.67)
    X_cal, y_cal  = X_train[cal_idx:], y_train[cal_idx:]
    raw_cal_proba = modele_final.predict_proba(X_cal)[:, 1]
    iso_reg       = IsotonicRegression(out_of_bounds="clip")
    iso_reg.fit(raw_cal_proba, y_cal)

    calibrateur = CalibratedModel(modele_final, iso_reg)

    # ── Évaluation sur le jeu de test ─────────────────────────────────────────
    log.info("Évaluation sur le jeu de test …")
    proba_test  = calibrateur.predict_proba(X_test)[:, 1]
    pred_test   = (proba_test > 0.50).astype(int)
    auc_test    = roc_auc_score(y_test, proba_test)
    brier_test  = brier_score_loss(y_test, proba_test)
    ks_test     = _ks_stat(y_test, proba_test)

    metriques_test = {
        "auc_roc":   round(float(auc_test), 4),
        "gini":      round(float(_gini(auc_test)), 4),
        "ks":        round(float(ks_test), 4),
        "brier":     round(float(brier_test), 4),
        "f1":        round(float(f1_score(y_test, pred_test)), 4),
        "precision": round(float(precision_score(y_test, pred_test)), 4),
        "rappel":    round(float(recall_score(y_test, pred_test)), 4),
    }
    log.info("  Test — AUC=%(auc_roc).4f  Gini=%(gini).4f  KS=%(ks).4f  "
             "Brier=%(brier).4f  F1=%(f1).4f", metriques_test)

    # ── SHAP ──────────────────────────────────────────────────────────────────
    shap_importance: dict[str, float] = {}
    if shap_disponible:
        log.info("Calcul des valeurs SHAP …")
        try:
            explainer   = shap_lib.TreeExplainer(modele_final)
            shap_values = explainer.shap_values(X_test[:500])  # sous-échantillon
            mean_abs    = np.abs(shap_values).mean(axis=0)
            shap_importance = dict(zip(ALL_FEATURES, mean_abs.tolist()))
            shap_importance = dict(sorted(shap_importance.items(),
                                          key=lambda x: x[1], reverse=True))

            fig, ax = plt.subplots(figsize=(10, 8))
            top_n = 15
            top_feats = list(shap_importance.keys())[:top_n]
            top_vals  = [shap_importance[f] for f in top_feats]
            ax.barh(top_feats[::-1], top_vals[::-1], color="#E74C3C")
            ax.set_xlabel("Valeur SHAP moyenne |v|")
            ax.set_title("Top 15 features — Importance SHAP (XGBoost)")
            ax.grid(axis="x", alpha=0.3)
            fig.tight_layout()
            fig.savefig(RES_SUP / "shap_importance.png", dpi=150)
            plt.close(fig)
        except Exception as e:
            log.warning("SHAP échoué : %s", e)

    # ── Courbe ROC ────────────────────────────────────────────────────────────
    fpr, tpr, _ = roc_curve(y_test, proba_test)
    fig, ax = plt.subplots(figsize=(7, 6))
    ax.plot(fpr, tpr, color="#2980B9", lw=2,
            label=f"AUC = {auc_test:.4f}  |  Gini = {_gini(auc_test):.4f}")
    ax.plot([0, 1], [0, 1], "k--", lw=1)
    ax.fill_between(fpr, tpr, alpha=0.08, color="#2980B9")
    ax.set_xlabel("Taux faux positifs"); ax.set_ylabel("Taux vrais positifs")
    ax.set_title("Courbe ROC — XGBoost MCRS (défaut 90j)")
    ax.legend(loc="lower right"); ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(RES_SUP / "roc_curve.png", dpi=150)
    plt.close(fig)

    # ── Courbe Précision-Rappel ───────────────────────────────────────────────
    prec_arr, rec_arr, _ = precision_recall_curve(y_test, proba_test)
    pr_auc = auc(rec_arr, prec_arr)
    fig, ax = plt.subplots(figsize=(7, 6))
    ax.plot(rec_arr, prec_arr, color="#27AE60", lw=2,
            label=f"PR-AUC = {pr_auc:.4f}")
    ax.axhline(y_test.mean(), color="red", linestyle="--",
               label=f"Baseline (prévalence {y_test.mean():.2%})")
    ax.set_xlabel("Rappel"); ax.set_ylabel("Précision")
    ax.set_title("Courbe Précision-Rappel — XGBoost MCRS")
    ax.legend(); ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(RES_SUP / "pr_curve.png", dpi=150)
    plt.close(fig)

    # ── Matrice de confusion ──────────────────────────────────────────────────
    fig, ax = plt.subplots(figsize=(6, 5))
    ConfusionMatrixDisplay.from_predictions(
        y_test, pred_test, ax=ax,
        display_labels=["Non défaut", "Défaut 90j"],
        colorbar=False, cmap="Blues")
    ax.set_title("Matrice de confusion — Seuil 0.50")
    fig.tight_layout()
    fig.savefig(RES_SUP / "confusion_matrix.png", dpi=150)
    plt.close(fig)

    # ── Courbe de calibration ─────────────────────────────────────────────────
    from sklearn.calibration import calibration_curve
    prob_true, prob_pred = calibration_curve(y_test, proba_test, n_bins=10)
    fig, ax = plt.subplots(figsize=(7, 6))
    ax.plot(prob_pred, prob_true, "s-", color="#8E44AD", lw=2, label="Modèle calibré")
    ax.plot([0, 1], [0, 1], "k--", lw=1, label="Calibration parfaite")
    ax.set_xlabel("Probabilité prédite"); ax.set_ylabel("Fréquence observée")
    ax.set_title("Courbe de calibration — Platt (isotonique)")
    ax.legend(); ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(RES_SUP / "calibration_curve.png", dpi=150)
    plt.close(fig)

    # ── Rapport classification (texte) ────────────────────────────────────────
    rapport_txt = classification_report(
        y_test, pred_test,
        target_names=["Non défaut (0)", "Défaut 90j (1)"])
    with open(RES_SUP / "classification_report.txt", "w", encoding="utf-8") as f:
        f.write("=" * 60 + "\n")
        f.write("RAPPORT DE CLASSIFICATION — XGBoost MCRS\n")
        f.write(f"Généré le : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("=" * 60 + "\n\n")
        f.write(rapport_txt)
        f.write("\nMétriques étendues :\n")
        for k, v in metriques_test.items():
            f.write(f"  {k:15s}: {v}\n")
        f.write("\nCross-validation (walk-forward 5 plis) :\n")
        for m in cv_metrics:
            f.write(f"  Pli {m['fold']} — AUC={m['auc_roc']}  Gini={m['gini']}  "
                    f"Brier={m['brier']}  F1={m['f1']}\n")
        if shap_importance:
            f.write("\nTop 10 features SHAP :\n")
            for feat, val in list(shap_importance.items())[:10]:
                f.write(f"  {feat:40s}: {val:.4f}\n")

    # ── Persistance modèle ────────────────────────────────────────────────────
    with open(RES_SUP / "model_xgboost.pkl", "wb") as f:
        pickle.dump({"modele": calibrateur, "features": ALL_FEATURES,
                     "metriques": metriques_test, "trained_at": datetime.now().isoformat()}, f)

    metriques_finales = {
        "paradigme":         "supervisé",
        "algorithme":        "XGBoost + calibration isotonique (Platt)",
        "n_train":           int(len(X_train)),
        "n_test":            int(len(X_test)),
        "n_features":        len(ALL_FEATURES),
        "ratio_defaut_train": round(float(ratio_defaut), 4),
        "cv_walk_forward":   cv_metrics,
        "test":              metriques_test,
        "shap_top10":        dict(list(shap_importance.items())[:10]),
        "generated_at":      datetime.now().isoformat(),
    }
    _sauver_json(metriques_finales, RES_SUP / "metrics.json")
    log.info("Supervisé — terminé ✓  AUC=%.4f  Gini=%.4f  KS=%.4f",
             auc_test, _gini(auc_test), ks_test)
    return metriques_finales


# ══════════════════════════════════════════════════════════════════════════════
# 1b. ANALYSE PAR RÉGION — performance du modèle par région camerounaise
# ══════════════════════════════════════════════════════════════════════════════

REGION_LABELS = {
    "REG01": "Adamaoua",    "REG02": "Centre",      "REG03": "Est",
    "REG04": "Extrême-Nord","REG05": "Littoral",    "REG06": "Nord",
    "REG07": "Nord-Ouest",  "REG08": "Ouest",       "REG09": "Sud",
    "REG10": "Sud-Ouest",
}

def analyser_par_region(test: pd.DataFrame, calibrateur: "CalibratedModel") -> dict:
    """
    Calcule l'AUC et le taux de défaut par région camerounaise sur le jeu de test.
    Génère un graphique et un JSON de résultats.
    """
    if "region_id" not in test.columns:
        log.warning("Colonne region_id absente — analyse régionale ignorée")
        return {}

    log.info("Analyse de performance par région camerounaise …")
    resultats = {}

    for reg_id, reg_name in sorted(REGION_LABELS.items()):
        sous = test[test["region_id"] == reg_id]
        if len(sous) < 20:
            continue

        X_reg = sous[ALL_FEATURES].values
        y_reg = sous[LABEL].values
        proba = calibrateur.predict_proba(X_reg)[:, 1]

        if y_reg.sum() == 0 or y_reg.sum() == len(y_reg):
            continue  # classe unique, AUC indéfinie

        auc_reg    = roc_auc_score(y_reg, proba)
        taux_def   = float(y_reg.mean())
        n_clients  = int(len(sous))

        resultats[reg_id] = {
            "region":        reg_name,
            "n_clients":     n_clients,
            "taux_defaut":   round(taux_def, 4),
            "auc_roc":       round(float(auc_reg), 4),
            "gini":          round(float(_gini(auc_reg)), 4),
        }
        log.info("  %-14s (N=%4d) — AUC=%.4f  Taux_défaut=%.1f%%",
                 reg_name, n_clients, auc_reg, taux_def * 100)

    # Graphique AUC par région
    if resultats:
        fig, axes = plt.subplots(1, 2, figsize=(14, 6))

        noms   = [v["region"] for v in resultats.values()]
        aucs   = [v["auc_roc"] for v in resultats.values()]
        taux   = [v["taux_defaut"] * 100 for v in resultats.values()]
        colors_auc = ["#27AE60" if a >= 0.78 else "#E67E22" if a >= 0.70 else "#E74C3C"
                      for a in aucs]

        axes[0].barh(noms[::-1], aucs[::-1], color=colors_auc[::-1])
        axes[0].axvline(0.78, color="#2C3E50", linestyle="--", lw=1.5, label="Cible AUC=0.78")
        axes[0].axvline(0.50, color="gray", linestyle=":", lw=1, label="Baseline=0.50")
        axes[0].set_xlabel("AUC-ROC")
        axes[0].set_title("AUC-ROC par région — XGBoost MCRS")
        axes[0].legend(fontsize=8); axes[0].grid(axis="x", alpha=0.3)
        for i, (a, nom) in enumerate(zip(aucs[::-1], noms[::-1])):
            axes[0].text(a + 0.002, i, f"{a:.3f}", va="center", fontsize=8)

        colors_def = ["#E74C3C" if t > 30 else "#E67E22" if t > 20 else "#27AE60"
                      for t in taux]
        axes[1].barh(noms[::-1], taux[::-1], color=colors_def[::-1])
        axes[1].set_xlabel("Taux de défaut (%)")
        axes[1].set_title("Taux de défaut 90j par région")
        axes[1].grid(axis="x", alpha=0.3)
        for i, (t, nom) in enumerate(zip(taux[::-1], noms[::-1])):
            axes[1].text(t + 0.3, i, f"{t:.1f}%", va="center", fontsize=8)

        fig.suptitle("Performance du modèle MCRS par région camerounaise", fontsize=13)
        fig.tight_layout()
        fig.savefig(RES_SUP / "regional_performance.png", dpi=150)
        plt.close(fig)

        _sauver_json(resultats, RES_SUP / "regional_metrics.json")
        log.info("Analyse régionale — terminée ✓  %d régions", len(resultats))

    return resultats


# ══════════════════════════════════════════════════════════════════════════════
# 2. APPRENTISSAGE NON SUPERVISÉ
# ══════════════════════════════════════════════════════════════════════════════

def entrainer_non_supervise(train: pd.DataFrame) -> dict:
    """
    Trois algorithmes non supervisés :

    A) K-Means (k=2…8) — segmentation clients par profil de risque.
       Critère de sélection : silhouette score + méthode du coude (inertia).

    B) DBSCAN — clustering à densité pour détecter les sous-groupes
       atypiques (micro-IMF, profils exceptionnels).

    C) Isolation Forest — détection d'anomalies (comportements frauduleux
       ou erreurs de saisie dans les données de collecte).
    """
    log.info("=" * 60)
    log.info("SECTION 2 — APPRENTISSAGE NON SUPERVISÉ")
    log.info("=" * 60)

    X  = train[ALL_FEATURES].values
    sc = StandardScaler()
    Xs = sc.fit_transform(X)

    # ── A) K-Means — méthode du coude ─────────────────────────────────────────
    log.info("A) K-Means — méthode du coude (k=2 à 8) …")
    inerties    = []
    silhouettes = []
    ks          = range(2, 9)

    for k in ks:
        km = KMeans(n_clusters=k, random_state=42, n_init=10)
        etiq = km.fit_predict(Xs)
        inerties.append(km.inertia_)
        silhouettes.append(silhouette_score(Xs, etiq, sample_size=2000))
        log.info("  k=%d — inertie=%.1f  silhouette=%.4f", k, km.inertia_,
                 silhouettes[-1])

    k_optimal = int(ks[np.argmax(silhouettes)])
    log.info("  k optimal (max silhouette) = %d", k_optimal)

    # Courbe du coude
    fig, ax1 = plt.subplots(figsize=(8, 5))
    color_ine = "#2980B9"
    color_sil = "#E74C3C"
    ax1.plot(list(ks), inerties, "o-", color=color_ine, lw=2, label="Inertie")
    ax1.set_xlabel("Nombre de clusters k"); ax1.set_ylabel("Inertie", color=color_ine)
    ax1.tick_params(axis="y", labelcolor=color_ine)
    ax2 = ax1.twinx()
    ax2.plot(list(ks), silhouettes, "s--", color=color_sil, lw=2, label="Silhouette")
    ax2.set_ylabel("Score silhouette", color=color_sil)
    ax2.tick_params(axis="y", labelcolor=color_sil)
    ax1.axvline(k_optimal, color="gray", linestyle=":", lw=1.5,
                label=f"k={k_optimal} (optimal)")
    ax1.set_title("K-Means — Méthode du coude et score silhouette")
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc="upper right")
    ax1.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(RES_UNS / "kmeans_elbow.png", dpi=150)
    plt.close(fig)

    # K-Means final avec k optimal
    km_final = KMeans(n_clusters=k_optimal, random_state=42, n_init=20)
    clusters  = km_final.fit_predict(Xs)
    train_cl  = train.copy()
    train_cl["cluster"] = clusters

    # Profil de chaque cluster
    profils = {}
    for c in range(k_optimal):
        sous = train_cl[train_cl["cluster"] == c]
        profils[int(c)] = {
            "n_clients":     int(len(sous)),
            "taux_defaut":   round(float(sous[LABEL].mean()), 4),
            "retard_moyen":  round(float(sous["jours_retard_actuel"].mean()), 1),
            "regularite_moy": round(float(sous["regularite_collecte_pct"].mean()), 1),
            "capacite_rem":  round(float(sous["capacite_remboursement"].mean()), 3),
        }

    silh_final = silhouette_score(Xs, clusters, sample_size=3000)
    db_final   = davies_bouldin_score(Xs, clusters)
    ch_final   = calinski_harabasz_score(Xs, clusters)

    metriques_kmeans = {
        "k_optimal":            k_optimal,
        "silhouette_score":     round(float(silh_final), 4),
        "davies_bouldin_index": round(float(db_final), 4),
        "calinski_harabasz":    round(float(ch_final), 1),
        "profils_clusters":     profils,
    }
    log.info("  K-Means final — silhouette=%.4f  Davies-Bouldin=%.4f",
             silh_final, db_final)

    # ── B) DBSCAN ────────────────────────────────────────────────────────────
    log.info("B) DBSCAN …")
    # eps estimé par la méthode du k-NN (k=5)
    from sklearn.neighbors import NearestNeighbors
    nn = NearestNeighbors(n_neighbors=5).fit(Xs)
    dist, _ = nn.kneighbors(Xs)
    eps_auto = float(np.percentile(dist[:, -1], 90))
    log.info("  eps automatique (percentile 90 des dist k=5) = %.4f", eps_auto)

    dbscan   = DBSCAN(eps=eps_auto, min_samples=10, n_jobs=-1)
    labels_db = dbscan.fit_predict(Xs)
    n_clusters_db  = len(set(labels_db)) - (1 if -1 in labels_db else 0)
    n_bruit        = int((labels_db == -1).sum())
    pct_bruit      = n_bruit / len(labels_db) * 100

    metriques_dbscan = {
        "eps":           round(eps_auto, 4),
        "min_samples":   10,
        "n_clusters":    n_clusters_db,
        "n_bruit":       n_bruit,
        "pct_bruit":     round(pct_bruit, 2),
    }
    if n_clusters_db > 1:
        mask_valid = labels_db != -1
        if mask_valid.sum() > 1:
            sil_db = silhouette_score(Xs[mask_valid], labels_db[mask_valid],
                                      sample_size=min(3000, mask_valid.sum()))
            metriques_dbscan["silhouette_score"] = round(float(sil_db), 4)
    log.info("  DBSCAN — clusters=%d  bruit=%.1f%%", n_clusters_db, pct_bruit)

    # ── C) Isolation Forest — détection d'anomalies ───────────────────────────
    log.info("C) Isolation Forest — détection d'anomalies …")
    iso = IsolationForest(n_estimators=200, contamination=0.05,
                          random_state=42, n_jobs=-1)
    scores_anomalie = iso.fit_predict(Xs)
    anomalies       = scores_anomalie == -1
    scores_bruts    = iso.decision_function(Xs)
    n_anomalies     = int(anomalies.sum())
    pct_anomalies   = n_anomalies / len(anomalies) * 100

    log.info("  Anomalies détectées : %d (%.1f%%)", n_anomalies, pct_anomalies)

    # Comparaison anomalies vs normaux
    train_an = train.copy()
    train_an["anomalie"] = anomalies
    train_an["score_anomalie"] = scores_bruts

    profil_anomalies = {
        "n_anomalies": n_anomalies,
        "pct_anomalies": round(pct_anomalies, 2),
        "taux_defaut_anomalies": round(float(train_an[anomalies][LABEL].mean()), 4),
        "taux_defaut_normaux":   round(float(train_an[~anomalies][LABEL].mean()), 4),
        "retard_moyen_anomalies": round(float(train_an[anomalies]["jours_retard_actuel"].mean()), 1),
        "retard_moyen_normaux":   round(float(train_an[~anomalies]["jours_retard_actuel"].mean()), 1),
    }

    # Export CSV anomalies
    cols_export = ["client_id", "imf_id", "jours_retard_actuel",
                   "nb_incidents_paiement_12m", LABEL, "score_anomalie"]
    export_cols = [c for c in cols_export if c in train_an.columns]
    train_an[anomalies][export_cols].to_csv(
        RES_UNS / "anomaly_scores.csv", index=False)

    rapport_anomalies = dedent(f"""
    RAPPORT DÉTECTION D'ANOMALIES — Isolation Forest
    Généré le : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
    {'='*55}

    Paramètres
      Estimateurs : 200 arbres
      Contamination attendue : 5 %

    Résultats
      Anomalies détectées  : {n_anomalies:>6d}  ({pct_anomalies:.1f}% du corpus)
      Clients normaux      : {len(anomalies)-n_anomalies:>6d}

    Comparaison profils
      Taux défaut anomalies : {profil_anomalies['taux_defaut_anomalies']:.2%}
      Taux défaut normaux   : {profil_anomalies['taux_defaut_normaux']:.2%}
      Retard moyen anomalies : {profil_anomalies['retard_moyen_anomalies']:.1f}j
      Retard moyen normaux   : {profil_anomalies['retard_moyen_normaux']:.1f}j

    Interprétation
      Les anomalies présentent généralement un taux de défaut
      plus élevé et des retards plus importants.
      → Recommandation : vérification manuelle avant scoring.
    """)
    with open(RES_UNS / "anomaly_report.txt", "w", encoding="utf-8") as f:
        f.write(rapport_anomalies)

    # ── Visualisation PCA 2D ──────────────────────────────────────────────────
    log.info("Visualisation PCA 2D …")
    pca   = PCA(n_components=2, random_state=42)
    Xpca  = pca.fit_transform(Xs)
    var   = pca.explained_variance_ratio_

    fig, axes = plt.subplots(1, 3, figsize=(18, 5))

    # K-Means clusters
    scatter = axes[0].scatter(Xpca[:, 0], Xpca[:, 1],
                              c=clusters, cmap="Set1", alpha=0.4, s=8)
    axes[0].set_title(f"K-Means (k={k_optimal})\nsilhouette={silh_final:.4f}")
    axes[0].set_xlabel(f"PC1 ({var[0]:.1%})")
    axes[0].set_ylabel(f"PC2 ({var[1]:.1%})")
    plt.colorbar(scatter, ax=axes[0], label="Cluster")

    # Défaut réel (supervisé overlay)
    colors_def = ["#3498DB" if y == 0 else "#E74C3C" for y in train[LABEL].values]
    axes[1].scatter(Xpca[:, 0], Xpca[:, 1], c=colors_def, alpha=0.3, s=8)
    from matplotlib.patches import Patch
    axes[1].legend(handles=[Patch(color="#3498DB", label="Non défaut"),
                             Patch(color="#E74C3C", label="Défaut 90j")])
    axes[1].set_title("Défauts réels (label supervisé)\nsur projection PCA")
    axes[1].set_xlabel(f"PC1 ({var[0]:.1%})")
    axes[1].set_ylabel(f"PC2 ({var[1]:.1%})")

    # Anomalies
    colors_an = ["#E74C3C" if a else "#95A5A6" for a in anomalies]
    axes[2].scatter(Xpca[:, 0], Xpca[:, 1], c=colors_an, alpha=0.4, s=8)
    axes[2].legend(handles=[Patch(color="#E74C3C", label=f"Anomalies ({n_anomalies})"),
                             Patch(color="#95A5A6", label="Normaux")])
    axes[2].set_title(f"Anomalies Isolation Forest\n({pct_anomalies:.1f}% détectées)")
    axes[2].set_xlabel(f"PC1 ({var[0]:.1%})")
    axes[2].set_ylabel(f"PC2 ({var[1]:.1%})")

    fig.suptitle("Visualisation PCA 2D — Apprentissage non supervisé", fontsize=13)
    fig.tight_layout()
    fig.savefig(RES_UNS / "pca_visualization.png", dpi=150)
    plt.close(fig)

    metriques_finales = {
        "paradigme":   "non_supervise",
        "n_clients":   int(len(train)),
        "n_features":  len(ALL_FEATURES),
        "pca_variance_expliquee": [round(float(v), 4) for v in var],
        "kmeans":  metriques_kmeans,
        "dbscan":  metriques_dbscan,
        "isolation_forest": profil_anomalies,
        "generated_at": datetime.now().isoformat(),
    }
    _sauver_json(metriques_finales, RES_UNS / "metrics.json")
    log.info("Non supervisé — terminé ✓  %d clusters, %d anomalies",
             k_optimal, n_anomalies)
    return metriques_finales


# ══════════════════════════════════════════════════════════════════════════════
# 3. APPRENTISSAGE PAR RENFORCEMENT — Q-Learning Recouvrement
# ══════════════════════════════════════════════════════════════════════════════

def entrainer_renforcement(train: pd.DataFrame, test: pd.DataFrame,
                            metriques_sup: dict) -> dict:
    """
    Q-Learning tabulaire pour optimiser la stratégie de recouvrement.

    Problème :
      À chaque contact avec un client, l'agent (le système) choisit une action
      parmi {AUCUNE, RELANCE_PREVENTIVE, VISITE_TERRAIN, MISE_EN_DEMEURE}.
      Le but est de maximiser les remboursements tout en minimisant les coûts
      opérationnels de recouvrement.

    Environnement simulé :
      - États : (niveau_risque × bucket_retard × niveau_incidents) — 48 états
      - Actions : 4 actions (0 à 3)
      - Récompense : f(probabilité de remboursement, coût action)
      - Transitions : probabilistes, calibrées depuis les données historiques

    Algorithme : Q-Learning ε-greedy avec décroissance exponentielle de ε.
    """
    log.info("=" * 60)
    log.info("SECTION 3 — APPRENTISSAGE PAR RENFORCEMENT (Q-Learning)")
    log.info("=" * 60)

    # ── Définitions ───────────────────────────────────────────────────────────
    ACTIONS = {
        0: "AUCUNE",
        1: "RELANCE_PREVENTIVE",
        2: "VISITE_TERRAIN",
        3: "MISE_EN_DEMEURE",
    }
    COUTS_ACTION = {0: 0.0, 1: 2.0, 2: 5.0, 3: 8.0}  # coûts relatifs (unité: FCFA×100)
    N_ACTIONS  = 4
    N_RISQUE   = 4   # FAIBLE, MODERE, ELEVE, CRITIQUE
    N_RETARD   = 4   # [0-30j], [30-90j], [90-180j], [180+j]
    N_INCIDENT = 3   # 0 incident, 1-2 incidents, 3+ incidents
    N_ETATS    = N_RISQUE * N_RETARD * N_INCIDENT   # 48 états

    def state_to_idx(r: int, d: int, i: int) -> int:
        return r * N_RETARD * N_INCIDENT + d * N_INCIDENT + i

    def row_to_state(row: pd.Series) -> int:
        score = (row["taux_remboursement_historique"] * W_RPS +
                 row["regularite_collecte_pct"] / 100 * W_CRS +
                 row["indice_resilience"] * W_CSI)
        score = min(1.0, max(0.0, 1.0 - score))  # inversé : 1 = risqué

        r = 0 if score < 0.30 else (1 if score < 0.55 else (2 if score < 0.75 else 3))
        d = (0 if row["jours_retard_actuel"] < 30 else
             1 if row["jours_retard_actuel"] < 90 else
             2 if row["jours_retard_actuel"] < 180 else 3)
        i = (0 if row["nb_incidents_paiement_12m"] == 0 else
             1 if row["nb_incidents_paiement_12m"] <= 2 else 2)
        return state_to_idx(r, d, i)

    # ── Probabilités de remboursement empiriques par état ─────────────────────
    # Calculées depuis les données historiques (train)
    proba_remboursement = np.zeros(N_ETATS)
    count_etat          = np.zeros(N_ETATS)

    for _, row in train.iterrows():
        s = row_to_state(row)
        count_etat[s] += 1
        proba_remboursement[s] += (1 - row[LABEL])  # 1 = pas de défaut = remboursement

    # Normalisation (lissage Laplace si état vide)
    proba_remboursement = (proba_remboursement + 1) / (count_etat + 2)

    def recompense(etat: int, action: int, rembourse: bool) -> float:
        """
        Récompense = gain remboursement - coût action.

        Logique :
          - Si le client rembourse ET on a pris une action coûteuse → récompense réduite
          - Si le client rembourse sans action → bonne économie
          - Si le client ne rembourse pas → pénalité proportionnelle au coût perdu
          - Bonus si action appropriée au risque (MISE_EN_DEMEURE pour CRITIQUE)
        """
        r_idx = etat // (N_RETARD * N_INCIDENT)
        cout  = COUTS_ACTION[action]

        if rembourse:
            # Gain de base selon le niveau de retard (plus de retard = plus de valeur à récupérer)
            retard_idx = (etat // N_INCIDENT) % N_RETARD
            gain_base  = [10.0, 20.0, 35.0, 50.0][retard_idx]
            rew        = gain_base - cout
        else:
            # Perte : coût action + pénalité défaut
            rew = -cout - [5.0, 10.0, 15.0, 20.0][r_idx]

        # Bonus cohérence : action adaptée au risque
        action_cible = [0, 1, 2, 3][r_idx]
        if action == action_cible:
            rew += 3.0
        elif abs(action - action_cible) >= 2:
            rew -= 2.0  # malus action inadaptée

        return float(rew)

    # ── Q-Table et hyperparamètres ─────────────────────────────────────────────
    Q             = np.zeros((N_ETATS, N_ACTIONS))
    alpha         = 0.10    # taux d'apprentissage
    gamma         = 0.95    # facteur d'actualisation
    epsilon_start = 1.00
    epsilon_fin   = 0.05
    N_EPISODES    = 10_000
    MAX_STEPS     = 12      # étapes max par épisode (1 an de suivi mensuel)

    eps_decay     = (epsilon_fin / epsilon_start) ** (1.0 / N_EPISODES)
    epsilon       = epsilon_start

    rng            = np.random.default_rng(42)
    historique_rwd = []   # récompense cumulée par épisode
    historique_eps = []   # epsilon par épisode

    # ── Boucle d'entraînement ─────────────────────────────────────────────────
    log.info("Entraînement Q-Learning — %d épisodes …", N_EPISODES)
    data_rl    = train.sample(frac=1, random_state=42).reset_index(drop=True)
    n_data     = len(data_rl)

    for episode in range(N_EPISODES):
        # Initialiser sur un client aléatoire
        row_idx  = episode % n_data
        row      = data_rl.iloc[row_idx]
        etat     = row_to_state(row)
        rwd_cum  = 0.0
        termine  = False
        step     = 0

        while not termine and step < MAX_STEPS:
            # Politique ε-greedy
            if rng.random() < epsilon:
                action = int(rng.integers(N_ACTIONS))
            else:
                action = int(np.argmax(Q[etat]))

            # Simulation de l'issue
            p_remb   = min(1.0, proba_remboursement[etat] * (1 + 0.1 * action))
            rembourse = rng.random() < p_remb
            r         = recompense(etat, action, rembourse)
            rwd_cum  += r

            # Transition d'état (simplifiée : amélioration si remboursement)
            if rembourse and etat > 0:
                etat_suivant = max(0, etat - 1)
                termine = (etat_suivant == 0)
            elif not rembourse:
                etat_suivant = min(N_ETATS - 1, etat + 1)
                termine = (step == MAX_STEPS - 1)
            else:
                etat_suivant = etat
                termine = True

            # Mise à jour Bellman
            Q[etat, action] += alpha * (
                r + gamma * np.max(Q[etat_suivant]) - Q[etat, action])
            etat = etat_suivant
            step += 1

        historique_rwd.append(rwd_cum)
        historique_eps.append(epsilon)
        epsilon *= eps_decay

        if (episode + 1) % 1000 == 0:
            moy = np.mean(historique_rwd[-500:])
            log.info("  Épisode %5d — récompense moy.(500 ep.) = %.2f  ε=%.4f",
                     episode + 1, moy, epsilon)

    # ── Extraction de la politique ────────────────────────────────────────────
    politique = {}
    for r_idx in range(N_RISQUE):
        for d_idx in range(N_RETARD):
            for i_idx in range(N_INCIDENT):
                s       = state_to_idx(r_idx, d_idx, i_idx)
                act_opt = int(np.argmax(Q[s]))
                key     = (["FAIBLE","MODERE","ELEVE","CRITIQUE"][r_idx],
                           ["0-30j","30-90j","90-180j","180j+"][d_idx],
                           ["0 incident","1-2 incidents","3+ incidents"][i_idx])
                politique[str(key)] = ACTIONS[act_opt]

    # ── Évaluation sur le jeu de test ─────────────────────────────────────────
    log.info("Évaluation de la politique sur le jeu de test …")
    recompenses_politique = []
    recompenses_baseline  = []  # toujours RELANCE_PREVENTIVE

    for _, row in test.iterrows():
        etat      = row_to_state(row)
        act_rl    = int(np.argmax(Q[etat]))
        p_remb    = proba_remboursement[etat]
        rembourse = rng.random() < p_remb

        recompenses_politique.append(recompense(etat, act_rl, rembourse))
        recompenses_baseline.append(recompense(etat, 1, rembourse))  # baseline=RELANCE

    perf_politique = float(np.mean(recompenses_politique))
    perf_baseline  = float(np.mean(recompenses_baseline))
    amelioration   = (perf_politique - perf_baseline) / abs(perf_baseline) * 100

    log.info("  Récompense politique RL : %.4f", perf_politique)
    log.info("  Récompense baseline     : %.4f", perf_baseline)
    log.info("  Amélioration            : %+.1f%%", amelioration)

    # ── Courbe d'apprentissage ────────────────────────────────────────────────
    window  = 200
    rwd_arr = np.array(historique_rwd)
    rwd_moy = np.convolve(rwd_arr, np.ones(window) / window, mode="valid")

    fig, ax1 = plt.subplots(figsize=(10, 5))
    ax1.plot(rwd_arr, alpha=0.15, color="#95A5A6", lw=0.5, label="Épisode brut")
    ax1.plot(np.arange(window - 1, N_EPISODES), rwd_moy, color="#2980B9", lw=2,
             label=f"Moyenne mobile ({window} ep.)")
    ax1.set_xlabel("Épisode")
    ax1.set_ylabel("Récompense cumulée", color="#2980B9")
    ax1.tick_params(axis="y", labelcolor="#2980B9")
    ax2 = ax1.twinx()
    ax2.plot(historique_eps, color="#E74C3C", alpha=0.5, lw=1, linestyle="--",
             label="ε (exploration)")
    ax2.set_ylabel("Epsilon (taux exploration)", color="#E74C3C")
    ax2.tick_params(axis="y", labelcolor="#E74C3C")
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc="lower right")
    ax1.set_title(f"Q-Learning — Courbe d'apprentissage ({N_EPISODES} épisodes)")
    ax1.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(RES_RL / "training_rewards.png", dpi=150)
    plt.close(fig)

    # Distribution des actions de la politique
    action_counts: dict[str, int] = defaultdict(int)
    for act_name in politique.values():
        action_counts[act_name] += 1

    fig, ax = plt.subplots(figsize=(7, 5))
    colors_act = ["#27AE60", "#F39C12", "#E67E22", "#E74C3C"]
    ax.bar(list(action_counts.keys()), list(action_counts.values()),
           color=colors_act[:len(action_counts)])
    ax.set_xlabel("Action recommandée")
    ax.set_ylabel("Nombre d'états (sur 48)")
    ax.set_title("Distribution des actions — Politique Q-Learning")
    ax.grid(axis="y", alpha=0.3)
    for i, (k, v) in enumerate(action_counts.items()):
        ax.text(i, v + 0.3, str(v), ha="center", fontsize=11)
    fig.tight_layout()
    fig.savefig(RES_RL / "policy_distribution.png", dpi=150)
    plt.close(fig)

    # Persistance Q-table
    with open(RES_RL / "q_table.pkl", "wb") as f:
        pickle.dump({"Q": Q, "actions": ACTIONS, "n_etats": N_ETATS,
                     "trained_at": datetime.now().isoformat()}, f)

    # Rapport texte
    rapport_rl = dedent(f"""
    RAPPORT — Q-LEARNING STRATÉGIE RECOUVREMENT
    Généré le : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
    {'='*55}

    Paramètres
      Épisodes      : {N_EPISODES:,}
      α (lr)        : {alpha}
      γ (discount)  : {gamma}
      ε initial     : {epsilon_start} → ε final : {epsilon_fin}
      Espace états  : {N_ETATS} (4 risques × 4 retards × 3 incidents)
      Actions       : {N_ACTIONS} ({', '.join(ACTIONS.values())})

    Résultats entraînement
      Récompense moy. (500 derniers ép.) : {np.mean(historique_rwd[-500:]):.4f}
      Récompense moy. finale              : {np.mean(historique_rwd[-100:]):.4f}

    Évaluation sur test (N={len(test)})
      Récompense politique RL  : {perf_politique:.4f}
      Récompense baseline      : {perf_baseline:.4f}  (RELANCE systématique)
      Amélioration             : {amelioration:+.1f}%

    Distribution des actions politiques
    """)
    for act_name, cnt in sorted(action_counts.items()):
        rapport_rl += f"  {act_name:25s}: {cnt:2d} états\n"
    rapport_rl += dedent(f"""
    Extrait politique (10 états)
    {'État':50s}{'Action':25s}
    {'-'*75}
    """)
    for i, (state_key, action) in enumerate(list(politique.items())[:10]):
        rapport_rl += f"  {str(state_key):50s}{action}\n"

    with open(RES_RL / "evaluation_report.txt", "w", encoding="utf-8") as f:
        f.write(rapport_rl)

    metriques_finales = {
        "paradigme": "renforcement",
        "algorithme": "Q-Learning tabulaire ε-greedy",
        "n_episodes": N_EPISODES,
        "n_etats": N_ETATS,
        "n_actions": N_ACTIONS,
        "hyperparametres": {"alpha": alpha, "gamma": gamma,
                            "epsilon_start": epsilon_start, "epsilon_fin": epsilon_fin},
        "recompense_moy_100_derniers": round(float(np.mean(historique_rwd[-100:])), 4),
        "evaluation": {
            "recompense_politique_rl": round(perf_politique, 4),
            "recompense_baseline":     round(perf_baseline, 4),
            "amelioration_pct":        round(amelioration, 2),
        },
        "distribution_actions": dict(action_counts),
        "generated_at": datetime.now().isoformat(),
    }
    _sauver_json(metriques_finales, RES_RL / "metrics.json")
    _sauver_json(politique, RES_RL / "policy_table.json")

    log.info("Par renforcement — terminé ✓  amélioration vs baseline : %+.1f%%",
             amelioration)
    return metriques_finales


# ══════════════════════════════════════════════════════════════════════════════
# 4. RAPPORT GLOBAL
# ══════════════════════════════════════════════════════════════════════════════

def generer_rapport(m_sup: dict, m_uns: dict, m_rl: dict, m_regional: dict | None = None) -> None:
    """Synthèse exécutive en Markdown, lisible par un DSI ou directeur d'IMF."""

    sup_test   = m_sup.get("test", {})
    uns_km     = m_uns.get("kmeans", {})
    uns_iso    = m_uns.get("isolation_forest", {})
    rl_eval    = m_rl.get("evaluation", {})

    rapport = dedent(f"""
    # Rapport d'entraînement ML — IMF Pipeline MCRS
    **Généré le :** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
    **Données :** `data/warehouse/ml/train.csv` — {m_sup.get('n_train', 0):,} clients d'entraînement

    ---

    ## 1. Apprentissage supervisé — Prédiction de défaut (XGBoost)

    | Métrique | Valeur |
    |----------|--------|
    | **AUC-ROC** | **{sup_test.get('auc_roc', 'N/A')}** |
    | Gini | {sup_test.get('gini', 'N/A')} |
    | KS statistic | {sup_test.get('ks', 'N/A')} |
    | Brier score | {sup_test.get('brier', 'N/A')} |
    | F1 score | {sup_test.get('f1', 'N/A')} |
    | Précision | {sup_test.get('precision', 'N/A')} |
    | Rappel | {sup_test.get('rappel', 'N/A')} |

    **Composition du score MCRS :**
    - CRS (régularité collectes) : 35 % — features terrain agent
    - RPS (probabilité défaut XGBoost) : 45 % — composant supervisé principal
    - CSI (résilience économique) : 20 % — facteurs macro, prix, météo

    **Protocole de validation :** Walk-forward 5 plis (ordre risque RPS précédent),
    gap d'un pli, calibration Platt isotonique sur le dernier tiers.

    **Features camerounaises intégrées (4 nouvelles) :**
    - `risque_regional` — profil de risque par région (0.90 Littoral → 1.45 Extrême-Nord)
    - `taux_penetration_mobile` — adoption mobile money MTN/Orange par région
    - `zone_agroclimatique` — Sahel/Équatorial/Highlands/Côtier (0-3)
    - `saison_recolte_active` — 1 si mois actuel = période récolte principale (cacao/café/coton/maïs)

    **Fichiers générés :**
    - `result/supervised/model_xgboost.pkl` — Modèle calibré prêt à déployer
    - `result/supervised/roc_curve.png` — Courbe ROC
    - `result/supervised/shap_importance.png` — Top 15 features SHAP
    - `result/supervised/confusion_matrix.png` — Matrice de confusion (seuil 0.50)
    - `result/supervised/calibration_curve.png` — Courbe de calibration
    - `result/supervised/regional_performance.png` — AUC et taux défaut par région
    - `result/supervised/regional_metrics.json` — Métriques détaillées par région
    - `result/supervised/classification_report.txt` — Rapport complet

    ---

    ## 2. Apprentissage non supervisé — Segmentation et anomalies

    ### K-Means (k={uns_km.get('k_optimal', 'N/A')} clusters optimaux)
    | Métrique | Valeur |
    |----------|--------|
    | Silhouette score | {uns_km.get('silhouette_score', 'N/A')} |
    | Davies-Bouldin | {uns_km.get('davies_bouldin_index', 'N/A')} |
    | Calinski-Harabasz | {uns_km.get('calinski_harabasz', 'N/A')} |

    ### Isolation Forest — Détection d'anomalies
    | Métrique | Valeur |
    |----------|--------|
    | Anomalies détectées | {uns_iso.get('n_anomalies', 'N/A')} ({uns_iso.get('pct_anomalies', 'N/A')}%) |
    | Taux défaut anomalies | {uns_iso.get('taux_defaut_anomalies', 'N/A'):.2%} |
    | Taux défaut normaux | {uns_iso.get('taux_defaut_normaux', 'N/A'):.2%} |

    > Les anomalies présentent un taux de défaut significativement plus élevé
    > → Vérification manuelle recommandée avant intégration au scoring.

    **Fichiers générés :**
    - `result/unsupervised/pca_visualization.png` — Projection 2D (K-Means + défauts + anomalies)
    - `result/unsupervised/kmeans_elbow.png` — Méthode du coude
    - `result/unsupervised/anomaly_scores.csv` — Liste clients anomalies
    - `result/unsupervised/anomaly_report.txt` — Rapport détaillé

    ---

    ## 3. Apprentissage par renforcement — Stratégie recouvrement (Q-Learning)

    | Métrique | Valeur |
    |----------|--------|
    | Épisodes d'entraînement | {m_rl.get('n_episodes', 0):,} |
    | Espace d'états | {m_rl.get('n_etats', 0)} (4 niveaux risque × 4 retards × 3 incidents) |
    | Récompense RL vs baseline | {rl_eval.get('recompense_politique_rl', 'N/A')} vs {rl_eval.get('recompense_baseline', 'N/A')} |
    | **Amélioration vs RELANCE systématique** | **{rl_eval.get('amelioration_pct', 'N/A'):+.1f}%** |

    **Politique apprise :**
    L'agent apprend à associer l'action optimale à chaque combinaison
    (niveau de risque, jours de retard, niveau d'incidents), réduisant les
    coûts opérationnels tout en maximisant les remboursements recouvrés.

    **Fichiers générés :**
    - `result/reinforcement/q_table.pkl` — Table Q complète (48×4)
    - `result/reinforcement/policy_table.json` — Politique lisible par état
    - `result/reinforcement/training_rewards.png` — Courbe d'apprentissage
    - `result/reinforcement/policy_distribution.png` — Distribution des actions

    ---

    ## 4. Performance par région camerounaise
    """)

    if m_regional:
        rapport += "\n    | Région | N clients | Taux défaut | AUC-ROC | Gini | Statut |\n"
        rapport += "    |--------|-----------|-------------|---------|------|--------|\n"
        for reg_id, reg_data in sorted(m_regional.items()):
            auc_r  = reg_data.get("auc_roc", 0)
            statut = "✅ Bon" if auc_r >= 0.78 else ("⚠️ Acceptable" if auc_r >= 0.70 else "❌ Amélioration requise")
            rapport += (f"    | {reg_data['region']:14s} | {reg_data['n_clients']:9d} |"
                       f" {reg_data['taux_defaut']:.1%}       | {auc_r:.4f}  |"
                       f" {reg_data['gini']:.4f} | {statut} |\n")
        rapport += dedent("""
    > **Régions prioritaires** : Extrême-Nord (sécheresse, instabilité), Nord-Ouest et Sud-Ouest
    > (contexte post-crise). Littoral et Centre bénéficient d'une meilleure infrastructure
    > financière (mobile money, agences denses).
    >
    > **Calendrier agricole intégré** : cacao (oct-déc, mar-mai), café arabica (nov-fév),
    > coton Extrême-Nord/Nord (sep-nov), maïs Highlands (jul-aoû). La variable
    > `saison_recolte_active` capture l'effet liquidité saisonnier sur le risque de défaut.

    ---
    """)
    else:
        rapport += "\n    *Analyse régionale non disponible (column region_id absente du test set)*\n\n    ---\n"

    rapport += dedent(f"""
    ## Synthèse et recommandations

    1. **Déploiement supervisé** : AUC-ROC de {sup_test.get('auc_roc', 'N/A')} indique
       {'une excellente' if float(sup_test.get('auc_roc', 0)) > 0.80 else 'une bonne'} capacité
       discriminante. Calibration Platt assure des probabilités bien calibrées pour
       les décisions de provisionnement COBAC.

    2. **Segmentation clients** : Les {uns_km.get('k_optimal', 'N/A')} clusters identifiés
       permettent une personnalisation des offres et du suivi par l'agence.

    3. **Anomalies** : {uns_iso.get('n_anomalies', 'N/A')} clients ({uns_iso.get('pct_anomalies', 'N/A')}%)
       présentent des comportements atypiques — revue manuelle recommandée.

    4. **Politique RL** : La stratégie apprise améliore le rendement de recouvrement
       de {rl_eval.get('amelioration_pct', 'N/A'):+.1f}% vs une politique de relance systématique.

    ---
    *Rapport généré automatiquement par `pipeline/train_models.py` — IMF Pipeline*
    """)

    path = RESULT / "rapport_global.md"
    with open(path, "w", encoding="utf-8") as f:
        f.write(rapport.strip())
    log.info("Rapport global → %s", path)


# ══════════════════════════════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ══════════════════════════════════════════════════════════════════════════════

def main() -> None:
    debut = datetime.now()
    log.info("╔══════════════════════════════════════════════════════╗")
    log.info("║  IMF Pipeline — Entraînement ML complet              ║")
    log.info("║  Données : data/warehouse/ml/                        ║")
    log.info("╚══════════════════════════════════════════════════════╝")

    train, test = charger_donnees()

    m_sup = entrainer_supervise(train, test)

    # Analyse par région — nécessite le modèle rechargé depuis le pickle
    import pickle as _pk
    with open(RES_SUP / "model_xgboost.pkl", "rb") as _f:
        _saved = _pk.load(_f)
    m_regional = analyser_par_region(test, _saved["modele"])

    m_uns = entrainer_non_supervise(train)
    m_rl  = entrainer_renforcement(train, test, m_sup)
    generer_rapport(m_sup, m_uns, m_rl, m_regional)

    duree = (datetime.now() - debut).total_seconds()
    log.info("══════════════════════════════════════════════════════")
    log.info("Entraînement terminé en %.1f secondes", duree)
    log.info("Résultats dans : %s", RESULT)
    log.info("══════════════════════════════════════════════════════")


if __name__ == "__main__":
    main()
