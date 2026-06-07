"""
pret_transformer.py — Transformations et enrichissement des données de prêts.

Calcule les indicateurs de risque conformément aux standards COBAC :
  - Jours de retard par rapport à la dernière échéance impayée
  - Classification PAR (Portfolio At Risk) : NORMAL / PAR30 / PAR90 / PAR180
  - Taux de recouvrement sur la période

Référence réglementaire : Règlement COBAC EMF/2002/01
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Optional

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Dataclasses de résultat
# ---------------------------------------------------------------------------

@dataclass
class PretTransformed:
    """Représentation enrichie d'un prêt après transformation."""
    id_pret: str
    reference: str
    id_client: int
    montant_initial: Decimal
    montant_restant: Decimal
    taux_interet: Decimal
    statut: str
    date_debut: Optional[date]
    date_fin: Optional[date]
    nombre_echeances: int
    echeances_payees: int
    # Indicateurs calculés
    jours_retard: int = 0
    statut_par: str = "NORMAL"
    taux_recouvrement: Decimal = Decimal("0.00")
    montant_rembourse: Decimal = Decimal("0.00")
    alerte_requise: bool = False


@dataclass
class EcheanceInfo:
    """Informations sur une échéance de prêt."""
    id: int
    id_pret: str
    numero: int
    date_echeance: date
    montant_du: Decimal
    montant_paye: Decimal
    statut: str


# ---------------------------------------------------------------------------
# Fonctions de calcul
# ---------------------------------------------------------------------------

def calculate_jours_retard(
    echeances: list[EcheanceInfo],
    date_calcul: Optional[date] = None,
) -> int:
    """
    Calcule le nombre de jours de retard selon la logique COBAC.

    La COBAC définit le retard à partir de la première échéance
    impayée ou partiellement payée, non à partir de la date d'échéance
    finale du contrat.

    Args:
        echeances: Liste des échéances du prêt, triées par numéro croissant.
        date_calcul: Date de référence (aujourd'hui par défaut).

    Returns:
        Nombre de jours de retard (0 si aucun retard).
    """
    ref = date_calcul or date.today()
    premiere_impayee: Optional[date] = None

    for e in echeances:
        if e.statut in ("EN_RETARD", "EN_ATTENTE") and e.date_echeance < ref:
            # Échéance passée non soldée
            if e.montant_paye < e.montant_du:
                if premiere_impayee is None or e.date_echeance < premiere_impayee:
                    premiere_impayee = e.date_echeance

    if premiere_impayee is None:
        return 0

    return max(0, (ref - premiere_impayee).days)


def classify_par(jours_retard: int) -> str:
    """
    Classifie le prêt selon les seuils PAR COBAC.

    Seuils réglementaires :
      - NORMAL  : 0 jours de retard
      - PAR30   : 1 à 30 jours de retard
      - PAR90   : 31 à 90 jours de retard
      - PAR180  : plus de 90 jours de retard

    Args:
        jours_retard: Nombre de jours de retard calculé.

    Returns:
        Classification PAR sous forme de chaîne.
    """
    if jours_retard <= 0:
        return "NORMAL"
    elif jours_retard <= 30:
        return "PAR30"
    elif jours_retard <= 90:
        return "PAR90"
    else:
        return "PAR180"


def calculate_taux_recouvrement(
    montant_initial: Decimal,
    montant_rembourse: Decimal,
) -> Decimal:
    """
    Calcule le taux de recouvrement en pourcentage.

    Args:
        montant_initial: Capital initial accordé.
        montant_rembourse: Somme des paiements reçus.

    Returns:
        Taux en pourcentage (0.00 à 100.00).
    """
    if montant_initial <= Decimal("0"):
        return Decimal("0.00")

    taux = (montant_rembourse / montant_initial * 100).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )
    return min(taux, Decimal("100.00"))


