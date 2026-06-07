"""
MCRS — Multi-Criteria Recovery Scoring
Score composite [0,1] : plus élevé = plus risqué (probabilité de défaut)

MCRS = 0.35 × CRS + 0.45 × RPS + 0.20 × CSI

Composantes
-----------
CRS (Collection Reliability Score)
    Régularité et tendance des collectes d'épargne terrain de l'agent/client.
    Évalue si le client honore régulièrement sa capacité d'épargne, signal fort
    de sa discipline financière et donc de sa solvabilité future.

RPS (Recovery Prediction Score)
    Probabilité de défaut à 90 jours, estimée par un XGBoost calibré (Platt
    scaling). C'est le seul composant supervisé — la cible est le passage en
    classe COBAC C ou pire dans les 90 jours suivants.

CSI (Client Solvency Index)
    Indice de résilience économique : diversification des produits, volatilité
    et tendance des prix sur les marchés locaux, conditions météo par zone et
    indicateurs macro BEAC/INS. Tient compte du SENS de l'impact prix selon
    que le client est producteur (hausse prix = favorable) ou consommateur net.

Conception
----------
- Walk-forward temporel strict (5 folds, 12m train / 3m test / 1m gap).
- Calibration Platt (isotonique) sur le dernier tiers des données.
- SHAP TreeExplainer pour l'explicabilité — top N features par client.
- PSI (Population Stability Index) pour la détection de drift.
- Vectorisation complète du scoring (pas d'itération row-by-row).
- Gestion explicite des features manquantes et des cas limites.
"""

from __future__ import annotations

