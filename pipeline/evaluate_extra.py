"""Analyses complémentaires : sous-populations, robustesse, efficacité.

Génère JSON/CSV dans `result/eval_subgroups.json`,
`result/eval_robustness.json`, `result/eval_efficiency.json`.
"""
from __future__ import annotations

import json
import os
import pickle
import time
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import (auc, brier_score_loss, f1_score, precision_score,
                             recall_score, roc_auc_score, precision_recall_curve,
                             roc_curve)


ROOT = Path(__file__).parent.parent
DATA_DIR = ROOT / "data" / "warehouse" / "ml"
RES = ROOT / "result"
RES.mkdir(parents=True, exist_ok=True)
RES_SUP = RES / "supervised"
RES_SUP.mkdir(parents=True, exist_ok=True)


def _ks_stat(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    pos = np.sort(y_prob[y_true == 1])
    neg = np.sort(y_prob[y_true == 0])
    if not len(pos) or not len(neg):
        return 0.0
    all_thresh = np.unique(np.concatenate([pos, neg]))
    cdf_pos = np.searchsorted(pos, all_thresh, side="right") / len(pos)
    cdf_neg = np.searchsorted(neg, all_thresh, side="right") / len(neg)
    return float(np.max(np.abs(cdf_pos - cdf_neg)))


def load_model(path: Path):
    with open(path, "rb") as f:
        data = pickle.load(f)
    return data


def metrics_for(y_true, proba, thresh=0.5):
    pred = (proba > thresh).astype(int)
    try:
        auc_ = roc_auc_score(y_true, proba)
    except Exception:
        auc_ = None
    return {
        "auc_roc": None if auc_ is None else float(round(float(auc_), 4)),
        "gini": None if auc_ is None else float(round(2 * auc_ - 1, 4)),
        "ks": float(round(_ks_stat(np.array(y_true), np.array(proba)), 4)),
        "brier": float(round(brier_score_loss(y_true, proba), 4)),
        "f1": float(round(f1_score(y_true, pred), 4)),
        "precision": float(round(precision_score(y_true, pred), 4)),
        "recall": float(round(recall_score(y_true, pred), 4)),
    }


def subgroup_analysis(df, model_obj, features, label_col="label_defaut_90j"):
    out = {}
    # by region
    if "region_id" in df.columns:
        groups = df["region_id"].unique().tolist()
        regs = []
        for r in groups:
            sub = df[df["region_id"] == r]
            if len(sub) < 30:
                continue
            X = sub[features].values
            y = sub[label_col].values
            proba = model_obj.predict_proba(X)[:, 1]
            m = metrics_for(y, proba)
            m.update({"n": int(len(sub)), "region": r})
            regs.append(m)
        out["by_region"] = regs

    # by quantiles of capacity to pay (proxy socio-éco)
    if "capacite_remboursement" in df.columns:
        bins = pd.qcut(df["capacite_remboursement"].fillna(0), q=4, duplicates="drop")
        df_q = df.copy()
        df_q["cap_q"] = bins.astype(str)
        caps = []
        for q, sub in df_q.groupby("cap_q"):
            X = sub[features].values
            y = sub[label_col].values
            proba = model_obj.predict_proba(X)[:, 1]
            m = metrics_for(y, proba)
            m.update({"n": int(len(sub)), "cap_group": q})
            caps.append(m)
        out["by_capacite_quartile"] = caps

    # by previous RPS score bins
    if "score_rps_precedent" in df.columns:
        bins = pd.qcut(df["score_rps_precedent"].fillna(0), q=4, duplicates="drop")
        df_s = df.copy()
        df_s["rps_q"] = bins.astype(str)
        rps = []
        for q, sub in df_s.groupby("rps_q"):
            X = sub[features].values
            y = sub[label_col].values
            proba = model_obj.predict_proba(X)[:, 1]
            m = metrics_for(y, proba)
            m.update({"n": int(len(sub)), "rps_group": q})
            rps.append(m)
        out["by_rps_quartile"] = rps

    return out


def robustness_tests(df, model_obj, features, label_col="label_defaut_90j"):
    results = {}
    X_base = df[features].values
    y = df[label_col].values
    proba_base = model_obj.predict_proba(X_base)[:, 1]
    results["baseline"] = metrics_for(y, proba_base)

    # 1) Gaussian noise 0.1 std and 0.2 std
    num_cols = df[features].select_dtypes(include=["float64", "int64"]).shape[1]
    stds = df[features].std()
    for factor in [0.1, 0.2]:
        df_noisy = df.copy()
        for c in df_noisy[features].columns:
            if df_noisy[c].dtype.kind in "fi":
                sigma = stds[c] if not np.isnan(stds[c]) else 1.0
                noise = np.random.normal(loc=0.0, scale=sigma * factor, size=len(df_noisy))
                df_noisy[c] = df_noisy[c] + noise
        Xn = df_noisy[features].values
        proba = model_obj.predict_proba(Xn)[:, 1]
        results[f"noise_{int(factor*100)}pct"] = metrics_for(y, proba)

    # 2) Missingness 10% and 30% (impute median)
    for p in [0.1, 0.3]:
        df_m = df.copy()
        rnd = np.random.RandomState(42)
        for c in df_m[features].columns:
            mask = rnd.rand(len(df_m)) < p
            df_m.loc[mask, c] = np.nan
        # impute median per column
        for c in df_m[features].columns:
            if df_m[c].isnull().any():
                df_m[c] = df_m[c].fillna(df_m[c].median())
        Xm = df_m[features].values
        proba = model_obj.predict_proba(Xm)[:, 1]
        results[f"missing_{int(p*100)}pct"] = metrics_for(y, proba)

    # 3) Covariate shift: increase prix_moyen_30j by 20% and 50%
    if "prix_moyen_30j" in df.columns:
        for mult in [1.2, 1.5]:
            df_s = df.copy()
            df_s["prix_moyen_30j"] = df_s["prix_moyen_30j"] * mult
            Xs = df_s[features].values
            proba = model_obj.predict_proba(Xs)[:, 1]
            results[f"shift_prix_{int((mult-1)*100)}pct"] = metrics_for(y, proba)

    return results


def efficiency_measures(df, model_obj, features):
    out = {}
    X = df[features].values
    n = len(X)
    # file size of model
    try:
        model_path = RES_SUP / "model_xgboost.pkl"
        out["model_size_bytes"] = int(model_path.stat().st_size)
    except Exception:
        out["model_size_bytes"] = None

    # timing: warmup then multiple runs
    # single run on entire test set
    for _ in range(3):
        _ = model_obj.predict_proba(X)
    reps = 10
    times = []
    for _ in range(reps):
        t0 = time.time()
        _ = model_obj.predict_proba(X)
        t1 = time.time()
        times.append(t1 - t0)
    out["predict_time_mean_s"] = float(round(float(np.mean(times)), 4))
    out["predict_time_std_s"] = float(round(float(np.std(times)), 4))
    out["predict_time_per_row_ms"] = float(round(out["predict_time_mean_s"] / max(1, n) * 1000, 6))
    out["n_test_rows"] = int(n)
    return out


def main():
    df_test = pd.read_csv(DATA_DIR / "test.csv")
    # load model
    model_data = load_model(RES_SUP / "model_xgboost.pkl")
    model = model_data.get("modele")
    features = model_data.get("features")

    # ensure features present
    features = [f for f in features if f in df_test.columns]

    # Subgroup analysis
    sub = subgroup_analysis(df_test, model, features)
    with open(RES / "eval_subgroups.json", "w", encoding="utf-8") as f:
        json.dump(sub, f, indent=2, ensure_ascii=False)

    # Robustness
    rob = robustness_tests(df_test, model, features)
    with open(RES / "eval_robustness.json", "w", encoding="utf-8") as f:
        json.dump(rob, f, indent=2, ensure_ascii=False)

    # Efficiency
    eff = efficiency_measures(df_test, model, features)
    with open(RES / "eval_efficiency.json", "w", encoding="utf-8") as f:
        json.dump(eff, f, indent=2, ensure_ascii=False)

    print("Analyses complémentaires terminées. Fichiers écrits dans result/")


if __name__ == "__main__":
    main()
