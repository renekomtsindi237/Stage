"""
IMF Pipeline — Service FastAPI ML (port 8090)
=============================================

Expose le modèle MCRS entraîné via HTTP pour le backend Spring Boot.
Appelé par MlScoringClient.java.

Endpoints :
  GET  /model/health        — santé du service + modèle chargé
  GET  /model/info          — métadonnées (version, AUC, features, config)
  POST /score               — score MCRS d'un client (features en JSON)
  POST /score/batch         — score batch (liste de clients)
  GET  /config              — configuration MCRS actuelle
  PUT  /config/weights      — mise à jour des poids à chaud (DSI)
  PUT  /config/thresholds   — mise à jour des seuils à chaud (DSI)
"""

from __future__ import annotations

import json
import logging
import sys
import time
from pathlib import Path
from typing import Any

import uvicorn
from fastapi import FastAPI, HTTPException, Security
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security.api_key import APIKeyHeader
from pydantic import BaseModel, Field, field_validator

# ─── Chemins ─────────────────────────────────────────────────────────────────
ROOT        = Path(__file__).parent.parent
CONFIG_PATH = Path(__file__).parent / "scoring_config.json"
MODEL_PATH  = ROOT / "result" / "supervised" / "model_xgboost.pkl"
METRICS_PATH = ROOT / "result" / "supervised" / "metrics.json"

sys.path.insert(0, str(Path(__file__).parent))

from compute_mcrs import MCRSScorer  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("api_ml")

# ─── App ──────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="IMF Pipeline — ML Scoring Service",
    description="Service MCRS (Multi-Criteria Recovery Scoring) — XGBoost + calibration isotonique",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "https://*.imf.cm"],
    allow_methods=["GET", "POST", "PUT"],
    allow_headers=["Content-Type", "X-Api-Key"],
)

# ─── Auth interne (clé API partagée avec le backend Java) ────────────────────
API_KEY_NAME   = "X-Api-Key"
api_key_header = APIKeyHeader(name=API_KEY_NAME, auto_error=False)

def _get_api_key(key: str | None = Security(api_key_header)) -> str | None:
    import os
    expected = os.getenv("ML_API_KEY", "imf-ml-internal-key")
    if key != expected:
        raise HTTPException(status_code=403, detail="Clé API invalide ou absente")
    return key

# ─── Chargement modèle au démarrage ──────────────────────────────────────────
_scorer: MCRSScorer | None = None
_start_time = time.time()

@app.on_event("startup")
def load_model() -> None:
    global _scorer
    log.info("Chargement du modèle MCRS depuis %s …", MODEL_PATH)
    try:
        _scorer = MCRSScorer(model_path=MODEL_PATH, config_path=CONFIG_PATH)
        log.info("Modèle chargé — %d features — AUC %.4f",
                 len(_scorer._features),
                 _read_metrics().get("test", {}).get("auc_roc", 0.0))
    except Exception as e:
        log.error("Échec chargement modèle : %s", e)
        _scorer = None


def _get_scorer() -> MCRSScorer:
    if _scorer is None:
        raise HTTPException(status_code=503, detail="Modèle non disponible — relancer le service")
    return _scorer