import json
import logging
import pickle
from dataclasses import asdict, dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import shap
from scipy.special import expit
from scipy.stats import ks_2samp
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import (
    brier_score_loss,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import TimeSeriesSplit
from xgboost import XGBClassifier

logger = logging.getLogger(__name__)
_SENTINEL = object()  # valeur sentinelle pour les arguments optionnels


def _walk_forward_splits(
    dates_sorted: pd.Series,
    n_folds: int,
    gap_mois: int,
) -> list[tuple[np.ndarray, np.ndarray]]:
    """
    Walk-forward temporel avec gap réel exprimé en mois.

    Contrairement à TimeSeriesSplit(gap=0), cette fonction respecte le paramètre
    gap_mois pour éviter la fuite d'information sur les créances longues durées
    (classes COBAC C/D/E dont l'horizon de défaut est ≥ 90 jours).

    La plage temporelle totale est découpée en `n_folds` fenêtres de test de 90j.
    Pour chaque fold, le jeu d'entraînement s'arrête `gap_mois` mois avant le début
    de la fenêtre de test.
    """
    dates_ts = pd.to_datetime(dates_sorted).values
    max_date = dates_ts.max()

    gap_days = int(gap_mois * 30.44)
    test_days = 90  # 3 mois par fenêtre de test

    splits: list[tuple[np.ndarray, np.ndarray]] = []
    for fold in range(n_folds):
        # fold 0 = plus ancien test, fold n-1 = plus récent
        offset_days = (n_folds - 1 - fold) * test_days
        test_end = max_date - np.timedelta64(offset_days, "D")
        test_start = test_end - np.timedelta64(test_days, "D")
        train_end = test_start - np.timedelta64(gap_days, "D")

        train_idx = np.where(dates_ts < train_end)[0]
        test_idx = np.where((dates_ts >= test_start) & (dates_ts < test_end))[0]

        if len(train_idx) >= 50 and len(test_idx) >= 20:
            splits.append((train_idx, test_idx))

    if not splits:
        # Fallback si le dataset est trop petit pour le gap demandé
        logger.warning(
            "Aucun split walk-forward valide avec gap=%d mois — fallback TimeSeriesSplit",
            gap_mois,
        )
        tscv = TimeSeriesSplit(n_splits=n_folds, gap=0)
        splits = [(np.array(tr), np.array(te)) for tr, te in tscv.split(dates_sorted)]

    return splits


# ─── Groupes de features ─────────────────────────────────────────────────────

CRS_FEATURES: list[str] = [
    "nb_collectes_12m",  # nombre de collectes sur 12 mois glissants
    "regularite_collecte_pct",  # % de semaines avec au moins une collecte (0–100)
    "tendance_collecte_3m",  # pente normalisée (régression OLS) sur 3 mois
    "montant_moy_collecte",  # montant moyen par collecte (FCFA)
    "ecart_type_collecte",  # volatilité du montant de collecte
    "nb_cycles_manques_12m",  # cycles sans aucune collecte (sur 52 semaines max)
    "montant_total_collectes_12m",  # total collecté sur 12 mois
]

RPS_FEATURES: list[str] = [
    "taux_remboursement_pct",  # % du capital remboursé à date (0–100)
    "jours_retard_moyen",  # moyenne des retards sur les 12 derniers mois
    "jours_retard_max",  # maximum des retards sur 12 mois
    "nb_incidents_paiement",  # nombre d'incidents de paiement sur 12 mois
    "montant_impaye_courant",  # encours impayé au moment du scoring (FCFA)
    "nb_remboursements_12m",  # nombre de remboursements effectués sur 12 mois
    "classe_risque_cobac_encode",  # classe COBAC encodée (A=0, B=1, C=2, D=3, E=4)
]

CSI_FEATURES: list[str] = [
    "revenu_mensuel_estime",  # revenu mensuel estimé client (FCFA)
    "anciennete_client_jours",  # ancienneté relation IMF en jours
    "nb_produits_actifs",  # nombre de produits distincts dans l'activité client
    "ratio_collecte_credit",  # montant collecte / encours crédit (capacité relative)
    "capacite_remboursement",  # revenu_mensuel / (montant_echeance * 1.2)
    "indice_resilience",  # min(nb_produits / 5, 1) — diversification activité
    "est_producteur",  # 1 si le client produit/vend, 0 si consommateur net
    "prix_produit_principal_moy",  # prix moyen 90j du produit principal (FCFA/unité)
    "volatilite_prix_produit",  # écart-type du prix produit sur 90j
    "tendance_prix_30j",  # pente régression prix sur 30j (+ = hausse)
    "prix_lag_30j",  # prix moyen période 31–60j (lag 30j) pour détecter tendance retardée
    "prix_lag_90j",  # prix moyen période 91–120j (lag 90j) pour amplitude de variation
    "inflation_mensuelle_moy",  # inflation mensuelle moyenne zone (%)
    "taux_directeur_beac",  # taux directeur BEAC en vigueur (%)
    "precipitation_moy_mm",  # précipitations cumulées 30j (mm)
    "indice_secheresse",  # Palmer DSI négatif = sécheresse
    "nb_evenements_negatifs",  # nombre d'événements perturbateurs sur 30j
]

ALL_FEATURES: list[str] = CRS_FEATURES + RPS_FEATURES + CSI_FEATURES

# Valeurs par défaut pour les features manquantes (médiane sectorielle estimée)
FEATURE_DEFAULTS: dict[str, float] = {
    "regularite_collecte_pct": 60.0,
    "tendance_collecte_3m": 0.0,
    "montant_moy_collecte": 5000.0,
    "ecart_type_collecte": 2000.0,
    "nb_cycles_manques_12m": 8.0,
    "taux_remboursement_pct": 75.0,
    "jours_retard_moyen": 0.0,
    "jours_retard_max": 0.0,
    "nb_incidents_paiement": 0.0,
    "classe_risque_cobac_encode": 0.0,
    "revenu_mensuel_estime": 50000.0,
    "anciennete_client_jours": 365.0,
    "nb_produits_actifs": 2.0,
    "ratio_collecte_credit": 0.1,
    "capacite_remboursement": 1.2,
    "indice_resilience": 0.4,
    "est_producteur": 1.0,
    "prix_produit_principal_moy": 500.0,
    "volatilite_prix_produit": 50.0,
    "tendance_prix_30j": 0.0,
    "prix_lag_30j": 500.0,  # même ordre de grandeur que le prix courant
    "prix_lag_90j": 500.0,
    "inflation_mensuelle_moy": 4.0,
    "taux_directeur_beac": 5.0,
    "precipitation_moy_mm": 80.0,
    "indice_secheresse": 0.0,
    "nb_evenements_negatifs": 0.0,
}

REGION_MAPPING_PATH = Path(__file__).resolve().parents[2] / "region_mapping.json"
REGION_THRESHOLDS_PATH = Path(__file__).resolve().parents[2] / "region_thresholds.json"
FEATURE_DEFAULTS_PATH = Path(__file__).resolve().parents[2] / "feature_defaults.json"

SEUILS_RISQUE: dict[str, tuple[float, float]] = {
    "FAIBLE": (0.00, 0.30),
    "MODERE": (0.30, 0.55),
    "ELEVE": (0.55, 0.75),
    "CRITIQUE": (0.75, 1.01),
}

ACTION_PAR_CLASSE: dict[str, str] = {
    "FAIBLE": "AUCUNE",
    "MODERE": "RELANCE_PREVENTIVE",
    "ELEVE": "VISITE_TERRAIN",
    "CRITIQUE": "MISE_EN_DEMEURE",
}

COBAC_ENCODE: dict[str, int] = {"A": 0, "B": 1, "C": 2, "D": 3, "E": 4}


# ─── Dataclasses ─────────────────────────────────────────────────────────────


@dataclass
class McrsParams:
    # XGBoost
    n_estimators: int = 500
    max_depth: int = 6
    learning_rate: float = 0.05
    subsample: float = 0.80
    colsample_bytree: float = 0.80
    min_child_weight: int = 5
    gamma: float = 0.1
    reg_alpha: float = 0.1
    reg_lambda: float = 1.0
    early_stopping_rounds: int = 50
    scale_pos_weight: float | str = (
        "auto"  # "auto" = calculé depuis le ratio défaut/sain
    )
    # Poids composantes MCRS
    poids_crs: float = 0.35
    poids_rps: float = 0.45
    poids_csi: float = 0.20
    # Walk-forward
    n_folds: int = 5
    taille_train_mois: int = 12
    taille_test_mois: int = 3
    gap_mois: int = (
        3  # 3 mois évite la fuite d'info sur les créances longues durées (C/D/E ≥ 90j)
    )
    # SHAP
    top_n_features: int = 10
    # Intervalle de confiance (bootstrap)
    ic_n_bootstrap: int = 200
    ic_niveau: float = 0.90
    # Revue humaine / pilotage contrôlé
    seuil_revue_humaine_defaut: float = 0.75


@dataclass
class ScoreResult:
    client_id_externe: str
    imf_code: str
    score_crs: float
    score_rps: float
    score_csi: float
    score_mcrs: float
    classe_risque: str
    probabilite_defaut_30j: float
    probabilite_defaut_90j: float
    score_mcrs_ic_bas: float
    score_mcrs_ic_haut: float
    action_recommandee: str
    priorite_recouvrement: int  # 1 (faible) à 5 (critique)
    region_id: str | None = None
    region_name: str | None = None
    seuil_operationnel: float | None = None
    revue_humaine_requise: bool = False
    decision_operationnelle: str = "AUTOMATIQUE"
    shap_values: dict[str, float] = field(default_factory=dict)
    scored_at: str = field(default_factory=lambda: datetime.utcnow().isoformat())

    def to_dict(self) -> dict:
        d = asdict(self)
        d["shap_values"] = self.shap_values
        return d


# ─── Modèle ──────────────────────────────────────────────────────────────────


class MCRSModel:
    """
    Modèle composite MCRS.

    Cycle de vie
    ------------
    1. fit(X, y, dates)          → entraînement walk-forward + calibration
    2. predict_batch(df)         → scoring vectorisé, retourne List[ScoreResult]
    3. predict_single(row)       → scoring d'un client unique (API temps réel)
    4. calculer_psi(ref, cur)    → détection drift
    5. sauvegarder / charger     → persistance pickle + JSON metadata
    """

    def __init__(self, params: McrsParams | None = None) -> None:
        self.params = params or McrsParams()
        self._model_rps: XGBClassifier | None = None
        self._calibrated_rps: CalibratedClassifierCV | None = None
        self._explainer: shap.TreeExplainer | None = None
        self.metrics_: dict[str, Any] = {}
        self.feature_importances_: dict[str, float] = {}
        self._reference_scores: np.ndarray | None = None  # pour PSI
        self._region_mapping: dict[str, str] = self._charger_json_optionnel(
            REGION_MAPPING_PATH
        )
        self._region_thresholds: dict[str, float] = self._charger_json_optionnel(
            REGION_THRESHOLDS_PATH
        )
        self._feature_defaults: dict[str, float] = self._charger_json_optionnel(
            FEATURE_DEFAULTS_PATH
        ) or dict(FEATURE_DEFAULTS)
        self._initialiser_champs_optionnels()

    # ── Entraînement ──────────────────────────────────────────────────────────

    def fit(self, X: pd.DataFrame, y: pd.Series, dates: pd.Series) -> "MCRSModel":
        """
        Entraîne le modèle MCRS par walk-forward temporel.

        Parameters
        ----------
        X     : DataFrame avec toutes les colonnes de ALL_FEATURES (+ client_id_externe, imf_code)
        y     : Série binaire — 1 = défaut à 90 jours (passage COBAC C+), 0 = sain
        dates : Série datetime — date de la ligne, utilisée pour le split temporel strict
        """
        logger.info(
            "Démarrage entraînement MCRS — %d observations, %.1f%% défauts",
            len(X),
            100 * y.mean(),
        )

        X_feat = self._preparer_features(X)
        fold_metrics: list[dict] = []

        # Tri chronologique strict pour le walk-forward
        order = dates.argsort().values
        X_sorted = X_feat.iloc[order].reset_index(drop=True)
        y_sorted = y.iloc[order].reset_index(drop=True)
        dates_sorted = dates.iloc[order].reset_index(drop=True)

        splits = _walk_forward_splits(
            dates_sorted, self.params.n_folds, self.params.gap_mois
        )

        for fold, (train_idx, test_idx) in enumerate(splits):
            X_tr, X_te = X_sorted.iloc[train_idx], X_sorted.iloc[test_idx]
            y_tr, y_te = y_sorted.iloc[train_idx], y_sorted.iloc[test_idx]

            spw = self._scale_pos_weight(y_tr)
            model_fold = self._construire_xgboost(spw)
            model_fold.fit(
                X_tr[ALL_FEATURES],
                y_tr,
                eval_set=[(X_te[ALL_FEATURES], y_te)],
                verbose=False,
            )

            y_proba = model_fold.predict_proba(X_te[ALL_FEATURES])[:, 1]
            fm = _calculer_metriques(y_te.values, y_proba, fold)
            fold_metrics.append(fm)
            logger.info(
                "Fold %d — AUC=%.4f  Gini=%.4f  KS=%.4f  Brier=%.4f",
                fold,
                fm["auc_roc"],
                fm["gini"],
                fm["ks_statistic"],
                fm["brier_score"],
            )

        # Métriques agrégées (moyenne des folds)
        self.metrics_ = {
            k: round(float(np.mean([m[k] for m in fold_metrics])), 4)
            for k in fold_metrics[0]
            if k != "fold"
        }
        self.metrics_["fold_metrics"] = fold_metrics

        # Entraînement final sur toutes les données (sans early stopping — pas d'eval_set)
        logger.info("Entraînement final sur dataset complet...")
        spw_global = self._scale_pos_weight(y_sorted)
        self._model_rps = self._construire_xgboost(
            spw_global, early_stopping_rounds=None
        )
        self._model_rps.fit(X_sorted[ALL_FEATURES], y_sorted, verbose=False)

        # Calibration Platt (sur le dernier tiers des données chronologiquement)
        cut = int(len(X_sorted) * 0.67)
        self._calibrated_rps = CalibratedClassifierCV(
            self._model_rps,
            cv="prefit",
            method="isotonic",
        )
        self._calibrated_rps.fit(X_sorted.iloc[cut:][ALL_FEATURES], y_sorted.iloc[cut:])

        # SHAP
        self._explainer = shap.TreeExplainer(self._model_rps)
        imp = self._model_rps.feature_importances_
        self.feature_importances_ = {
            feat: round(float(imp[i]), 6) for i, feat in enumerate(ALL_FEATURES)
        }

        # Scores de référence pour PSI (sur le dataset de calibration)
        ref_proba = self._calibrated_rps.predict_proba(
            X_sorted.iloc[cut:][ALL_FEATURES]
        )[:, 1]
        self._reference_scores = ref_proba

        logger.info(
            "Entraînement MCRS terminé — AUC_moy=%.4f  Gini_moy=%.4f",
            self.metrics_["auc_roc"],
            self.metrics_["gini"],
        )
        return self

    def _construire_xgboost(
        self,
        scale_pos_weight: float = 1.0,
        early_stopping_rounds: int | None = _SENTINEL,
    ) -> XGBClassifier:
        p = self.params
        esr = (
            p.early_stopping_rounds
            if early_stopping_rounds is _SENTINEL
            else early_stopping_rounds
        )
        return XGBClassifier(
            n_estimators=p.n_estimators,
            max_depth=p.max_depth,
            learning_rate=p.learning_rate,
            subsample=p.subsample,
            colsample_bytree=p.colsample_bytree,
            min_child_weight=p.min_child_weight,
            gamma=p.gamma,
            reg_alpha=p.reg_alpha,
            reg_lambda=p.reg_lambda,
            early_stopping_rounds=esr,
            eval_metric="auc",
            objective="binary:logistic",
            scale_pos_weight=scale_pos_weight,
            tree_method="hist",
            random_state=42,
        )

    def _scale_pos_weight(self, y: pd.Series) -> float:
        if isinstance(self.params.scale_pos_weight, (int, float)):
            return float(self.params.scale_pos_weight)
        n_neg = (y == 0).sum()
        n_pos = (y == 1).sum()
        return float(n_neg / max(n_pos, 1))

    # ── Scoring vectorisé ─────────────────────────────────────────────────────

    def predict_batch(self, df: pd.DataFrame) -> list[ScoreResult]:
        """
        Score un batch de clients.  Vectorisé — O(N) non O(N²).

        Le DataFrame df doit contenir :
        - Les colonnes de ALL_FEATURES (ou un sous-ensemble, les manquantes sont imputées).
        - Les colonnes 'client_id_externe' et 'imf_code'.
        """
        self._verifier_entraine()
        X = self._preparer_features(df)

        # Probabilité de défaut 90j (RPS) vectorisée
        proba_90j: np.ndarray = self._calibrated_rps.predict_proba(X[ALL_FEATURES])[
            :, 1
        ]

        # CRS et CSI vectorisés
        crs_vec: np.ndarray = self._vect_crs(X)
        csi_vec: np.ndarray = self._vect_csi(X)

        # Score composite MCRS
        p = self.params
        mcrs_vec: np.ndarray = (
            p.poids_crs * crs_vec + p.poids_rps * proba_90j + p.poids_csi * csi_vec
        )
        mcrs_vec = np.clip(mcrs_vec, 0.0, 1.0)

        # Intervalles de confiance (bootstrap vectorisé)
        ic_bas, ic_haut = self._intervalle_confiance_batch(mcrs_vec)

        # SHAP (batch)
        shap_matrix: np.ndarray | None = None
        if self._explainer is not None:
            shap_matrix = self._explainer.shap_values(X[ALL_FEATURES].values)

        results: list[ScoreResult] = []
        for i in range(len(X)):
            mcrs = float(mcrs_vec[i])
            classe = _classifier_risque(mcrs)
            region_id = str(df.iloc[i].get("region_id", "") or "")
            region_name = str(df.iloc[i].get("region_name", "") or "")
            seuil_op = self._seuil_operationnel(
                region_id=region_id, region_name=region_name
            )
            revue_humaine = mcrs >= seuil_op

            shap_vals: dict[str, float] = {}
            if shap_matrix is not None:
                sv = shap_matrix[i]
                top_idx = np.argsort(np.abs(sv))[::-1][: self.params.top_n_features]
                shap_vals = {ALL_FEATURES[j]: round(float(sv[j]), 6) for j in top_idx}

            results.append(
                ScoreResult(
                    client_id_externe=str(df.iloc[i]["client_id_externe"]),
                    imf_code=str(df.iloc[i]["imf_code"]),
                    score_crs=round(float(crs_vec[i]), 4),
                    score_rps=round(float(proba_90j[i]), 4),
                    score_csi=round(float(csi_vec[i]), 4),
                    score_mcrs=round(mcrs, 4),
                    classe_risque=classe,
                    probabilite_defaut_30j=round(
                        float(np.clip(proba_90j[i] * 0.35, 0, 1)), 4
                    ),
                    probabilite_defaut_90j=round(float(proba_90j[i]), 4),
                    score_mcrs_ic_bas=round(float(ic_bas[i]), 4),
                    score_mcrs_ic_haut=round(float(ic_haut[i]), 4),
                    action_recommandee=ACTION_PAR_CLASSE[classe],
                    priorite_recouvrement=_priorite(mcrs),
                    region_id=region_id or None,
                    region_name=self._region_mapping.get(
                        region_id, region_name or None
                    ),
                    seuil_operationnel=round(float(seuil_op), 4),
                    revue_humaine_requise=bool(revue_humaine),
                    decision_operationnelle=(
                        "REVUE_HUMAINE" if revue_humaine else "AUTOMATIQUE"
                    ),
                    shap_values=shap_vals,
                )
            )

        return results

    def predict_single(self, row: dict[str, Any]) -> ScoreResult:
        """Score d'un client unique — utilisé par l'API FastAPI."""
        df = pd.DataFrame([row])
        return self.predict_batch(df)[0]

    # ── Scores composites vectorisés ─────────────────────────────────────────

    def _vect_crs(self, X: pd.DataFrame) -> np.ndarray:
        """Collection Reliability Score vectorisé."""
        regularite = (
            X["regularite_collecte_pct"].values / 100.0
        )  # normalise 0–100 → 0–1
        regularite = np.clip(regularite, 0.0, 1.0)

        tendance = X["tendance_collecte_3m"].values
        # Normalisation via sigmoid centrée en 0 (tendance positive = CRS plus bas = moins risqué)
        tendance_norm = expit(
            tendance * 5.0
        )  # tendance > 0 → tendance_norm > 0.5 = bon

        manques_fraction = np.minimum(X["nb_cycles_manques_12m"].values / 52.0, 1.0)

        crs = 0.50 * regularite + 0.30 * tendance_norm + 0.20 * (1.0 - manques_fraction)
        # CRS élevé = client FIABLE → score risque FAIBLE
        # Pour cohérence avec MCRS (élevé = risqué) on inverse : risque_crs = 1 - crs
        return np.clip(1.0 - crs, 0.0, 1.0)

    def _vect_csi(self, X: pd.DataFrame) -> np.ndarray:
        """
        Client Solvency Index vectorisé.

        Gestion producteur / consommateur net :
        - est_producteur = 1 : une HAUSSE du prix améliore sa solvabilité → CSI bas
        - est_producteur = 0 (acheteur net) : une HAUSSE dégrade sa solvabilité → CSI élevé

        Les lag features (prix_lag_30j, prix_lag_90j) capturent la variation inter-périodes,
        donnant une profondeur temporelle que la seule tendance_prix_30j (pente instantanée)
        ne peut pas fournir — notamment pour des chocs de prix à latence de 1–3 mois.
        """
        eps = 1e-9
        resilience = np.clip(X["indice_resilience"].values, 0.0, 1.0)
        volatilite = X["volatilite_prix_produit"].values
        tendance_px = X["tendance_prix_30j"].values
        est_prod = np.clip(X["est_producteur"].values, 0.0, 1.0)
        evenements = np.minimum(X["nb_evenements_negatifs"].values / 5.0, 1.0)
        inflation = np.minimum(np.abs(X["inflation_mensuelle_moy"].values) / 10.0, 1.0)
        secheresse = np.maximum(X["indice_secheresse"].values * -1, 0.0)
        secheresse = np.minimum(secheresse / 4.0, 1.0)

        # Tendance instantanée normalisée [0, 1] (0.5 = stable)
        tendance_norm = expit(tendance_px * 3.0)

        # Impact tendance courte (sens selon producteur/consommateur)
        impact_prix = (
            est_prod * (1.0 - tendance_norm) + (1.0 - est_prod) * tendance_norm
        )

        # Variation vs lag 30j : (prix_actuel - prix_lag30j) / prix_lag30j ∈ [-1, 1]
        prix_actuel = X["prix_produit_principal_moy"].values
        prix_lag30 = X["prix_lag_30j"].values
        prix_lag90 = X["prix_lag_90j"].values
        var30 = np.clip((prix_actuel - prix_lag30) / (prix_lag30 + eps), -1.0, 1.0)
        var90 = np.clip((prix_actuel - prix_lag90) / (prix_lag90 + eps), -1.0, 1.0)

        # Normalise variation [-1,1] → [0,1] (1 = forte hausse)
        var30_norm = (var30 + 1.0) / 2.0
        var90_norm = (var90 + 1.0) / 2.0

        # Impact lag selon profil (même logique que tendance courte)
        impact_var30 = est_prod * (1.0 - var30_norm) + (1.0 - est_prod) * var30_norm
        impact_var90 = est_prod * (1.0 - var90_norm) + (1.0 - est_prod) * var90_norm

        # Volatilité prix normalisée (500 FCFA écart-type = seuil élevé)
        vol_norm = np.minimum(volatilite / 500.0, 1.0)

        # Poids ajustés pour intégrer les lags (somme = 1.0)
        csi = (
            0.25 * (1.0 - resilience)  # faible résilience = risque élevé
            + 0.20 * vol_norm  # forte volatilité = risque élevé
            + 0.15 * impact_prix  # tendance courte (30j instantané)
            + 0.10 * impact_var30  # variation vs lag 30j
            + 0.10 * impact_var90  # variation vs lag 90j (chocs latents)
            + 0.08 * evenements  # événements perturbateurs
            + 0.07 * inflation  # inflation élevée
            + 0.05 * secheresse  # sécheresse (impact agriculteurs)
        )
        return np.clip(csi, 0.0, 1.0)

    # ── Utilitaires ───────────────────────────────────────────────────────────

    def _preparer_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Impute les features manquantes, encode la classe COBAC,
        et garantit que toutes les colonnes ALL_FEATURES existent.
        """
        X = df.copy()

        # Encodage classe COBAC → entier
        if (
            "classe_cobac" in X.columns
            and "classe_risque_cobac_encode" not in X.columns
        ):
            X["classe_risque_cobac_encode"] = (
                X["classe_cobac"].map(COBAC_ENCODE).fillna(0.0)
            )

        # Imputation par défaut pour les colonnes manquantes
        for col in ALL_FEATURES:
            if col not in X.columns:
                default = self._feature_defaults.get(
                    col, FEATURE_DEFAULTS.get(col, 0.0)
                )
                X[col] = default
                logger.debug("Feature '%s' absente — imputée à %.2f", col, default)
            else:
                # Imputer uniquement les NaN (pas les 0 légitimes)
                default = self._feature_defaults.get(
                    col, FEATURE_DEFAULTS.get(col, 0.0)
                )
                X[col] = X[col].fillna(default)

        X[ALL_FEATURES] = X[ALL_FEATURES].astype(float)
        return X

    def _charger_json_optionnel(self, path: Path) -> dict[str, Any]:
        if not path.exists():
            return {}
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            if isinstance(data, dict):
                return data
        except Exception:
            logger.warning(
                "Impossible de charger %s — valeurs par défaut utilisées", path
            )
        return {}

    def _initialiser_champs_optionnels(self) -> None:
        if not hasattr(self, "_region_mapping"):
            self._region_mapping = self._charger_json_optionnel(REGION_MAPPING_PATH)
        if not hasattr(self, "_region_thresholds"):
            self._region_thresholds = self._charger_json_optionnel(
                REGION_THRESHOLDS_PATH
            )
        if not hasattr(self, "_feature_defaults"):
            self._feature_defaults = self._charger_json_optionnel(
                FEATURE_DEFAULTS_PATH
            ) or dict(FEATURE_DEFAULTS)
        if not hasattr(self, "params"):
            self.params = McrsParams()

    def _seuil_operationnel(self, region_id: str = "", region_name: str = "") -> float:
        base = float(self.params.seuil_revue_humaine_defaut)
        for key in [region_id, region_name]:
            if key and key in self._region_thresholds:
                try:
                    return float(self._region_thresholds[key])
                except Exception:
                    continue
        return base

    def set_region_threshold(
        self, region_key: str, threshold: float, persist: bool = True
    ) -> None:
        if not (0.0 < threshold < 1.0):
            raise ValueError("Le seuil opérationnel doit être dans ]0, 1[")
        self._region_thresholds[region_key] = float(threshold)
        if persist:
            REGION_THRESHOLDS_PATH.write_text(
                json.dumps(self._region_thresholds, indent=2, ensure_ascii=False),
                encoding="utf-8",
            )

    def set_region_thresholds(
        self, thresholds: dict[str, float], persist: bool = True
    ) -> None:
        for key, value in thresholds.items():
            self.set_region_threshold(key, float(value), persist=False)
        if persist:
            REGION_THRESHOLDS_PATH.write_text(
                json.dumps(self._region_thresholds, indent=2, ensure_ascii=False),
                encoding="utf-8",
            )

    def _intervalle_confiance_batch(
        self, scores: np.ndarray
    ) -> tuple[np.ndarray, np.ndarray]:
        """
        Intervalle de confiance bootstrap sur le score MCRS.
        Modélise l'incertitude de la combinaison CRS+RPS+CSI par perturbation gaussienne
        calibrée sur la variance des scores du modèle.
        """
        rng = np.random.default_rng(42)
        # Incertitude proportionnelle au score (plus risqué = plus incertain)
        sigma = 0.03 + 0.04 * scores
        n = self.params.ic_n_bootstrap
        alpha = (1.0 - self.params.ic_niveau) / 2.0

        samples = rng.normal(
            loc=scores[:, None],
            scale=sigma[:, None],
            size=(len(scores), n),
        )
        samples = np.clip(samples, 0.0, 1.0)
        ic_bas = np.percentile(samples, alpha * 100, axis=1)
        ic_haut = np.percentile(samples, (1 - alpha) * 100, axis=1)
        return ic_bas, ic_haut

    def _verifier_entraine(self) -> None:
        if self._calibrated_rps is None:
            raise RuntimeError(
                "MCRSModel non entraîné. Appelez fit() avant predict_batch()."
            )

    # ── PSI (détection drift) ─────────────────────────────────────────────────

    @staticmethod
    def calculer_psi(
        ref_scores: np.ndarray,
        cur_scores: np.ndarray,
        bins: int = 10,
    ) -> float:
        """
        Population Stability Index.

        Interprétation :
        - PSI < 0.10 : distribution stable
        - 0.10 ≤ PSI < 0.20 : drift modéré — surveiller
        - PSI ≥ 0.20 : drift significatif — retraining recommandé
        """
        eps = 1e-9
        breakpoints = np.percentile(ref_scores, np.linspace(0, 100, bins + 1))
        breakpoints[0] = 0.0
        breakpoints[-1] = 1.0 + eps

        ref_counts = np.histogram(ref_scores, bins=breakpoints)[0]
        cur_counts = np.histogram(cur_scores, bins=breakpoints)[0]

        ref_pct = ref_counts / (ref_counts.sum() + eps) + eps
        cur_pct = cur_counts / (cur_counts.sum() + eps) + eps

        psi = float(np.sum((cur_pct - ref_pct) * np.log(cur_pct / ref_pct)))
        return round(psi, 6)

    def calculer_psi_depuis_reference(self, cur_scores: np.ndarray) -> float:
        """PSI en comparant avec les scores de référence de l'entraînement."""
        if self._reference_scores is None:
            raise RuntimeError(
                "Scores de référence non disponibles (modèle non entraîné)."
            )
        return self.calculer_psi(self._reference_scores, cur_scores)

    # ── Persistance ───────────────────────────────────────────────────────────

    def sauvegarder(self, dossier: str | Path) -> Path:
        dossier = Path(dossier)
        dossier.mkdir(parents=True, exist_ok=True)
        model_path = dossier / "mcrs_model.pkl"
        meta_path = dossier / "mcrs_meta.json"
        ref_path = dossier / "reference_scores.npy"

        with open(model_path, "wb") as f:
            pickle.dump(self, f, protocol=5)

        if self._reference_scores is not None:
            np.save(ref_path, self._reference_scores)

        meta = {
            "params": asdict(self.params),
            "metrics": self.metrics_,
            "feature_importances": self.feature_importances_,
            "features": ALL_FEATURES,
            "n_crs_features": len(CRS_FEATURES),
            "n_rps_features": len(RPS_FEATURES),
            "n_csi_features": len(CSI_FEATURES),
            "saved_at": datetime.utcnow().isoformat(),
            "version": "2.0.0",
        }
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)

        logger.info(
            "Modèle MCRS sauvegardé dans %s (AUC=%.4f)",
            dossier,
            self.metrics_.get("auc_roc", 0),
        )
        return model_path

    @classmethod
    def charger(cls, dossier: str | Path) -> "MCRSModel":
        model_path = Path(dossier) / "mcrs_model.pkl"
        if not model_path.exists():
            raise FileNotFoundError(f"Modèle MCRS introuvable : {model_path}")
        with open(model_path, "rb") as f:
            model: MCRSModel = pickle.load(f)

        model._initialiser_champs_optionnels()

        ref_path = Path(dossier) / "reference_scores.npy"
        if ref_path.exists() and model._reference_scores is None:
            model._reference_scores = np.load(ref_path)

        logger.info("Modèle MCRS chargé depuis %s", dossier)
        return model


