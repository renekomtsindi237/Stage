"""
IMF Pipeline — Calcul MCRS paramétrable
========================================

Scoring MCRS (Multi-Criteria Recovery Scoring) composite [0, 1].
Les poids et seuils sont chargés depuis scoring_config.json et peuvent
être modifiés par le DSI sans retrainement du modèle XGBoost.

Trois composantes (EF-R05) :
  CRS (Collection Reliability Score)  — régularité terrain
  RPS (Recovery Prediction Score)     — XGBoost P(défaut 90j)
  CSI (Client Solvency Index)         — facteurs externes

  MCRS = w_crs*(1-CRS) + w_rps*RPS + w_csi*(1-CSI)

Usage :
  from compute_mcrs import MCRSScorer
  scorer = MCRSScorer()
  result = scorer.score(row_dict)
  # result = {mcrs, crs, rps, csi, risque, alertes, cobac_classe, cobac_provision}
"""

from __future__ import annotations

import json
import pickle
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

ROOT        = Path(__file__).parent.parent
CONFIG_PATH = Path(__file__).parent / "scoring_config.json"
MODEL_PATH  = ROOT / "result" / "supervised" / "model_xgboost.pkl"

# Features attendues par le modèle XGBoost (dans l'ordre d'entraînement)
CRS_FEATURES = [
    "regularite_collecte_pct",
    "nb_collectes_30j",
    "montant_moyen_collecte",
    "tendance_collecte_30j",
    "coefficient_variation_collecte",
    "nb_semaines_sans_collecte",
    "rang_collecte_agence",
]
RPS_FEATURES = [
    "jours_retard_actuel",
    "nb_incidents_paiement_12m",
    "taux_remboursement_historique",
    "ratio_creance_revenus",
    "nb_reechelonnements",
    "score_rps_precedent",
]
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
# Features contextuelles camerounaises (zones agroclimatiques, mobile money, saisonnalité agricole)
CAMEROON_FEATURES = [
    "risque_regional",
    "taux_penetration_mobile",
    "zone_agroclimatique",
    "saison_recolte_active",
]
ALL_FEATURES = CRS_FEATURES + RPS_FEATURES + CSI_FEATURES + CAMEROON_FEATURES


# ─── Configuration ────────────────────────────────────────────────────────────

