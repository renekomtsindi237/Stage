"""
API FastAPI servant le modèle MCRS en temps réel.

Utilisé par le backend Spring Boot via des appels HTTP internes (appel pipeline)
pour obtenir un score MCRS immédiat à la demande (ex : ouverture d'un dossier).

Endpoints
---------
POST /score/single          — score d'un client unique
POST /score/batch           — score d'un batch de clients (JSON)
GET  /model/info            — informations sur le modèle actif
GET  /model/health          — healthcheck (200 = modèle chargé et prêt)
POST /model/drift           — calcul PSI entre scores de référence et scores courants

Démarrage
---------
uvicorn pipeline.src.ml.api_service:app --host 0.0.0.0 --port 8090 --workers 2
"""

from __future__ import annotations

import json
import logging
import os
import secrets
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator
from sklearn.metrics import (
    brier_score_loss,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)

from pipeline.src.ml.mcrs_model import (
    ALL_FEATURES,
    FEATURE_DEFAULTS,
    MCRSModel,
    ScoreResult,
)

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

# ─── Chemins ─────────────────────────────────────────────────────────────────

MODEL_DIR = Path(os.getenv("MCRS_MODEL_DIR", "/ml/models/mcrs/champion"))
MANUAL_REVIEW_CONFIG_PATH = (
    Path(__file__).resolve().parents[2] / "manual_review_config.json"
)
HUMAN_REVIEWS_LOG = (
    Path(__file__).resolve().parents[2] / "result" / "human_reviews.jsonl"
)

# ─── État global du service ───────────────────────────────────────────────────

_model: MCRSModel | None = None
_model_loaded_at: float = 0.0
_model_meta: dict = {}


def _charger_modele(dossier: Path = MODEL_DIR) -> MCRSModel:
    global _model, _model_loaded_at, _model_meta
    logger.info("Chargement du modèle MCRS depuis %s", dossier)
    model = MCRSModel.charger(dossier)
    meta_path = dossier / "mcrs_meta.json"
    if meta_path.exists():
        import json

        with open(meta_path, encoding="utf-8") as f:
            _model_meta = json.load(f)
    _model = model
    _model_loaded_at = time.time()
    logger.info(
        "Modèle MCRS chargé (AUC=%.4f)",
        _model_meta.get("metrics", {}).get("auc_roc", 0),
    )
    return model


# Manual review mode: 'critical' (default) | 'always' | 'none'
_manual_review_mode: str = "critical"


