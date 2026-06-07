"""Construit les valeurs d'imputation robustes à partir du jeu d'entraînement.

Sortie : `pipeline/feature_defaults.json`
Ces valeurs sont utilisées par `MCRSModel` si le fichier existe.
"""
from __future__ import annotations

import json
from pathlib import Path

import pandas as pd


ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "data" / "warehouse" / "ml"
OUTPUT = ROOT / "pipeline" / "feature_defaults.json"


def main() -> None:
    train = pd.read_csv(DATA_DIR / "train.csv")
    numeric = train.select_dtypes(include=["number"])
    defaults = {col: float(round(float(numeric[col].median()), 6)) for col in numeric.columns if col != "label_defaut_90j"}
    OUTPUT.write_text(json.dumps(defaults, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Defaults d'imputation écrits : {OUTPUT}")


if __name__ == "__main__":
    main()
