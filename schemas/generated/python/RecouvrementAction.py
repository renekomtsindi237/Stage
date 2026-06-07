    from __future__ import annotations
from datetime import date
from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field
from typing import Optional, Union, Any

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/RecouvrementAction.avsc
    # Namespace Avro : cm.imf.pipeline.events
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : 2026-05-25 15:08:00
    # ─────────────────────────────────────────────────────────────────────────────


class TypeAction(str, Enum):
    AUCUNE = "AUCUNE"
    RELANCE_PREVENTIVE = "RELANCE_PREVENTIVE"
    VISITE_TERRAIN = "VISITE_TERRAIN"
    MISE_EN_DEMEURE = "MISE_EN_DEMEURE"

class StatutAction(str, Enum):
    RECOMMANDEE = "RECOMMANDEE"
    PLANIFIEE = "PLANIFIEE"
    EN_COURS = "EN_COURS"
    EFFECTUEE = "EFFECTUEE"
    ABANDONNEE = "ABANDONNEE"

class ResultatAction(str, Enum):
    REMBOURSE = "REMBOURSE"
    PROMESSE_PAIEMENT = "PROMESSE_PAIEMENT"
    SANS_EFFET = "SANS_EFFET"
    INJOIGNABLE = "INJOIGNABLE"


class RecouvrementAction(BaseModel):
    """Action de recouvrement recommandée par l'agent Q-Learning et confirmée par l'agent terrain."""
    event_id: str
action_id: str
client_id_externe: str
imf_id: int
agence_id: str
agent_id: Optional[str] = None
action_type: TypeAction
statut: StatutAction
score_mcrs: float
jours_retard: int
montant_encours: float
resultat: Optional[ResultatAction] = None
montant_recupere: Optional[float] = None
source_recommandation: str = Field(..., description="Q-LEARNING | RULE_BASED | MANUEL")
timestamp_ms: datetime
date_planifiee: Optional[date] = None

    class Config:
        use_enum_values = True
        json_encoders = {
            "datetime": lambda v: int(v.timestamp() * 1000),
        }