def _load_manual_review_config() -> None:
    global _manual_review_mode
    try:
        if MANUAL_REVIEW_CONFIG_PATH.exists():
            with open(MANUAL_REVIEW_CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
            mode = cfg.get("mode", "critical")
            if mode in ("critical", "always", "none"):
                _manual_review_mode = mode
    except Exception:
        logger.warning(
            "Impossible de charger la configuration de revue humaine, mode par défaut utilisé"
        )


def _save_manual_review_config(mode: str) -> None:
    try:
        MANUAL_REVIEW_CONFIG_PATH.write_text(
            json.dumps({"mode": mode}, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    except Exception:
        logger.exception("Impossible de sauvegarder la configuration de revue humaine")


def _get_model() -> MCRSModel:
    if _model is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Modèle MCRS non chargé. Réessayez dans quelques instants.",
        )
    return _model


# ─── Authentification interne ────────────────────────────────────────────────
#
# /score/single et /score/batch étaient jusqu'ici accessibles sans aucune
# authentification à quiconque atteint le port 8090 (network_mode: host sur le
# VPS). CORS ne protège que les appels navigateur, pas les appels
# serveur-à-serveur (Blucash, dag_ml_scoring). MCRS_INTERNAL_API_KEY est
# partagée avec le backend Spring Boot (MlScoringClient) et Blucash
# (McrsScoringClient) — absente : mode dégradé ouvert, pour ne rien casser
# tant qu'elle n'est pas déployée partout.
INTERNAL_API_KEY = os.getenv("MCRS_INTERNAL_API_KEY")


def _verifier_cle_interne(x_internal_key: str | None = Header(default=None)) -> None:
    if not INTERNAL_API_KEY:
        return
    if not x_internal_key or not secrets.compare_digest(
        x_internal_key, INTERNAL_API_KEY
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="En-tête X-Internal-Key manquant ou invalide.",
        )


# ─── Lifespan ─────────────────────────────────────────────────────────────────


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        _charger_modele()
    except FileNotFoundError:
        logger.warning(
            "Modèle MCRS non trouvé dans %s — service démarré sans modèle.", MODEL_DIR
        )
    if not INTERNAL_API_KEY:
        logger.warning(
            "MCRS_INTERNAL_API_KEY non définie — /score/single et /score/batch "
            "restent accessibles sans authentification."
        )
    yield
    logger.info("API MCRS arrêtée.")


# ─── Application ──────────────────────────────────────────────────────────────

app = FastAPI(
    title="MCRS Scoring API",
    description=(
        "API de scoring **Multi-Criteria Recovery Scoring (MCRS)** "
        "pour institutions de microfinance du Cameroun.\n\n"
        "Fournit des scores en temps réel (`/score/single`) et en batch (`/score/batch`), "
        "ainsi que des outils de monitoring de drift, de revue humaine et de pilotage "
        "des seuils opérationnels par région.\n\n"
        "**Conformité** : Loi n° 2024/017 Cameroun (RGPD), Règlement COBAC 01/02 CEMAC."
    ),
    version="2.0.0",
    docs_url="/model/docs",
    openapi_url="/model/openapi.json",
    redoc_url="/model/redoc",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],  # Spring Boot uniquement
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

# ─── Middleware logging ───────────────────────────────────────────────────────


@app.middleware("http")
async def log_requests(request: Request, call_next):
    t0 = time.perf_counter()
    response = await call_next(request)
    ms = round((time.perf_counter() - t0) * 1000, 1)
    logger.info(
        "%s %s — %d — %.1f ms",
        request.method,
        request.url.path,
        response.status_code,
        ms,
    )
    return response


# ─── Schémas de requête / réponse ────────────────────────────────────────────


class FeatureInput(BaseModel):
    """
    Features d'un client pour le scoring MCRS.
    Toutes les features sont optionnelles — les manquantes sont imputées
    avec les médianes sectorielles (voir FEATURE_DEFAULTS dans mcrs_model.py).
    """

    client_id_externe: str = Field(..., description="Identifiant client côté CBS/app")
    imf_code: str = Field(..., description="Code IMF (multi-tenant)")
    region_id: str | None = Field(
        default=None, description="Code région administratif (optionnel)"
    )
    region_name: str | None = Field(
        default=None, description="Nom de région Cameroon (optionnel)"
    )

    # CRS
    nb_collectes_12m: float | None = None
    regularite_collecte_pct: float | None = None
    tendance_collecte_3m: float | None = None
    montant_moy_collecte: float | None = None
    ecart_type_collecte: float | None = None
    nb_cycles_manques_12m: float | None = None
    montant_total_collectes_12m: float | None = None

    # RPS
    taux_remboursement_pct: float | None = None
    jours_retard_moyen: float | None = None
    jours_retard_max: float | None = None
    nb_incidents_paiement: float | None = None
    montant_impaye_courant: float | None = None
    nb_remboursements_12m: float | None = None
    classe_risque_cobac_encode: float | None = None  # 0=A, 1=B, 2=C, 3=D, 4=E

    # CSI
    revenu_mensuel_estime: float | None = None
    anciennete_client_jours: float | None = None
    nb_produits_actifs: float | None = None
    ratio_collecte_credit: float | None = None
    capacite_remboursement: float | None = None
    indice_resilience: float | None = None
    est_producteur: float | None = None  # 1 = producteur/vendeur, 0 = consommateur net
    prix_produit_principal_moy: float | None = None
    volatilite_prix_produit: float | None = None
    tendance_prix_30j: float | None = None
    inflation_mensuelle_moy: float | None = None
    taux_directeur_beac: float | None = None
    precipitation_moy_mm: float | None = None
    indice_secheresse: float | None = None
    nb_evenements_negatifs: float | None = None

    @field_validator("regularite_collecte_pct")
    @classmethod
    def valider_regularite(cls, v: float | None) -> float | None:
        if v is not None and not (0 <= v <= 100):
            raise ValueError("regularite_collecte_pct doit être entre 0 et 100")
        return v

    @field_validator("taux_remboursement_pct")
    @classmethod
    def valider_taux_rembours(cls, v: float | None) -> float | None:
        if v is not None and not (0 <= v <= 100):
            raise ValueError("taux_remboursement_pct doit être entre 0 et 100")
        return v

    def to_dict(self) -> dict[str, Any]:
        d = self.model_dump()
        # Imputer les None avec les défauts
        for feat in ALL_FEATURES:
            if d.get(feat) is None:
                d[feat] = FEATURE_DEFAULTS.get(feat, 0.0)
        return d


class ScoreResponse(BaseModel):
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
    priorite_recouvrement: int
    region_id: str | None = None
    region_name: str | None = None
    seuil_operationnel: float | None = None
    revue_humaine_requise: bool = False
    decision_operationnelle: str = "AUTOMATIQUE"
    shap_values: dict[str, float]
    scored_at: str


class BatchScoreRequest(BaseModel):
    clients: list[FeatureInput] = Field(..., min_length=1, max_length=2000)


class BatchScoreResponse(BaseModel):
    total: int
    scores: list[ScoreResponse]
    duration_ms: float


class DriftRequest(BaseModel):
    current_scores: list[float] = Field(..., min_length=10)


class DriftResponse(BaseModel):
    psi: float
    drift_detecte: bool
    interpretation: str


class RegionalThresholdsUpdate(BaseModel):
    thresholds: dict[str, float] = Field(..., min_length=1)


class MonitoringRecord(FeatureInput):
    label_defaut_90j: int = Field(..., ge=0, le=1)


class MonitoringBatchRequest(BaseModel):
    clients: list[MonitoringRecord] = Field(..., min_length=1, max_length=5000)


class ReviewSubmission(BaseModel):
    client_id_externe: str
    imf_code: str
    reviewer_id: str
    decision_operationnelle: str
    comment: str | None = None
    score_mcrs: float | None = None
    scored_at: str | None = None


class ManualReviewConfig(BaseModel):
    mode: str = Field(..., description="'critical'|'always'|'none'")


# ─── Endpoints ────────────────────────────────────────────────────────────────


@app.get("/model/health", tags=["Modèle"])
def healthcheck():
    """
    Healthcheck Docker-compatible : retourne toujours HTTP 200.
    - status=ok       : modèle chargé et prêt
    - status=degraded : service UP mais aucun modèle en mémoire
    Le scoring retournera une erreur métier si model_loaded=false.
    """
    if _model is None:
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content={
                "status": "degraded",
                "model_loaded": False,
                "detail": "Aucun modèle champion trouvé — scoring indisponible",
            },
        )
    return {
        "status": "ok",
        "model_loaded": True,
        "loaded_at": _model_loaded_at,
        "auc_roc": _model_meta.get("metrics", {}).get("auc_roc"),
        "version": _model_meta.get("version", "unknown"),
    }


@app.get("/model/info", tags=["Modèle"])
def model_info():
    """Retourne les métadonnées du modèle actif."""
    model = _get_model()
    return {
        "features": ALL_FEATURES,
        "n_features": len(ALL_FEATURES),
        "params": _model_meta.get("params", {}),
        "metrics": _model_meta.get("metrics", {}),
        "feature_importances": model.feature_importances_,
        "saved_at": _model_meta.get("saved_at"),
        "version": _model_meta.get("version", "unknown"),
    }


@app.post(
    "/score/single",
    response_model=ScoreResponse,
    tags=["Scoring"],
    dependencies=[Depends(_verifier_cle_interne)],
)
def score_single(input_data: FeatureInput):
    """
    Score un client unique.

    Utilisé par le backend Spring Boot lors de l'affichage d'un dossier de
    recouvrement, pour obtenir un score MCRS actualisé immédiatement.
    """
    model = _get_model()
    try:
        result: ScoreResult = model.predict_single(input_data.to_dict())
    except Exception as exc:
        logger.exception("Erreur scoring client %s", input_data.client_id_externe)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Erreur lors du scoring : {exc}",
        ) from exc

    # Enforce manual review mode
    forced = (_manual_review_mode == "always") or (
        _manual_review_mode == "critical" and result.revue_humaine_requise
    )
    if forced:
        result.revue_humaine_requise = True
        result.decision_operationnelle = "BLOQUE_POUR_REVUE"

    return ScoreResponse(**result.to_dict())