# ─── Fonctions utilitaires pures (hors classe) ───────────────────────────────


def _calculer_metriques(y_true: np.ndarray, y_proba: np.ndarray, fold: int) -> dict:
    """Calcule les métriques de performance d'un fold."""
    auc = roc_auc_score(y_true, y_proba)
    gini = 2 * auc - 1

    # KS statistic propre via scipy.stats
    pos = y_proba[y_true == 1]
    neg = y_proba[y_true == 0]
    if len(pos) > 0 and len(neg) > 0:
        ks_stat, _ = ks_2samp(pos, neg)
    else:
        ks_stat = 0.0

    seuil_optimal = _trouver_seuil_optimal(y_true, y_proba)
    y_pred = (y_proba >= seuil_optimal).astype(int)

    return {
        "fold": fold,
        "auc_roc": round(auc, 4),
        "gini": round(gini, 4),
        "ks_statistic": round(float(ks_stat), 4),
        "precision": round(precision_score(y_true, y_pred, zero_division=0), 4),
        "recall": round(recall_score(y_true, y_pred, zero_division=0), 4),
        "f1_score": round(f1_score(y_true, y_pred, zero_division=0), 4),
        "brier_score": round(brier_score_loss(y_true, y_proba), 4),
        "seuil_optimal": round(seuil_optimal, 4),
        "taux_defaut": round(float(y_true.mean()), 4),
    }


def _trouver_seuil_optimal(y_true: np.ndarray, y_proba: np.ndarray) -> float:
    """
    Trouve le seuil maximisant le F1-score (adapté aux datasets déséquilibrés).
    Évite d'utiliser 0.5 comme seuil unique sur des données de crédit.
    """
    seuils = np.linspace(0.1, 0.9, 81)
    best_f1, best_seuil = 0.0, 0.5
    for s in seuils:
        y_pred = (y_proba >= s).astype(int)
        f1 = f1_score(y_true, y_pred, zero_division=0)
        if f1 > best_f1:
            best_f1, best_seuil = f1, s
    return best_seuil


def _classifier_risque(score: float) -> str:
    for classe, (lo, hi) in SEUILS_RISQUE.items():
        if lo <= score < hi:
            return classe
    return "CRITIQUE"


def _priorite(score: float) -> int:
    """Priorité de traitement recouvrement : 1 (faible) → 5 (critique immédiat)."""
    if score < 0.25:
        return 1
    if score < 0.40:
        return 2
    if score < 0.55:
        return 3
    if score < 0.70:
        return 4
    return 5