def _read_metrics() -> dict:
    try:
        return json.loads(METRICS_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


# ─── Schémas Pydantic ─────────────────────────────────────────────────────────

class ClientFeatures(BaseModel):
    client_id_externe:              str
    imf_id:                         int
    regularite_collecte_pct:        float = 0.0
    nb_collectes_30j:               float = 0.0
    montant_moyen_collecte:         float = 0.0
    tendance_collecte_30j:          float = 0.0
    coefficient_variation_collecte: float = 0.0
    nb_semaines_sans_collecte:      float = 0.0
    rang_collecte_agence:           float = 0.0
    jours_retard_actuel:            float = 0.0
    nb_incidents_paiement_12m:      float = 0.0
    taux_remboursement_historique:  float = 0.5
    ratio_creance_revenus:          float = 0.0
    nb_reechelonnements:            float = 0.0
    score_rps_precedent:            float = 0.5
    prix_moyen_30j:                 float = 0.0
    volatilite_prix_30j:            float = 0.0
    saisonnalite_prix:              float = 0.0
    precipitations_30j:             float = 0.0
    indice_secheresse:              float = 0.0
    inflation:                      float = 3.0
    taux_beac:                      float = 4.5
    ipc:                            float = 100.0
    chomage:                        float = 3.5
    indice_resilience:              float = 0.5
    capacite_remboursement:         float = 1.0
    ratio_collecte_credit:          float = 0.0
    score_diversification_produits: float = 0.5
    # Features contextuelles camerounaises
    risque_regional:                float = 1.12   # valeur nationale moyenne
    taux_penetration_mobile:        float = 0.50
    zone_agroclimatique:            float = 1.0    # 0=Sahel 1=Équatorial 2=Highlands 3=Côtier
    saison_recolte_active:          float = 0.0    # 0 ou 1


class BatchRequest(BaseModel):
    clients: list[ClientFeatures] = Field(..., min_length=1, max_length=5000)


class WeightsUpdate(BaseModel):
    w_crs: float = Field(..., gt=0, lt=1)
    w_rps: float = Field(..., gt=0, lt=1)
    w_csi: float = Field(..., gt=0, lt=1)

    @field_validator("w_csi")
    @classmethod
    def poids_somme_1(cls, w_csi: float, info: Any) -> float:
        data = info.data
        total = round(data.get("w_crs", 0) + data.get("w_rps", 0) + w_csi, 6)
        if abs(total - 1.0) > 1e-4:
            raise ValueError(f"Les poids doivent sommer à 1.0 — actuel : {total}")
        return w_csi


class ThresholdsUpdate(BaseModel):
    faible: float = Field(..., gt=0, lt=1)
    modere: float = Field(..., gt=0, lt=1)
    eleve:  float = Field(..., gt=0, lt=1)

    @field_validator("eleve")
    @classmethod
    def seuils_croissants(cls, eleve: float, info: Any) -> float:
        data = info.data
        if not (data.get("faible", 0) < data.get("modere", 0) < eleve):
            raise ValueError("Les seuils doivent être strictement croissants : faible < modere < eleve")
        return eleve


# ─── Endpoints ────────────────────────────────────────────────────────────────

@app.get("/model/health", tags=["Modèle"])
def model_health() -> dict:
    """Vérification santé — utilisé par MlScoringClient.java et le healthcheck Docker."""
    uptime = round(time.time() - _start_time, 1)
    return {
        "status":       "UP" if _scorer is not None else "DEGRADED",
        "model_loaded": _scorer is not None,
        "uptime_s":     uptime,
        "model_path":   str(MODEL_PATH),
        "config_path":  str(CONFIG_PATH),
    }


@app.get("/model/info", tags=["Modèle"])
def model_info(_key: str = Security(_get_api_key)) -> dict:
    """Métadonnées complètes du modèle actif (version, AUC, features, poids MCRS)."""
    scorer  = _get_scorer()
    metrics = _read_metrics()
    cfg     = scorer.config
    return {
        "version":          "1.0.0",
        "algorithme":       "XGBoost + calibration isotonique (Platt)",
        "n_features":       len(scorer._features),
        "features":         scorer._features,
        "trained_at":       metrics.get("generated_at", "inconnu"),
        "n_train":          metrics.get("n_train", 0),
        "n_test":           metrics.get("n_test", 0),
        "metriques_test":   metrics.get("test", {}),
        "cv_walk_forward":  metrics.get("cv_walk_forward", []),
        "mcrs_weights":     {"crs": cfg.w_crs, "rps": cfg.w_rps, "csi": cfg.w_csi},
        "risk_thresholds":  {
            "faible":   cfg.seuil_faible,
            "modere":   cfg.seuil_modere,
            "eleve":    cfg.seuil_eleve,
            "critique": 1.0,
        },
    }


@app.post("/score", tags=["Scoring"])
def score_client(
    client: ClientFeatures,
    _key: str = Security(_get_api_key),
) -> dict:
    """Score MCRS d'un client unique."""
    scorer = _get_scorer()
    row    = client.model_dump()
    result = scorer.score(row)
    return {
        "client_id_externe": client.client_id_externe,
        "imf_id":            client.imf_id,
        **result,
    }


@app.post("/score/batch", tags=["Scoring"])
def score_batch(
    batch: BatchRequest,
    _key: str = Security(_get_api_key),
) -> dict:
    """
    Score MCRS en batch — jusqu'à 5 000 clients par appel.
    Utilisé par le DAG dag_ml_scoring pour le scoring quotidien.
    """
    scorer  = _get_scorer()
    t0      = time.time()
    results = []

    for client in batch.clients:
        row    = client.model_dump()
        result = scorer.score(row)
        results.append({
            "client_id_externe": client.client_id_externe,
            "imf_id":            client.imf_id,
            **result,
        })

    elapsed = round(time.time() - t0, 3)
    log.info("Batch scoring : %d clients en %.3fs", len(results), elapsed)
    return {
        "n_scored":    len(results),
        "elapsed_s":   elapsed,
        "scores":      results,
    }


@app.get("/config", tags=["Configuration"])
def get_config(_key: str = Security(_get_api_key)) -> dict:
    """Retourne la configuration MCRS actuelle (poids, seuils, alertes)."""
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


@app.put("/config/weights", tags=["Configuration"])
def update_weights(
    update: WeightsUpdate,
    _key: str = Security(_get_api_key),
) -> dict:
    """
    Met à jour les poids MCRS à chaud — sans retrainement.
    Accessible uniquement au DSI (contrôle via clé API côté backend).
    """
    scorer = _get_scorer()
    scorer.update_weights(update.w_crs, update.w_rps, update.w_csi, save=True)
    log.info("Poids MCRS mis à jour : CRS=%.2f RPS=%.2f CSI=%.2f",
             update.w_crs, update.w_rps, update.w_csi)
    return {
        "message": "Poids mis à jour avec succès",
        "weights": {"crs": update.w_crs, "rps": update.w_rps, "csi": update.w_csi},
    }


@app.put("/config/thresholds", tags=["Configuration"])
def update_thresholds(
    update: ThresholdsUpdate,
    _key: str = Security(_get_api_key),
) -> dict:
    """Met à jour les seuils de classification du risque à chaud."""
    scorer = _get_scorer()
    scorer.update_thresholds(update.faible, update.modere, update.eleve, save=True)
    log.info("Seuils risque mis à jour : FAIBLE<%.2f MODERE<%.2f ELEVE<%.2f",
             update.faible, update.modere, update.eleve)
    return {
        "message":    "Seuils mis à jour avec succès",
        "thresholds": {"faible": update.faible, "modere": update.modere, "eleve": update.eleve},
    }


# ─── Point d'entrée ───────────────────────────────────────────────────────────

if __name__ == "__main__":
    uvicorn.run(
        "api_ml:app",
        host="0.0.0.0",
        port=8090,
        reload=False,
        log_level="info",
    )