@app.post(
    "/score/batch",
    response_model=BatchScoreResponse,
    tags=["Scoring"],
    dependencies=[Depends(_verifier_cle_interne)],
)
def score_batch(request: BatchScoreRequest):
    """
    Score un batch de clients.

    Utilisé par dag_ml_scoring (Airflow) pour le scoring journalier de l'ensemble
    du portefeuille. Les clients manquant de features sont scorés avec les valeurs
    médianes sectorielles (imputation transparente, tracée dans les logs).
    """
    model = _get_model()
    t0 = time.perf_counter()

    import pandas as pd

    rows = [c.to_dict() for c in request.clients]
    df = pd.DataFrame(rows)

    try:
        results: list[ScoreResult] = model.predict_batch(df)
    except Exception as exc:
        logger.exception("Erreur scoring batch (%d clients)", len(request.clients))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Erreur lors du scoring batch : {exc}",
        ) from exc

    duration_ms = round((time.perf_counter() - t0) * 1000, 1)
    logger.info("Batch scoré : %d clients en %.0f ms", len(results), duration_ms)

    # Apply manual review enforcement per-result
    responses = []
    for r in results:
        forced = (_manual_review_mode == "always") or (
            _manual_review_mode == "critical" and r.revue_humaine_requise
        )
        if forced:
            r.revue_humaine_requise = True
            r.decision_operationnelle = "BLOQUE_POUR_REVUE"
        responses.append(ScoreResponse(**r.to_dict()))

    return BatchScoreResponse(
        total=len(results),
        scores=responses,
        duration_ms=duration_ms,
    )


