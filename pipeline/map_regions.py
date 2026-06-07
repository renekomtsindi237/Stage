"""Mapper les codes région (`REGxx`) vers les noms réels du Cameroun.

Usage:
 - Éditez `pipeline/region_mapping.json` si nécessaire.
 - Lancez : `python pipeline/map_regions.py` pour générer
   `result/eval_equity_cameroon.json` et mettre à jour `result/recommendations.md`.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).parent.parent
DATA_DIR = ROOT / "data" / "warehouse" / "ml"
RES = ROOT / "result"
MAPPING = Path(__file__).parent / "region_mapping.json"

# Default Cameroon regions list provided by user
CAMEROON_REGIONS = [
    "Centre", "Littoral", "Ouest", "Nord-Ouest", "Sud-Ouest",
    "Bamenda", "Est", "Sud", "Extrême-Nord", "Nord"
]


def main():
    # load existing equity results
    eq_path = RES / "eval_equity.json"
    if not eq_path.exists():
        print("Fichier result/eval_equity.json introuvable — exécutez d'abord pipeline/analyze_equity_and_recommendations.py")
        return
    eq = json.loads(eq_path.read_text(encoding="utf-8"))

    # collect region codes present
    codes = []
    if "by_region_id" in eq:
        codes = [g["group"] for g in eq["by_region_id"]]
    elif "by_region" in eq:
        codes = [g.get("region") for g in eq["by_region"] if g.get("region")]

    codes = sorted(list(dict.fromkeys([c for c in codes if c is not None])))

    # create mapping template if not exists
    if not MAPPING.exists():
        mapping = {code: (CAMEROON_REGIONS[i] if i < len(CAMEROON_REGIONS) else "") for i, code in enumerate(codes)}
        MAPPING.write_text(json.dumps(mapping, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"Mapping template créé : {MAPPING}\nVérifiez/éditer le fichier si nécessaire puis relancez le script.")
        return

    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))

    # apply mapping to equity results
    eq_cam = eq.copy()
    if "by_region_id" in eq_cam:
        for g in eq_cam["by_region_id"]:
            code = g["group"]
            g["region_name"] = mapping.get(code, code)
    if "by_region" in eq_cam:
        for g in eq_cam["by_region"]:
            code = g.get("region")
            if code:
                g["region_name"] = mapping.get(code, code)

    out_path = RES / "eval_equity_cameroon.json"
    out_path.write_text(json.dumps(eq_cam, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Fichier généré : {out_path}")

    # update recommendations to list Cameroonian regions and mapping used
    rec_path = RES / "recommendations.md"
    rec = rec_path.read_text(encoding="utf-8") if rec_path.exists() else "# Recommandations\n"
    header = "\n## Adaptation locale — Cartographie régionale (Cameroun)\n"
    lines = [header, "Mapping codes→régions utilisé :", "\n"]
    for code in codes:
        lines.append(f"- {code} → {mapping.get(code, '')}")
    lines.append("\n")
    rec_new = rec + "\n" + "\n".join(lines)
    rec_path.write_text(rec_new, encoding="utf-8")
    print(f"recommendations.md mis à jour avec la cartographie régionale.")


if __name__ == "__main__":
    main()
