    from __future__ import annotations
from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field
from typing import Optional, Union, Any

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/ScoringResult.avsc
    # Namespace Avro : cm.imf.pipeline.events
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : 2026-05-25 15:08:00
    # ─────────────────────────────────────────────────────────────────────────────


class ScoringResult(BaseModel):
    """Résultat du scoring MCRS retourné par le service FastAPI ML."""
    request_id: str = Field(..., description="Corrélation avec ScoringRequest")
client_id_externe: str
imf_id: int
score_mcrs: float
score_crs: float
score_rps: float
score_csi: float
niveau_risque: str = Field(..., description="FAIBLE | MODERE | ELEVE | CRITIQUE")
cobac_classe: str = Field(..., description="A | B | C | D | E")
cobac_provision_taux: float
alertes: list[str]
model_version: str = "1.0.0"
timestamp_ms: datetime
latence_ms: Optional[int] = None

    class Config:
        use_enum_values = True
        json_encoders = {
            "datetime": lambda v: int(v.timestamp() * 1000),
        }
