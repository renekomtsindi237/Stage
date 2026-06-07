    from __future__ import annotations
from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field
from typing import Optional, Union, Any

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/ScoringRequest.avsc
    # Namespace Avro : cm.imf.pipeline.events
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : 2026-05-25 15:08:00
    # ─────────────────────────────────────────────────────────────────────────────


class ScoringRequest(BaseModel):
    """Demande de scoring MCRS envoyée au service FastAPI ML via Kafka."""
    request_id: str = Field(..., description="UUID de la demande (corrélation réponse)")
client_id_externe: str
imf_id: int
region_id: str
regularite_collecte_pct: float = 0.0
nb_collectes_30j: float = 0.0
montant_moyen_collecte: float = 0.0
tendance_collecte_30j: float = 0.0
coefficient_variation_collecte: float = 0.0
nb_semaines_sans_collecte: float = 0.0
rang_collecte_agence: float = 0.5
jours_retard_actuel: float = 0.0
nb_incidents_paiement_12m: float = 0.0
taux_remboursement_historique: float = 0.5
ratio_creance_revenus: float = 0.0
nb_reechelonnements: float = 0.0
score_rps_precedent: float = 0.5
prix_moyen_30j: float = 0.0
volatilite_prix_30j: float = 0.0
saisonnalite_prix: float = 0.0
precipitations_30j: float = 0.0
indice_secheresse: float = 0.0
inflation: float = 3.0
taux_beac: float = 4.5
ipc: float = 100.0
chomage: float = 3.5
indice_resilience: float = 0.5
capacite_remboursement: float = 1.0
ratio_collecte_credit: float = 0.0
score_diversification_produits: float = 0.5
risque_regional: float = 1.12
taux_penetration_mobile: float = 0.5
zone_agroclimatique: float = 1.0
saison_recolte_active: float = 0.0
timestamp_ms: datetime

    class Config:
        use_enum_values = True
        json_encoders = {
            "datetime": lambda v: int(v.timestamp() * 1000),
        }