@dataclass
class MCRSConfig:
    """Configuration complète du scoring, chargée depuis scoring_config.json."""

    # Poids MCRS
    w_crs: float = 0.35
    w_rps: float = 0.45
    w_csi: float = 0.20

    # Seuils de risque
    seuil_faible:   float = 0.30
    seuil_modere:   float = 0.55
    seuil_eleve:    float = 0.75

    # Seuils d'alerte
    alerte_defaut_imminent:       float = 0.75
    alerte_deterioration_rapide:  float = 0.65
    alerte_baisse_collecte:       float = 0.50

    # Paramètres CRS
    crs_weights:             dict = field(default_factory=lambda: {
        "regularite_collecte_pct":      0.30,
        "nb_collectes_30j_normalise":   0.20,
        "taux_remboursement_historique": 0.25,
        "absence_semaines_penalite":    0.15,
        "stabilite_montants":           0.10,
    })
    nb_collectes_ref:        float = 4.0
    semaines_ref:            float = 52.0

    # Paramètres CSI
    csi_weights:             dict = field(default_factory=lambda: {
        "indice_resilience":      0.35,
        "capacite_remboursement": 0.30,
        "pression_prix":          0.15,
        "pression_climatique":    0.10,
        "diversification":        0.10,
    })
    inflation_ref:           float = 5.0
    secheresse_max:          float = 4.0
    capacite_saturation:     float = 2.0

    # PSI
    psi_threshold:           float = 0.20

    @classmethod
    def load(cls, path: Path = CONFIG_PATH) -> "MCRSConfig":
        cfg_raw = json.loads(path.read_text(encoding="utf-8"))

        w = cfg_raw["mcrs"]["weights"]
        rt = cfg_raw["mcrs"]["risk_thresholds"]
        at = cfg_raw["mcrs"]["alert_thresholds"]
        crs_cfg = cfg_raw["crs"]
        csi_cfg = cfg_raw["csi"]

        return cls(
            w_crs=w["crs"], w_rps=w["rps"], w_csi=w["csi"],
            seuil_faible=rt["faible"], seuil_modere=rt["modere"], seuil_eleve=rt["eleve"],
            alerte_defaut_imminent=at["risque_defaut_imminent"],
            alerte_deterioration_rapide=at["deterioration_rapide"],
            alerte_baisse_collecte=at["baisse_collecte_persistante"],
            crs_weights=crs_cfg["sub_weights"],
            nb_collectes_ref=float(crs_cfg["nb_collectes_reference_mensuel"]),
            semaines_ref=float(crs_cfg["semaines_reference_annuel"]),
            csi_weights=csi_cfg["sub_weights"],
            inflation_ref=float(csi_cfg["inflation_reference_pct"]),
            secheresse_max=float(csi_cfg["secheresse_max"]),
            capacite_saturation=float(csi_cfg["capacite_saturation"]),
            psi_threshold=float(cfg_raw["retraining"]["psi_threshold"]),
        )

    def save(self, path: Path = CONFIG_PATH) -> None:
        raw = json.loads(path.read_text(encoding="utf-8"))
        raw["mcrs"]["weights"]           = {"crs": self.w_crs, "rps": self.w_rps, "csi": self.w_csi}
        raw["mcrs"]["risk_thresholds"]   = {
            "faible": self.seuil_faible, "modere": self.seuil_modere,
            "eleve": self.seuil_eleve, "critique": 1.0
        }
        raw["mcrs"]["alert_thresholds"]  = {
            "risque_defaut_imminent": self.alerte_defaut_imminent,
            "deterioration_rapide": self.alerte_deterioration_rapide,
            "baisse_collecte_persistante": self.alerte_baisse_collecte,
        }
        raw["crs"]["sub_weights"]        = self.crs_weights
        raw["csi"]["sub_weights"]        = self.csi_weights
        raw["retraining"]["psi_threshold"] = self.psi_threshold
        path.write_text(json.dumps(raw, indent=2, ensure_ascii=False), encoding="utf-8")

    def validate(self) -> None:
        total = round(self.w_crs + self.w_rps + self.w_csi, 6)
        if abs(total - 1.0) > 1e-4:
            raise ValueError(f"Poids MCRS doivent sommer à 1.0 — actuel : {total}")
        if not (0 < self.seuil_faible < self.seuil_modere < self.seuil_eleve < 1.0):
            raise ValueError("Seuils de risque doivent être strictement croissants dans ]0, 1[")


# ─── Calcul des composantes ───────────────────────────────────────────────────

def _clip(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, v))


def compute_crs(row: dict[str, Any], cfg: MCRSConfig) -> float:
    """
    Collection Reliability Score — régularité des collectes terrain.
    Retourne un score 0→1 (1 = excellent comportement collecte).
    """
    w = cfg.crs_weights

    regularite   = _clip(float(row.get("regularite_collecte_pct", 0.0)))
    nb_coll      = _clip(float(row.get("nb_collectes_30j", 0.0)) / cfg.nb_collectes_ref)
    taux_remb    = _clip(float(row.get("taux_remboursement_historique", 0.0)))
    nb_sans      = float(row.get("nb_semaines_sans_collecte", 0.0))
    absence_pen  = _clip(1.0 - nb_sans / cfg.semaines_ref)
    coeff_var    = float(row.get("coefficient_variation_collecte", 0.0))
    stabilite    = _clip(1.0 - coeff_var / 2.0)  # coeff_var >2 → score 0

    crs = (
        w["regularite_collecte_pct"]      * regularite
        + w["nb_collectes_30j_normalise"] * nb_coll
        + w["taux_remboursement_historique"] * taux_remb
        + w["absence_semaines_penalite"]  * absence_pen
        + w["stabilite_montants"]         * stabilite
    )
    return round(_clip(crs), 4)


def compute_csi(row: dict[str, Any], cfg: MCRSConfig) -> float:
    """
    Client Solvency Index — résilience économique et facteurs externes.
    Retourne un score 0→1 (1 = très résilient).
    """
    w = cfg.csi_weights

    resilience = _clip(float(row.get("indice_resilience", 0.5)) / 1.0)

    cap_remb_raw = float(row.get("capacite_remboursement", 0.0))
    cap_remb     = _clip(cap_remb_raw / cfg.capacite_saturation)

    inflation     = float(row.get("inflation", cfg.inflation_ref))
    pression_prix = _clip(1.0 - max(0.0, inflation - cfg.inflation_ref) / cfg.inflation_ref)

    secheresse    = float(row.get("indice_secheresse", 0.0))
    pression_clim = _clip(1.0 - secheresse / cfg.secheresse_max)

    diversif = _clip(float(row.get("score_diversification_produits", 0.5)))

    csi = (
        w["indice_resilience"]      * resilience
        + w["capacite_remboursement"] * cap_remb
        + w["pression_prix"]          * pression_prix
        + w["pression_climatique"]    * pression_clim
        + w["diversification"]        * diversif
    )
    return round(_clip(csi), 4)


