    from __future__ import annotations
from datetime import date
from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field
from typing import Optional, Union, Any

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/CreanceEvenement.avsc
    # Namespace Avro : cm.imf.pipeline.events
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : 2026-05-25 15:08:00
    # ─────────────────────────────────────────────────────────────────────────────


class TypeEvenementCreance(str, Enum):
    CREATION = "CREATION"
    PAIEMENT_ECHEANCE = "PAIEMENT_ECHEANCE"
    PAIEMENT_PARTIEL = "PAIEMENT_PARTIEL"
    RETARD_DETECTE = "RETARD_DETECTE"
    REECHELONNEMENT = "REECHELONNEMENT"
    PASSAGE_COBAC_B = "PASSAGE_COBAC_B"
    PASSAGE_COBAC_C = "PASSAGE_COBAC_C"
    PASSAGE_COBAC_D = "PASSAGE_COBAC_D"
    PASSAGE_COBAC_E = "PASSAGE_COBAC_E"
    REMBOURSEMENT_TOTAL = "REMBOURSEMENT_TOTAL"
    PERTE_DEFINITIVE = "PERTE_DEFINITIVE"


class CreanceEvenement(BaseModel):
    """Événement sur une créance : création, paiement, retard, restructuration."""
    event_id: str
creance_id: str
client_id_externe: str
imf_id: int
agence_id: str
type_evenement: TypeEvenementCreance
montant_encours: float = Field(..., description="Capital restant dû en FCFA")
montant_evenement: float = Field(..., description="Montant du paiement ou de la provision")
jours_retard: int
cobac_classe_avant: str
cobac_classe_apres: str
taux_provision: float
timestamp_ms: datetime
date_evenement: date

    class Config:
        use_enum_values = True
        json_encoders = {
            "datetime": lambda v: int(v.timestamp() * 1000),
        }