@app.post("/model/drift", response_model=DriftResponse, tags=["Modèle"])
def calculer_drift(request: DriftRequest):
    """
    Calcule le PSI entre les scores courants et les scores de référence du modèle.

    Appelé par dag_ml_scoring après le scoring journalier pour détecter un drift
    de distribution nécessitant un retraining.
    """
    model = _get_model()
    try:
        cur = np.array(request.current_scores, dtype=float)
        psi = model.calculer_psi_depuis_reference(cur)
    except RuntimeError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
        ) from exc

    if psi < 0.10:
        interp = "Distribution stable — aucune action requise"
    elif psi < 0.20:
        interp = "Drift modéré — surveiller l'évolution"
    else:
        interp = "Drift significatif — retraining recommandé"

    return DriftResponse(
        psi=round(psi, 6),
        drift_detecte=psi >= 0.20,
        interpretation=interp,
    )


@app.post("/model/monitoring", tags=["Modèle"])
def monitoring_batch(request: MonitoringBatchRequest):
    """
    Calcule un rapport de monitoring en production sur un batch labellisé.

    Retourne des métriques globales, par région et par quartile de capacité de remboursement.
    """
    model = _get_model()
    rows = [c.to_dict() for c in request.clients]
    labels = np.array([c.label_defaut_90j for c in request.clients], dtype=int)
    df = pd.DataFrame(rows)
    scores: list[ScoreResponse] = []
    predictions: list[float] = []

    for row in rows:
        result: ScoreResult = model.predict_single(row)
        predictions.append(float(result.probabilite_defaut_90j))
        scores.append(ScoreResponse(**result.to_dict()))

    proba = np.array(predictions, dtype=float)
    pred = (proba >= 0.5).astype(int)

    def _metrics(
        y_true: np.ndarray, y_pred: np.ndarray, y_proba: np.ndarray
    ) -> dict[str, float | None]:
        try:
            auc = float(round(roc_auc_score(y_true, y_proba), 4))
        except Exception:
            auc = None
        return {
            "auc_roc": auc,
            "brier": float(round(brier_score_loss(y_true, y_proba), 4)),
            "f1": float(round(f1_score(y_true, y_pred), 4)),
            "precision": float(round(precision_score(y_true, y_pred), 4)),
            "recall": float(round(recall_score(y_true, y_pred), 4)),
        }

    overall = _metrics(labels, pred, proba)
    psi = None
    drift_detecte = False
    interpretation = "PSI non calculé"
    try:
        psi = float(round(model.calculer_psi_depuis_reference(proba), 6))
        drift_detecte = psi >= 0.20
        interpretation = (
            "Distribution stable — aucune action requise"
            if psi < 0.10
            else (
                "Drift modéré — surveiller l'évolution"
                if psi < 0.20
                else "Drift significatif — recalibration / retraining recommandé"
            )
        )
    except Exception:
        pass

    reports: dict[str, Any] = {
        "overall": overall,
        "psi": psi,
        "drift_detecte": drift_detecte,
        "interpretation": interpretation,
        "n": int(len(df)),
        "review_rate": float(
            round(float(np.mean([s.revue_humaine_requise for s in scores])), 4)
        ),
    }

    if "region_name" in df.columns or "region_id" in df.columns:
        region_col = "region_name" if df["region_name"].notna().any() else "region_id"
        grouped = []
        for region, sub in df.groupby(region_col, dropna=False):
            idx = sub.index.to_numpy()
            grouped.append(
                {
                    "region": None if pd.isna(region) else str(region),
                    "n": int(len(sub)),
                    **_metrics(labels[idx], pred[idx], proba[idx]),
                }
            )
        reports["by_region"] = grouped

    if "capacite_remboursement" in df.columns:
        tmp = df.copy()
        tmp["cap_q"] = pd.qcut(
            tmp["capacite_remboursement"].fillna(0), q=4, duplicates="drop"
        ).astype(str)
        grouped = []
        for q, sub in tmp.groupby("cap_q"):
            idx = sub.index.to_numpy()
            grouped.append(
                {
                    "cap_group": q,
                    "n": int(len(sub)),
                    **_metrics(labels[idx], pred[idx], proba[idx]),
                }
            )
        reports["by_capacite_quartile"] = grouped

    return {"summary": reports, "scores": scores}


