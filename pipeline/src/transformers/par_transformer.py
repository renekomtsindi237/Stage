"""
par_transformer.py — Calcul des métriques PAR (Portfolio At Risk).

PAR30 = encours des prêts avec >= 30 jours de retard / encours total
PAR90 = encours des prêts avec >= 90 jours de retard / encours total

Produit les enregistrements à insérer dans dw.fact_remboursements.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Any

from config import settings
from exceptions import DataValidationError, TransformationError

logger = logging.getLogger(__name__)

PAR30_DAYS = settings.pipeline.par30_threshold_days
PAR90_DAYS = settings.pipeline.par90_threshold_days


@dataclass
class FactRemboursement:
    """Enregistrement cible pour dw.fact_remboursements."""

    id_pret: str
    id_agence: str          # nom_agence utilisé comme clé jusqu'à résolution dim_agence
    date_valeur: date
    montant_pret: Decimal
    montant_rembourse: Decimal
    solde_restant: Decimal
    statut_pret: str
    jours_retard: int
    encours_par30: Decimal
    encours_par90: Decimal


@dataclass
class PARSummary:
    """Résumé PAR pour une agence et une date."""

    nom_agence: str
    date_valeur: date
    encours_total: Decimal = Decimal("0")
    encours_par30: Decimal = Decimal("0")
    encours_par90: Decimal = Decimal("0")
    nb_prets: int = 0
    nb_prets_par30: int = 0
    nb_prets_par90: int = 0

    @property
    def taux_par30(self) -> Decimal:
        if self.encours_total == 0:
            return Decimal("0")
        return (self.encours_par30 / self.encours_total * 100).quantize(Decimal("0.01"))

    @property
    def taux_par90(self) -> Decimal:
        if self.encours_total == 0:
            return Decimal("0")
        return (self.encours_par90 / self.encours_total * 100).quantize(Decimal("0.01"))


def transform_prets_to_fact(
    prets: list[dict[str, Any]],
    date_valeur: date | None = None,
) -> list[FactRemboursement]:
    """
    Transforme une liste de prêts bruts en enregistrements fact_remboursements.

    Args:
        prets: Résultat de extract_all_prets_actifs().
        date_valeur: Date de référence (défaut : aujourd'hui).

    Returns:
        Liste de FactRemboursement prêts à charger.

    Raises:
        TransformationError: si une transformation de valeur échoue.
    """
    if date_valeur is None:
        date_valeur = date.today()

    step = "par_transformer.transform_prets_to_fact"
    result: list[FactRemboursement] = []
    skipped = 0

    for pret in prets:
        id_pret = pret.get("id_pret", "?")
        try:
            solde_restant = _to_decimal(pret["solde_restant"], "solde_restant", id_pret)
            jours_retard = int(pret["jours_retard"])

            if jours_retard < 0:
                raise DataValidationError(step, "jours_retard", jours_retard, "valeur négative interdite")

            encours_par30 = solde_restant if jours_retard >= PAR30_DAYS else Decimal("0")
            encours_par90 = solde_restant if jours_retard >= PAR90_DAYS else Decimal("0")

            result.append(FactRemboursement(
                id_pret=id_pret,
                id_agence=pret.get("nom_agence", "INCONNU"),
                date_valeur=date_valeur,
                montant_pret=_to_decimal(pret["montant_pret"], "montant_pret", id_pret),
                montant_rembourse=_to_decimal(pret["montant_rembourse"], "montant_rembourse", id_pret),
                solde_restant=solde_restant,
                statut_pret=str(pret.get("statut_pret", "")),
                jours_retard=jours_retard,
                encours_par30=encours_par30,
                encours_par90=encours_par90,
            ))
        except DataValidationError:
            skipped += 1
            logger.warning("Prêt %s ignoré — validation échouée", id_pret)
        except (KeyError, TypeError, ValueError) as exc:
            raise TransformationError(step, str(exc), record_id=id_pret) from exc

    if skipped:
        logger.warning("%d prêt(s) ignoré(s) lors de la transformation PAR", skipped)
    logger.info("Transformé %d prêts → %d enregistrements fact_remboursements", len(prets), len(result))
    return result


def compute_par_summary(facts: list[FactRemboursement]) -> dict[str, PARSummary]:
    """
    Agrège les FactRemboursement par agence pour produire le résumé PAR.

    Returns:
        Dictionnaire nom_agence → PARSummary.
    """
    summaries: dict[str, PARSummary] = {}

    for fact in facts:
        if fact.id_agence not in summaries:
            summaries[fact.id_agence] = PARSummary(
                nom_agence=fact.id_agence,
                date_valeur=fact.date_valeur,
            )
        s = summaries[fact.id_agence]
        s.encours_total += fact.solde_restant
        s.encours_par30 += fact.encours_par30
        s.encours_par90 += fact.encours_par90
        s.nb_prets += 1
        if fact.jours_retard >= PAR30_DAYS:
            s.nb_prets_par30 += 1
        if fact.jours_retard >= PAR90_DAYS:
            s.nb_prets_par90 += 1

    return summaries


def _to_decimal(value: Any, field_name: str, record_id: Any) -> Decimal:
    """Convertit une valeur en Decimal avec message d'erreur détaillé."""
    try:
        return Decimal(str(value))
    except (InvalidOperation, TypeError) as exc:
        raise DataValidationError(
            "par_transformer",
            field_name,
            value,
            f"conversion Decimal impossible : {exc}",
        ) from exc
