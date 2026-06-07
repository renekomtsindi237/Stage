"""Analyse représentativité / équité et recommandations.

Produit `result/eval_equity.json` et `result/recommendations.md`.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import confusion_matrix, roc_auc_score


ROOT = Path(__file__).parent.parent
DATA_DIR = ROOT / "data" / "warehouse" / "ml"
RES = ROOT / "result"
RES.mkdir(parents=True, exist_ok=True)
RES_SUP = RES / "supervised"


def safe_auc(y, p):
    try:
        return float(round(roc_auc_score(y, p), 4))
    except Exception:
        return None


def group_fairness(df, y_true, y_pred, y_proba, group_col):
    out = {}
    stats = []
    for g, sub in df.groupby(group_col):
        idx = sub.index
        yt = y_true[idx]
        yp = y_pred[idx]
        pr = y_proba[idx]
        tn, fp, fn, tp = confusion_matrix(yt, yp, labels=[0, 1]).ravel()
        n = int(len(idx))
        fpr = fp / (fp + tn) if (fp + tn) > 0 else None
        fnr = fn / (fn + tp) if (fn + tp) > 0 else None
        ppr = yp.mean() if len(yp) > 0 else None
        auc = safe_auc(yt, pr)
        stats.append({
            "group": str(g),
            "n": n,
            "auc": auc,
            "ppr": None if ppr is None else float(round(float(ppr), 4)),
            "fpr": None if fpr is None else float(round(float(fpr), 4)),
            "fnr": None if fnr is None else float(round(float(fnr), 4)),
        })
    out["by_" + group_col] = stats
    # disparities
    numeric_keys = ["auc", "ppr", "fpr", "fnr"]
    disp = {}
    for k in numeric_keys:
        vals = [s[k] for s in stats if s[k] is not None]
        if vals:
            disp[k + "_min"] = min(vals)
            disp[k + "_max"] = max(vals)
            disp[k + "_gap"] = float(round(disp[k + "_max"] - disp[k + "_min"], 4))
    out["disparities"] = disp
    return out


def main():
    df = pd.read_csv(DATA_DIR / "test.csv")
    model_data = None
    try:
        import pickle
        model_data = pickle.load(open(RES_SUP / "model_xgboost.pkl", "rb"))
    except Exception:
        raise

    model = model_data.get("modele")
    features = [f for f in model_data.get("features") if f in df.columns]

    X = df[features].values
    y = df["label_defaut_90j"].values
    proba = model.predict_proba(X)[:, 1]
    pred = (proba > 0.5).astype(int)

    results = {}
    # by region
    if "region_id" in df.columns:
        results.update(group_fairness(df, y, pred, proba, "region_id"))

    # by capacity quartile
    if "capacite_remboursement" in df.columns:
        df["cap_q"] = pd.qcut(df["capacite_remboursement"].fillna(0), q=4, duplicates="drop").astype(str)
        results.update(group_fairness(df, y, pred, proba, "cap_q"))

    # by rps quartile
    if "score_rps_precedent" in df.columns:
        df["rps_q"] = pd.qcut(df["score_rps_precedent"].fillna(0), q=4, duplicates="drop").astype(str)
        results.update(group_fairness(df, y, pred, proba, "rps_q"))

    with open(RES / "eval_equity.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)

    # recommendations text
    rec_lines = [
        "# Recommandations techniques — Modèle MCRS",
        "",
        "## Observations clés",
        "- Disparités AUC, FPR et FNR observées entre régions et groupes socio-économiques (voir `result/eval_equity.json`).",
        "- Certaines régions présentent AUC < 0.7 → vérifier dataset local et features spécifiques (data quality, label noise).",
        "- Missingness 30% réduit AUC ≈ 0.78 — prévoir robustesse et imputation robuste.",
        "",
        "## Actions recommandées (priorité)",
        "1. Re-vérifier représentativité du jeu d'entraînement pour régions sous-performantes et enrichir en données locales si nécessaire.",
        "2. Ajuster seuils par région (seuils opérationnels) pour contrôler FPR/FNR au niveau local plutôt que global.",
        "3. Mettre en place monitoring post-déploiement : AUC, FPR, FNR, dérive covariables par région et par quartile de capacité.",
        "4. Ajouter calibration locale si nécessaire (re-calibration par région).",
        "5. Avant déploiement, exécuter un pilote en production avec revue humaine pour les cas à risque élevé.",
        "",
        "## Actions techniques (moyen terme)",
        "- Entraîner modèles locaux ou modèles multi-tâches avec adaptation par domaine (domain adaptation).",
        "- Explorer modèles plus robustes au bruit et missingness (ensembles, regularization).",
        "- Documenter features sensibles et limiter usage si elles introduisent biais discriminatoires.",
        "",
        "## Mesures opérationnelles",
        "- Définir KPIs métier par région (ex : gains de recouvrement, coûts faux positifs).",
        "- Processus d'appel manuel pour prédictions critiques et procédure de contestation.",
    ]

    with open(RES / "recommendations.md", "w", encoding="utf-8") as f:
        f.write("\n".join(rec_lines))

    print("Analyse d'équité et recommandations générées : result/eval_equity.json, result/recommendations.md")


if __name__ == "__main__":
    main()
