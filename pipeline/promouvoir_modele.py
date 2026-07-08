"""
promouvoir_modele.py — Compare challenger/ au champion/ actuel et promeut si
meilleur. Étape formelle 2/3 du cycle de ré-entraînement MCRS (1. entraîner
via train_mcrs_champion.py — 2. promouvoir localement — 3. déployer, cf.
docs/V0/06_Doc_Systeme/MCRS_Deploiement_Modele.md).

Ne déploie rien à distance : opère uniquement sur pipeline/models/{champion,
challenger,archive} en local. Le déploiement (scp vers le serveur +
POST /model/reload) reste une étape manuelle documentée séparément — un
modèle ne doit jamais atteindre un service exposé sans validation humaine
explicite de la comparaison de métriques ci-dessous.

Usage
-----
    python promouvoir_modele.py                # comparaison + promotion si meilleur
    python promouvoir_modele.py --forcer        # promeut sans comparer (premier déploiement)
"""

from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime
from pathlib import Path

MODELS_DIR = Path(__file__).parent / "models"
CHAMPION_DIR = MODELS_DIR / "champion"
CHALLENGER_DIR = MODELS_DIR / "challenger"
ARCHIVE_DIR = MODELS_DIR / "archive"


def lire_auc(dossier: Path) -> float | None:
    meta_path = dossier / "mcrs_meta.json"
    if not meta_path.exists():
        return None
    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)
    return meta.get("metrics", {}).get("auc_roc")


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare challenger/ au champion/ et promeut si meilleur.")
    parser.add_argument("--forcer", action="store_true", help="Promeut le challenger sans comparaison (premier déploiement, aucun champion existant)")
    args = parser.parse_args()

    if not (CHALLENGER_DIR / "mcrs_model.pkl").exists():
        print(f"Aucun challenger trouvé dans {CHALLENGER_DIR} — lancer train_mcrs_champion.py d'abord.")
        return 1

    auc_challenger = lire_auc(CHALLENGER_DIR)
    auc_champion = lire_auc(CHAMPION_DIR)

    print(f"AUC challenger : {auc_challenger}")
    print(f"AUC champion actuel : {auc_champion if auc_champion is not None else '(aucun champion déployé)'}")

    if not args.forcer and auc_champion is not None and auc_challenger is not None and auc_challenger <= auc_champion:
        print("Challenger non meilleur que le champion actuel — pas de promotion. (--forcer pour outrepasser)")
        return 0

    if (CHAMPION_DIR / "mcrs_model.pkl").exists():
        horodatage = datetime.utcnow().strftime("%Y%m%dT%H%M%S")
        destination_archive = ARCHIVE_DIR / horodatage
        destination_archive.mkdir(parents=True, exist_ok=True)
        for fichier in CHAMPION_DIR.glob("*"):
            shutil.copy2(fichier, destination_archive / fichier.name)
        print(f"Ancien champion archivé dans {destination_archive}")

    CHAMPION_DIR.mkdir(parents=True, exist_ok=True)
    for fichier in CHALLENGER_DIR.glob("*"):
        shutil.copy2(fichier, CHAMPION_DIR / fichier.name)

    print(f"\nChallenger promu champion ({CHAMPION_DIR}).")
    print("Prochaine étape (déploiement, manuel) :")
    print("  scp pipeline/models/champion/{mcrs_model.pkl,mcrs_meta.json,reference_scores.npy} <serveur>:/ml/models/mcrs/champion/")
    print("  puis redémarrer le conteneur ml-api (POST /model/reload ne recharge qu'un seul worker sur N).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