def pret_requires_alerte(jours_retard: int, statut_par: str) -> bool:
    """
    Détermine si un prêt doit générer une alerte automatique.

    Une alerte est déclenchée dès que le prêt entre en PAR30.

    Args:
        jours_retard: Jours de retard calculés.
        statut_par: Classification PAR.

    Returns:
        True si une alerte doit être créée ou mise à jour.
    """
    return jours_retard > 0 and statut_par != "NORMAL"


def transform_pret(
    raw: dict,
    echeances: list[EcheanceInfo],
    date_calcul: Optional[date] = None,
) -> PretTransformed:
    """
    Transforme un prêt brut (dict depuis la base) en objet enrichi.

    Args:
        raw: Dictionnaire avec les colonnes du prêt (id_pret, reference, etc.)
        echeances: Liste des échéances associées à ce prêt.
        date_calcul: Date de référence pour les calculs (today par défaut).

    Returns:
        PretTransformed avec tous les indicateurs calculés.
    """
    montant_initial = Decimal(str(raw.get("montant_initial", 0) or 0))
    montant_rembourse = Decimal(str(raw.get("montant_rembourse", 0) or 0))
    montant_restant = montant_initial - montant_rembourse

    jours_retard = calculate_jours_retard(echeances, date_calcul)
    statut_par = classify_par(jours_retard)
    taux_recouvrement = calculate_taux_recouvrement(montant_initial, montant_rembourse)
    alerte_requise = pret_requires_alerte(jours_retard, statut_par)

    logger.debug(
        "Prêt %s — retard: %d j, PAR: %s, recouvrement: %s%%",
        raw.get("reference", "?"),
        jours_retard,
        statut_par,
        taux_recouvrement,
    )

    return PretTransformed(
        id_pret=str(raw.get("id_pret", "")),
        reference=raw.get("reference", ""),
        id_client=int(raw.get("id_client", 0) or 0),
        montant_initial=montant_initial,
        montant_restant=max(Decimal("0"), montant_restant),
        taux_interet=Decimal(str(raw.get("taux_interet", 0) or 0)),
        statut=raw.get("statut", ""),
        date_debut=_parse_date(raw.get("date_debut")),
        date_fin=_parse_date(raw.get("date_fin")),
        nombre_echeances=int(raw.get("nombre_echeances", 0) or 0),
        echeances_payees=int(raw.get("echeances_payees", 0) or 0),
        jours_retard=jours_retard,
        statut_par=statut_par,
        taux_recouvrement=taux_recouvrement,
        montant_rembourse=montant_rembourse,
        alerte_requise=alerte_requise,
    )


def transform_batch(
    prets: list[dict],
    echeances_par_pret: dict[str, list[EcheanceInfo]],
    date_calcul: Optional[date] = None,
) -> list[PretTransformed]:
    """
    Transforme un lot de prêts en parallèle (liste complète).

    Args:
        prets: Liste de dicts bruts depuis la base app.
        echeances_par_pret: Mapping id_pret → liste d'EcheanceInfo.
        date_calcul: Date de référence commune.

    Returns:
        Liste de PretTransformed triée par jours_retard décroissant.
    """
    results = []
    errors = 0

    for raw in prets:
        id_pret = str(raw.get("id_pret", ""))
        echeances = echeances_par_pret.get(id_pret, [])
        try:
            results.append(transform_pret(raw, echeances, date_calcul))
        except Exception as exc:
            errors += 1
            logger.error("Erreur transformation prêt %s : %s", id_pret, exc)

    if errors:
        logger.warning("%d prêts n'ont pas pu être transformés", errors)

    return sorted(results, key=lambda p: p.jours_retard, reverse=True)


# ---------------------------------------------------------------------------
# Utilitaires internes
# ---------------------------------------------------------------------------

def _parse_date(value: Optional[str | date]) -> Optional[date]:
    """Parse une date depuis une chaîne ISO ou retourne None."""
    if value is None:
        return None
    if isinstance(value, date):
        return value
    try:
        return date.fromisoformat(str(value))
    except (ValueError, TypeError):
        return None
