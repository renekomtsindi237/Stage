"""Recommande des seuils opérationnels par région à partir du jeu labellisé.

Sorties :
 - `pipeline/region_thresholds.json` (utilisé par `MCRSModel`)
 - `result/regional_thresholds_report.json`

Le seuil recommandé est choisi par maximisation de l'indice de Youden (TPR-FPR)
sur le jeu d'évaluation disponible, avec un minimum de 50 observations par zone.
"""
from __future__ import annotations

import json
import pickle
import sys
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import confusion_matrix, f1_score, roc_auc_score, roc_curve


ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "data" / "warehouse" / "ml"
RESULT_DIR = ROOT / "result"
RESULT_DIR.mkdir(parents=True, exist_ok=True)
MODEL_PATH = RESULT_DIR / "supervised" / "model_xgboost.pkl"
MAPPING_PATH = ROOT / "pipeline" / "region_mapping.json"
THRESHOLDS_PATH = ROOT / "pipeline" / "region_thresholds.json"

sys.path.insert(0, str(ROOT / "pipeline"))


def _load_mapping() -> dict[str, str]:
    if not MAPPING_PATH.exists():
        return {}
    return json.loads(MAPPING_PATH.read_text(encoding="utf-8"))


def _select_threshold(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    fpr, tpr, thresholds = roc_curve(y_true, y_prob)
    youden = tpr - fpr
    idx = int(np.argmax(youden))
    return float(thresholds[idx])


def _metrics(y_true: np.ndarray, y_prob: np.ndarray, threshold: float) -> dict[str, float]:
    pred = (y_prob >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, pred, labels=[0, 1]).ravel()
    fpr = fp / (fp + tn) if (fp + tn) else 0.0
    fnr = fn / (fn + tp) if (fn + tp) else 0.0
    return {
        "auc_roc": float(round(roc_auc_score(y_true, y_prob), 4)),
        "f1": float(round(f1_score(y_true, pred), 4)),
        "fpr": float(round(fpr, 4)),
        "fnr": float(round(fnr, 4)),
    }


def main() -> None:
    df = pd.read_csv(DATA_DIR / "test.csv")
    with open(MODEL_PATH, "rb") as f:
        model_data = pickle.load(f)
    model = model_data["modele"]
    features = [f for f in model_data["features"] if f in df.columns]
    mapping = _load_mapping()

    y = df["label_defaut_90j"].to_numpy(dtype=int)
    proba = model.predict_proba(df[features].values)[:, 1]

    thresholds: dict[str, float] = {}
    report: dict[str, object] = {
        "default_threshold": 0.75,
        "global": {
            **_metrics(y, proba, 0.75),
            "threshold": 0.75,
        },
        "regions": [],
    }

    if "region_id" in df.columns:
        for region_id, sub in df.groupby("region_id"):
            if len(sub) < 50:
                continue
            idx = sub.index.to_numpy()
            y_r = y[idx]
            p_r = proba[idx]
            thr = _select_threshold(y_r, p_r)
            region_name = mapping.get(str(region_id), str(region_id))
            thresholds[str(region_id)] = round(thr, 4)
            thresholds[region_name] = round(thr, 4)
            report["regions"].append({
                "region_id": str(region_id),
                "region_name": region_name,
                "n": int(len(sub)),
                "threshold": round(thr, 4),
                **_metrics(y_r, p_r, thr),
            })

    THRESHOLDS_PATH.write_text(json.dumps(thresholds, indent=2, ensure_ascii=False), encoding="utf-8")
    (RESULT_DIR / "regional_thresholds_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"Seuils locaux écrits : {THRESHOLDS_PATH}")
    print(f"Rapport écrit : {RESULT_DIR / 'regional_thresholds_report.json'}")


if __name__ == "__main__":
    main()
