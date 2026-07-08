"""
train_mcrs_champion.py — Entraînement formel du modèle MCRS champion/challenger.

Point d'entrée officiel pour (ré)entraîner MCRS, en complément du DAG
Airflow `dag_ml_training` (qui reste le mécanisme de production visé —
walk-forward sur `ml.features_client`, planifié chaque dimanche) : utilisable
manuellement tant que la connexion base de données d'Airflow n'est pas
réparée (cf. docs/V0/06_Doc_Systeme/MCRS_Deploiement_Modele.md), ou pour tout
entraînement ad hoc (jeu de données partenaire, démonstration).

Ne sauvegarde JAMAIS directement dans champion/ : toujours dans
challenger/, à comparer et promouvoir explicitement via
promouvoir_modele.py — c'est la même discipline que le DAG Airflow
(comparer_champion_challenger -> promouvoir_challenger), pour qu'aucun
modèle non validé ne devienne actif par accident.

Usage
-----
    # Sur un jeu de données réel (colonnes ALL_FEATURES + label_defaut_90j
    # + client_id_externe + imf_code, une ligne = un client à une date) :
    python train_mcrs_champion.py --donnees chemin/vers/extrait.csv --source "Extraction ml.features_client 2026-07-08"

    # Jeu de démonstration (25 clients réels FINTECH SARL + ~175
    # synthétiques calibrés) — à utiliser uniquement pour une présentation,
    # jamais pour une décision de recouvrement réelle :
    python train_mcrs_champion.py --demo
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pipeline.src.ml.mcrs_model import ALL_FEATURES, MCRSModel

CHALLENGER_DIR = Path(__file__).parent / "models" / "challenger"
DEMO_CSV = Path(__file__).parent / "models" / "fintech" / "features_fintech.csv"
N_SYNTHETIQUES_DEMO = 175
TAUX_DEFAUT_CIBLE_DEMO = 0.17


def generer_synthetiques_demo(n: int, rng: np.random.Generator, taux_defaut: float) -> pd.DataFrame:
    """
    Cohorte synthétique calibrée sur une variable latente de solvabilité
    continue + bruit indépendant par feature — le chevauchement délibéré
    entre profils sains et à risque produit un signal crédible (AUC
    walk-forward ~0.85-0.90) plutôt qu'une séparation triviale (~0.99, qui
    trahirait une fuite d'information plutôt qu'un vrai pouvoir prédictif).
    """
    latent = rng.normal(0, 1, n)
    seuil = np.quantile(latent, 1 - taux_defaut)
    proba_defaut = 1 / (1 + np.exp(-3 * (latent - seuil)))
    label = (rng.uniform(0, 1, n) < proba_defaut).astype(int)

    def bruite(signal, sigma, low=None, high=None):
        v = signal + rng.normal(0, sigma, n)
        return v.clip(low, high) if (low is not None or high is not None) else v

    anciennete_jours = rng.integers(30, 900, n).astype(float)
    jours_retard_max = bruite(20 + latent * 30, 25, 0, 180)

    return pd.DataFrame({
        "client_id_externe": [f"SYN-{i:04d}" for i in range(n)],
        "imf_code": "FINTECH",
        "anciennete_jours": anciennete_jours,
        "anciennete_client_jours": anciennete_jours,
        "revenu_mensuel_estime": bruite(220_000 - latent * 25_000, 70_000, 50_000, 600_000),
        "nb_collectes_12m": rng.integers(0, 30, n).astype(float),
        "regularite_collecte_pct": bruite(70 - latent * 15, 20, 0, 100),
        "montant_moy_collecte": bruite(35_000 - latent * 3_000, 12_000, 2_000, 90_000),
        "ecart_type_collecte": rng.uniform(1_000, 20_000, n),
        "montant_total_collectes_12m": bruite(35_000 - latent * 3_000, 12_000, 2_000, 90_000) * rng.integers(0, 30, n),
        "nb_cycles_manques_12m": bruite(6 + latent * 6, 5, 0, 40),
        "tendance_collecte_3m": rng.uniform(-50, 50, n),
        "jours_retard_max": jours_retard_max,
        "jours_retard_moyen": jours_retard_max * rng.uniform(0.4, 1.0, n),
        "nb_incidents_paiement": bruite(1 + latent * 1.5, 1.2, 0, 8),
        "taux_remboursement_pct": bruite(80 - latent * 20, 18, 0, 100),
        "montant_impaye_courant": bruite(latent * 400_000 + 150_000, 250_000, 0, None),
        "nb_remboursements_12m": bruite(6 - latent * 2.5, 2.5, 0, 12),
        "classe_risque_cobac_encode": np.round(bruite(1 + latent, 1.1, 0, 4)),
        "label_defaut_90j": label.astype(float),
        "nb_produits_actifs": rng.integers(1, 4, n).astype(float),
        "indice_resilience": (rng.integers(1, 4, n).astype(float) / 5).clip(0, 1),
        "ratio_collecte_credit": rng.uniform(0.1, 2.0, n),
        "capacite_remboursement": bruite(1.8 - latent * 0.6, 0.6, 0.1, 4.0),
        "inflation_mensuelle_moy": rng.uniform(2.5, 6.0, n),
        "taux_directeur_beac": 5.0,
        "precipitation_moy_mm": rng.uniform(20, 180, n),
        "indice_secheresse": rng.uniform(-2, 2, n),
        "nb_evenements_negatifs": rng.integers(0, 3, n).astype(float),
        "prix_produit_principal_moy": rng.uniform(300, 1600, n),
        "volatilite_prix_produit": rng.uniform(20, 200, n),
        "tendance_prix_30j": rng.uniform(-50, 80, n),
        "prix_lag_30j": rng.uniform(300, 1600, n),
        "prix_lag_90j": rng.uniform(300, 1600, n),
        "est_producteur": rng.integers(0, 2, n).astype(float),
    })


def construire_jeu_demo() -> tuple[pd.DataFrame, dict]:
    rng = np.random.default_rng(42)
    df_reel = pd.read_csv(DEMO_CSV)
    df_reel["anciennete_jours"] = df_reel["anciennete_jours"].fillna(df_reel["anciennete_client_jours"])
    df_syn = generer_synthetiques_demo(N_SYNTHETIQUES_DEMO, rng, TAUX_DEFAUT_CIBLE_DEMO)
    df = pd.concat([df_reel, df_syn], ignore_index=True, sort=False)

    provenance = {
        "type": "DEMONSTRATION",
        "n_reels": len(df_reel),
        "n_defaut_reels": int(df_reel["label_defaut_90j"].sum()),
        "n_synthetiques": len(df_syn),
        "avertissement": (
            "MODELE DE DEMONSTRATION (presentation de projet, pas de production). "
            f"{len(df_reel)} clients reels FINTECH SARL ({int(df_reel['label_defaut_90j'].sum())} defaut(s)) "
            f"+ {len(df_syn)} clients synthetiques (variable latente + bruit, taux de defaut cible "
            f"{TAUX_DEFAUT_CIBLE_DEMO:.0%}). Ne pas utiliser pour une decision de recouvrement reelle."
        ),
    }
    return df, provenance


def main() -> int:
    parser = argparse.ArgumentParser(description="Entraîne un challenger MCRS (jamais directement le champion).")
    parser.add_argument("--donnees", type=Path, help="CSV réel (colonnes ALL_FEATURES + label_defaut_90j + client_id_externe + imf_code)")
    parser.add_argument("--source", type=str, default=None, help="Description de la provenance des données (traçabilité, ex. requête SQL/date d'extraction)")
    parser.add_argument("--demo", action="store_true", help="Utilise le jeu FINTECH SARL réel + augmentation synthétique (démonstration uniquement)")
    parser.add_argument("--sortie", type=Path, default=CHALLENGER_DIR, help=f"Dossier de sortie (défaut : {CHALLENGER_DIR})")
    args = parser.parse_args()

    if not args.donnees and not args.demo:
        parser.error("Fournir --donnees <csv> (entraînement réel) ou --demo (jeu de démonstration).")

    if args.demo:
        df, provenance = construire_jeu_demo()
    else:
        df = pd.read_csv(args.donnees)
        if "label_defaut_90j" not in df.columns:
            parser.error(f"Colonne label_defaut_90j absente de {args.donnees}.")
        n_defaut = int(df["label_defaut_90j"].sum())
        if n_defaut < 2:
            parser.error(
                f"{n_defaut} cas de défaut dans {args.donnees} — insuffisant pour une validation croisée "
                "(minimum 2, réalistiquement des dizaines). Utiliser --demo si c'est voulu, ou fournir un extrait plus large."
            )
        provenance = {
            "type": "REEL",
            "source": args.source or str(args.donnees),
            "n_lignes": len(df),
            "n_defaut": n_defaut,
        }

    print(f"Lignes d'entraînement : {len(df)} — provenance : {provenance['type']}")
    print(df["label_defaut_90j"].value_counts())

    missing = [c for c in ALL_FEATURES if c not in df.columns]
    if missing:
        print(f"⚠ Features manquantes (imputées par défaut) : {missing}")

    if "anciennete_jours" not in df.columns and "anciennete_client_jours" in df.columns:
        df["anciennete_jours"] = df["anciennete_client_jours"]

    ref_date = datetime.utcnow()
    dates = ref_date - pd.to_timedelta(df["anciennete_jours"].fillna(365), unit="D")

    model = MCRSModel()
    model.fit(df, df["label_defaut_90j"].astype(int), dates)
    model.metrics_["_provenance"] = provenance
    model.metrics_["_entraine_le"] = ref_date.isoformat()

    chemin = model.sauvegarder(args.sortie)
    print(f"\nChallenger sauvegardé : {chemin}")
    print(f"AUC-ROC moyen (walk-forward) : {model.metrics_.get('auc_roc')}")
    print(f"Gini moyen : {model.metrics_.get('gini')}")
    print("\nProchaine étape : python promouvoir_modele.py — compare au champion actuel et promeut si meilleur.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
