    from __future__ import annotations
from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field
from typing import Optional, Union, Any

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/AlerteRisque.avsc
    # Namespace Avro : cm.imf.pipeline.events
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : 2026-05-25 15:08:00
    # ─────────────────────────────────────────────────────────────────────────────


class NiveauRisque(str, Enum):
    FAIBLE = "FAIBLE"
    MODERE = "MODERE"
    ELEVE = "ELEVE"
    CRITIQUE = "CRITIQUE"

class ClasseCOBAC(str, Enum):
    A = "A"
    B = "B"
    C = "C"
    D = "D"
    E = "E"

class ActionRecouvrement(str, Enum):
    AUCUNE = "AUCUNE"
    RELANCE_PREVENTIVE = "RELANCE_PREVENTIVE"
    VISITE_TERRAIN = "VISITE_TERRAIN"
    MISE_EN_DEMEURE = "MISE_EN_DEMEURE"


class AlerteRisque(BaseModel):
    """Alerte émise lorsque le score MCRS d'un client franchit un seuil critique."""
    event_id: str
alerte_id: str = Field(..., description="UUID de l'alerte")
client_id_externe: str
imf_id: int
agence_id: str
region_id: str
score_mcrs: float = Field(..., description="Score MCRS [0,1] — plus élevé = plus risqué")
score_crs: float = Field(..., description="Collection Reliability Score")
score_rps: float = Field(..., description="Recovery Prediction Score (XGBoost)")
score_csi: float = Field(..., description="Client Solvency Index")
niveau_risque: NiveauRisque
cobac_classe: ClasseCOBAC = Field(..., description="Classification COBAC EMF 01/02 CEMAC")
cobac_provision_taux: float = Field(..., description="Taux de provision COBAC (0.0-1.0)")
types_alertes: list[str] = Field(..., description="RISQUE_DEFAUT_IMMINENT, DETERIORATION_RAPIDE, BAISSE_COLLECTE_PERSISTANTE")
jours_retard: int
action_recommandee: ActionRecouvrement = Field(..., description="Action Q-Learning recommandée")
timestamp_ms: datetime
source: str = Field(..., description="dag_ml_scoring | api_ml | stream_flink")

    class Config:
        use_enum_values = True
        json_encoders = {
            "datetime": lambda v: int(v.timestamp() * 1000),
        }
