"""
pret_extractor.py — Extraction des prêts depuis la table staging.stg_prets.

Lit les enregistrements actifs (non soldés, non en perte) et ceux
en retard selon le seuil configuré.
"""

from __future__ import annotations

import logging
from decimal import Decimal
from typing import Any

from config import settings
from database import readonly_session, check_table_exists
from exceptions import (
    ColumnMissingError,
    EmptyDatasetError,
    ExtractionError,
    SchemaNotFoundError,
)

logger = logging.getLogger(__name__)

# Colonnes requises dans stg_prets
REQUIRED_COLUMNS = {
    "id_pret", "id_client", "nom_client", "nom_agence", "nom_agent",
    "montant_pret", "date_deblocage", "date_echeance",
    "montant_rembourse", "solde_restant", "statut_pret", "jours_retard",
}


def _validate_row(row: dict[str, Any], source: str) -> None:
    """
    Valide qu'une ligne contient toutes les colonnes requises.

    Raises:
        ColumnMissingError: si une colonne est absente.
    """
    for col in REQUIRED_COLUMNS:
        if col not in row:
            raise ColumnMissingError(source, col)


def extract_prets_en_retard(min_jours_retard: int | None = None) -> list[dict[str, Any]]:
    """
    Extrait les prêts dont le retard dépasse le seuil configuré.

    Args:
        min_jours_retard: seuil de jours de retard minimum (défaut : ALERTE_MIN_JOURS_RETARD).

    Returns:
        Liste de dicts représentant chaque prêt en retard.

    Raises:
        SchemaNotFoundError: si staging.stg_prets n'existe pas.
        ExtractionError: si la requête échoue.
    """
    schema = settings.db.staging_schema
    table = "stg_prets"
    source = f"{schema}.{table}"
    seuil = min_jours_retard if min_jours_retard is not None else settings.pipeline.alerte_min_jours_retard

    if not check_table_exists(schema, table):
        raise SchemaNotFoundError(schema, table)

    sql = f"""
        SELECT
            id_pret,
            id_client,
            nom_client,
            nom_agence,
            nom_agent,
            montant_pret,
            date_deblocage,
            date_echeance,
            COALESCE(montant_rembourse, 0)  AS montant_rembourse,
            COALESCE(solde_restant, 0)      AS solde_restant,
            statut_pret,
            COALESCE(jours_retard, 0)       AS jours_retard
        FROM {schema}.{table}
        WHERE jours_retard >= %s
          AND statut_pret NOT IN ('SOLDE', 'PERTE', 'ANNULE')
        ORDER BY jours_retard DESC, id_pret
    """

    try:
        with readonly_session() as cur:
            cur.execute(sql, (seuil,))
            rows = cur.fetchall()
    except SchemaNotFoundError:
        raise
    except Exception as exc:
        raise ExtractionError(source, "requête SQL échouée", details=str(exc)) from exc

    if not rows:
        logger.info("Aucun prêt en retard >= %d jours dans %s", seuil, source)
        return []

    result: list[dict[str, Any]] = []
    for row in rows:
        row_dict = dict(row)
        _validate_row(row_dict, source)
        # Normalisation des types
        row_dict["montant_pret"] = Decimal(str(row_dict["montant_pret"]))
        row_dict["montant_rembourse"] = Decimal(str(row_dict["montant_rembourse"]))
        row_dict["solde_restant"] = Decimal(str(row_dict["solde_restant"]))
        row_dict["jours_retard"] = int(row_dict["jours_retard"])
        result.append(row_dict)

    logger.info("Extrait %d prêts en retard (seuil=%d j) depuis %s", len(result), seuil, source)
    return result


def extract_all_prets_actifs() -> list[dict[str, Any]]:
    """
    Extrait tous les prêts actifs (non soldés, non perdus) pour le calcul PAR.

    Returns:
        Liste complète des prêts actifs.

    Raises:
        SchemaNotFoundError: si staging.stg_prets n'existe pas.
        EmptyDatasetError: si aucun prêt actif n'est trouvé.
        ExtractionError: si la requête échoue.
    """
    schema = settings.db.staging_schema
    table = "stg_prets"
    source = f"{schema}.{table}"

    if not check_table_exists(schema, table):
        raise SchemaNotFoundError(schema, table)

    sql = f"""
        SELECT
            id_pret, id_client, nom_client, nom_agence, nom_agent,
            montant_pret,
            date_deblocage, date_echeance,
            COALESCE(montant_rembourse, 0) AS montant_rembourse,
            COALESCE(solde_restant, 0)     AS solde_restant,
            statut_pret,
            COALESCE(jours_retard, 0)      AS jours_retard
        FROM {schema}.{table}
        WHERE statut_pret NOT IN ('SOLDE', 'PERTE', 'ANNULE')
        ORDER BY id_pret
    """

    try:
        with readonly_session() as cur:
            cur.execute(sql)
            rows = cur.fetchall()
    except SchemaNotFoundError:
        raise
    except Exception as exc:
        raise ExtractionError(source, "requête SQL échouée", details=str(exc)) from exc

    if not rows:
        raise EmptyDatasetError(source)

    result: list[dict[str, Any]] = []
    for row in rows:
        row_dict = dict(row)
        _validate_row(row_dict, source)
        row_dict["montant_pret"] = Decimal(str(row_dict["montant_pret"]))
        row_dict["montant_rembourse"] = Decimal(str(row_dict["montant_rembourse"]))
        row_dict["solde_restant"] = Decimal(str(row_dict["solde_restant"]))
        row_dict["jours_retard"] = int(row_dict["jours_retard"])
        result.append(row_dict)

    logger.info("Extrait %d prêts actifs depuis %s", len(result), source)
    return result