@app.put("/model/thresholds/region", tags=["Configuration"])
def update_regional_thresholds(update: RegionalThresholdsUpdate):
    """Met à jour les seuils opérationnels par région pour le pilotage contrôlé."""
    model = _get_model()
    try:
        model.set_region_thresholds(update.thresholds, persist=True)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
        ) from exc
    return {"status": "ok", "thresholds": update.thresholds}


@app.put("/config/manual_review", tags=["Configuration"])
def set_manual_review_config(cfg: ManualReviewConfig):
    """Met à jour le mode global de revue humaine: 'critical'|'always'|'none'."""
    global _manual_review_mode
    mode = cfg.mode
    if mode not in ("critical", "always", "none"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="Mode invalide"
        )
    _manual_review_mode = mode
    try:
        _save_manual_review_config(mode)
    except Exception:
        logger.exception("Impossible de sauvegarder le mode de revue humaine")
    return {"status": "ok", "mode": _manual_review_mode}


@app.post("/review/submit", tags=["Revue"])
def submit_review(sub: ReviewSubmission):
    """Soumet le verdict d'une revue humaine; enregistré pour traçabilité et réentraînement."""
    try:
        HUMAN_REVIEWS_LOG.parent.mkdir(parents=True, exist_ok=True)
        record = sub.model_dump()
        record["received_at"] = time.time()
        with open(HUMAN_REVIEWS_LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
        logger.info(
            "Revue humaine soumise: %s %s by %s",
            sub.client_id_externe,
            sub.imf_code,
            sub.reviewer_id,
        )
        return {"status": "ok"}
    except Exception as exc:
        logger.exception("Erreur en enregistrant la revue humaine")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)
        ) from exc


@app.post("/model/reload", tags=["Modèle"])
def reload_model():
    """Recharge le modèle depuis le disque (après promotion d'un challenger)."""
    try:
        _charger_modele()
        return {"status": "ok", "message": "Modèle rechargé avec succès"}
    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Modèle introuvable : {exc}",
        ) from exc


# ─── Gestionnaire d'erreurs global ───────────────────────────────────────────


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception("Erreur non gérée : %s %s", request.method, request.url)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": f"Erreur interne : {type(exc).__name__}"},
    )
