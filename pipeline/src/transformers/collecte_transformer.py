"""
collecte_transformer.py — Transformation des collectes terrain pour le DW.

Prépare les enregistrements à insérer dans dw.fact_collectes
en résolvant les clés de dimension (dim_date, dim_agence).
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from typing import Any

from exceptions import DataValidationError, TransformationError

logger = logging.getLogger(__name__)

CANAUX_VALIDES = {"ESPECES", "MTN_MOBILE_MONEY", "ORANGE_MONEY", "VIREMENT", "CHEQUE"}


@dataclass
class FactCollecte:
    """Enregistrement cible pour dw.fact_collectes."""

    source_id: int  # app.collectes_terrain.id — pour déduplication
    id_pret: str
    id_agence: str  # résolu à partir du nom_agence (dim_agence)
    date_valeur: date
    canal: str
    montant: Decimal
    nom_agent: str


def transform_collectes(
    collectes: list[dict[str, Any]],
    agence_map: dict[str, str] | None = None,
) -> list[FactCollecte]:
    """
    Transforme les collectes brutes en FactCollecte pour le DW.

    Args:
        collectes: Résultat de extract_collectes_confirmees().
        agence_map: Dictionnaire {nom_agence: id_agence} pour résoudre dim_agence.
                    Si None, nom_agence est utilisé directement comme id_agence.

    Returns:
        Liste de FactCollecte prêts à être chargés.

    Raises:
        TransformationError: si une erreur bloquante survient.
    """
    step = "collecte_transformer.transform_collectes"
    result: list[FactCollecte] = []
    skipped = 0

    for col in collectes:
        source_id = col.get("id", "?")
        try:
            # Validation du canal
            canal = str(col.get("canal", "")).strip().upper()
            if canal not in CANAUX_VALIDES:
                raise DataValidationError(
                    step, "canal", canal, f"valeur attendue parmi {CANAUX_VALIDES}"
                )

            # Résolution date
            date_collecte = col.get("date_collecte")
            if date_collecte is None:
                raise DataValidationError(
                    step, "date_collecte", date_collecte, "date absente"
                )

            if isinstance(date_collecte, datetime):
                date_val = date_collecte.date()
            elif isinstance(date_collecte, date):
                date_val = date_collecte
            else:
                date_val = datetime.fromisoformat(str(date_collecte)).date()

            # Montant
            montant = Decimal(str(col["montant"]))
            if montant <= 0:
                raise DataValidationError(
                    step, "montant", montant, "montant doit être > 0"
                )

            # Résolution agence
            nom_agence = str(col.get("nom_agence", "INCONNU"))
            id_agence = (agence_map or {}).get(nom_agence, nom_agence)

            result.append(
                FactCollecte(
                    source_id=int(source_id),
                    id_pret=str(col["id_pret"]),
                    id_agence=id_agence,
                    date_valeur=date_val,
                    canal=canal,
                    montant=montant,
                    nom_agent=str(col.get("nom_agent", "INCONNU")),
                )
            )

        except DataValidationError:
            skipped += 1
            logger.warning("Collecte id=%s ignorée — validation échouée", source_id)
        except (KeyError, TypeError, ValueError) as exc:
            raise TransformationError(step, str(exc), record_id=source_id) from exc

    if skipped:
        logger.warning("%d collecte(s) ignorée(s) lors de la transformation", skipped)
    logger.info(
        "Transformé %d collectes → %d enregistrements fact_collectes",
        len(collectes),
        len(result),
    )
    return result
