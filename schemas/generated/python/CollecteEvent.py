    from __future__ import annotations
from datetime import date
from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field
from typing import Optional, Union, Any

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/CollecteEvent.avsc
    # Namespace Avro : cm.imf.pipeline.events
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : 2026-05-25 15:08:00
    # ─────────────────────────────────────────────────────────────────────────────


class CanalPaiement(str, Enum):
    MOBILE_MONEY_MTN = "MOBILE_MONEY_MTN"
    MOBILE_MONEY_ORANGE = "MOBILE_MONEY_ORANGE"
    ESPECES = "ESPECES"
    VIREMENT_BANCAIRE = "VIREMENT_BANCAIRE"
    CHEQUE = "CHEQUE"

class StatutCollecte(str, Enum):
    CONFIRMEE = "CONFIRMEE"
    EN_ATTENTE = "EN_ATTENTE"
    REJETEE = "REJETEE"
    ANNULEE = "ANNULEE"


class CollecteEvent(BaseModel):
    """Événement émis lors de la confirmation d'une collecte terrain par un agent."""
    event_id: str = Field(..., description="UUID unique de l'événement")
collecte_id: str = Field(..., description="ID externe de la collecte")
client_id_externe: str = Field(..., description="ID externe du client")
agent_id: str = Field(..., description="ID de l'agent collecteur")
agence_id: str = Field(..., description="ID de l'agence")
imf_id: int = Field(..., description="ID de l'IMF (multi-tenant)")
region_id: str = Field(..., description="Région camerounaise (REG01-REG10)")
montant: float = Field(..., description="Montant collecté en FCFA")
canal: CanalPaiement = Field(..., description="Canal de paiement utilisé")
statut: StatutCollecte
reference_momo: Optional[str] = Field(None, description="Référence transaction mobile money")
latitude: Optional[float] = None
longitude: Optional[float] = None
timestamp_ms: datetime = Field(..., description="Horodatage UTC")
date_collecte: date = Field(..., description="Date locale Cameroun")

    class Config:
        use_enum_values = True
        json_encoders = {
            "datetime": lambda v: int(v.timestamp() * 1000),
        }