def classify_risk(mcrs: float, cfg: MCRSConfig) -> str:
    if mcrs < cfg.seuil_faible:  return "FAIBLE"
    if mcrs < cfg.seuil_modere:  return "MODERE"
    if mcrs < cfg.seuil_eleve:   return "ELEVE"
    return "CRITIQUE"


def detect_alerts(mcrs: float, crs: float, cfg: MCRSConfig) -> list[str]:
    alerts = []
    if mcrs >= cfg.alerte_defaut_imminent:
        alerts.append("RISQUE_DEFAUT_IMMINENT")
    elif mcrs >= cfg.alerte_deterioration_rapide:
        alerts.append("DETERIORATION_RAPIDE")
    if (1.0 - crs) >= cfg.alerte_baisse_collecte:
        alerts.append("BAISSE_COLLECTE_PERSISTANTE")
    return alerts


def cobac_class(jours_retard: float) -> tuple[str, float]:
    j = int(jours_retard)
    if j < 30:   return "A", 0.00
    if j < 90:   return "B", 0.20
    if j < 180:  return "C", 0.50
    if j < 360:  return "D", 0.80
    return "E", 1.00


# ─── Scorer principal ─────────────────────────────────────────────────────────

class MCRSScorer:
    """
    Scorer MCRS paramétrable.

    Charge le modèle XGBoost entraîné et la configuration depuis les fichiers
    par défaut, mais les deux peuvent être passés explicitement.

    Exemple :
        scorer = MCRSScorer()
        result = scorer.score({"regularite_collecte_pct": 0.7,
                               "jours_retard_actuel": 45, ...})
    """

    def __init__(
        self,
        model_path: Path = MODEL_PATH,
        config_path: Path = CONFIG_PATH,
    ) -> None:
        # CalibratedModel doit être importé avant de charger le pickle
        sys.path.insert(0, str(Path(__file__).parent))
        from models import CalibratedModel  # noqa: F401 — registre pickle
        with open(model_path, "rb") as f:
            saved = pickle.load(f)
        self._model    = saved["modele"]
        self._features = saved["features"]
        self.config    = MCRSConfig.load(config_path)
        self.config.validate()

    def reload_config(self, config_path: Path = CONFIG_PATH) -> None:
        """Recharge la configuration à chaud sans recharger le modèle."""
        self.config = MCRSConfig.load(config_path)
        self.config.validate()

    def score(self, row: dict[str, Any]) -> dict[str, Any]:
        """
        Calcule le score MCRS pour un client.

        Paramètre
        ---------
        row : dict avec les 30 features métier (valeurs manquantes autorisées → défauts régionaux).
              Les 4 features camerounaises (risque_regional, taux_penetration_mobile,
              zone_agroclimatique, saison_recolte_active) utilisent des valeurs nationales
              moyennes par défaut si absentes.

        Retour
        ------
        dict avec : mcrs, crs, rps, csi, risque, alertes, cobac_classe, cobac_provision
        """
        # Défauts pour les features camerounaises (valeurs nationales moyennes)
        row.setdefault("risque_regional", 1.12)          # moyenne pondérée des 10 régions
        row.setdefault("taux_penetration_mobile", 0.50)  # taux national moyen
        row.setdefault("zone_agroclimatique", 1)          # équatorial (dominant en surface)
        row.setdefault("saison_recolte_active", 0)        # non-saison par défaut
        cfg = self.config

        # ── CRS — formule configurable ────────────────────────────────────────
        crs = compute_crs(row, cfg)

        # ── CSI — formule configurable ────────────────────────────────────────
        csi = compute_csi(row, cfg)

        # ── RPS — modèle XGBoost ──────────────────────────────────────────────
        x = np.array([[float(row.get(f, 0.0)) for f in self._features]])
        rps = float(self._model.predict_proba(x)[0, 1])

        # ── MCRS composite ────────────────────────────────────────────────────
        # (1-CRS) et (1-CSI) : plus CRS/CSI est bon, moins il contribue au risque
        mcrs = _clip(cfg.w_crs * (1.0 - crs) + cfg.w_rps * rps + cfg.w_csi * (1.0 - csi))
        mcrs = round(mcrs, 4)

        # ── Classification et alertes ─────────────────────────────────────────
        risque  = classify_risk(mcrs, cfg)
        alertes = detect_alerts(mcrs, crs, cfg)

        # ── COBAC ─────────────────────────────────────────────────────────────
        jours = float(row.get("jours_retard_actuel", 0.0))
        classe_cobac, provision = cobac_class(jours)

        return {
            "mcrs":              mcrs,
            "crs":               crs,
            "rps":               round(rps, 4),
            "csi":               csi,
            "risque":            risque,
            "alertes":           alertes,
            "cobac_classe":      classe_cobac,
            "cobac_provision_taux": provision,
        }

    def score_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Score un DataFrame complet. Ajoute les colonnes mcrs, crs, rps, csi,
        risque, alertes, cobac_classe, cobac_provision_taux.
        """
        results = [self.score(row) for row in df.to_dict(orient="records")]
        out = df.copy()
        for col in ["mcrs", "crs", "rps", "csi", "risque",
                    "alertes", "cobac_classe", "cobac_provision_taux"]:
            out[col] = [r[col] for r in results]
        return out

    def update_weights(self, w_crs: float, w_rps: float, w_csi: float,
                       save: bool = True, config_path: Path = CONFIG_PATH) -> None:
        """
        Met à jour les poids MCRS à chaud (sans retraining).
        Persiste dans scoring_config.json si save=True.
        """
        self.config.w_crs = w_crs
        self.config.w_rps = w_rps
        self.config.w_csi = w_csi
        self.config.validate()
        if save:
            self.config.save(config_path)

    def update_thresholds(self, faible: float, modere: float, eleve: float,
                          save: bool = True, config_path: Path = CONFIG_PATH) -> None:
        """Met à jour les seuils de classification du risque à chaud."""
        self.config.seuil_faible  = faible
        self.config.seuil_modere  = modere
        self.config.seuil_eleve   = eleve
        self.config.validate()
        if save:
            self.config.save(config_path)


# ─── Point d'entrée ───────────────────────────────────────────────────────────

def _demo() -> None:
    scorer = MCRSScorer()
    cfg    = scorer.config

    print("Configuration chargée :")
    print(f"  Poids : CRS={cfg.w_crs}  RPS={cfg.w_rps}  CSI={cfg.w_csi}")
    print(f"  Seuils risque : FAIBLE<{cfg.seuil_faible}  MODERE<{cfg.seuil_modere}"
          f"  ELEVE<{cfg.seuil_eleve}  CRITIQUE>=0.75")
    print()

    import pandas as pd
    test = pd.read_csv(ROOT / "data" / "warehouse" / "ml" / "test.csv")

    # Score du premier client
    client_0 = test.iloc[0].to_dict()
    result   = scorer.score(client_0)
    print("Client 0 :", client_0.get("client_id", "?"))
    for k, v in result.items():
        print(f"  {k:28s}: {v}")
    print()

    # Batch score sur l'ensemble du test
    scored = scorer.score_dataframe(test)
    print("Distribution des risques (test complet) :")
    print(scored["risque"].value_counts().to_string())
    print()
    print("Distribution des classes COBAC :")
    print(scored["cobac_classe"].value_counts().sort_index().to_string())
    print()
    print("Alertes déclenchées :", scored["alertes"].apply(len).gt(0).sum(),
          "/", len(scored))
    print()

    from sklearn.metrics import roc_auc_score
    auc = roc_auc_score(scored["label_defaut_90j"], scored["mcrs"])
    print(f"AUC MCRS composite (test) : {auc:.4f}")
    print()

    # Démonstration de la paramétrie à chaud
    print("--- Mise à jour des poids (DSI augmente le poids CRS) ---")
    scorer.update_weights(w_crs=0.50, w_rps=0.35, w_csi=0.15, save=False)
    scored_new = scorer.score_dataframe(test)
    auc_new = roc_auc_score(scored_new["label_defaut_90j"], scored_new["mcrs"])
    print(f"  Nouveau AUC avec CRS=50% RPS=35% CSI=15% : {auc_new:.4f}")


if __name__ == "__main__":
    _demo()
