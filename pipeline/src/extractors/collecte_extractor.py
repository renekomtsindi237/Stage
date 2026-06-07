"""
collecte_extractor.py — Extraction des collectes terrain non encore chargées dans le DW.

Lit depuis app.collectes_terrain les enregistrements avec statut CONFIRMEE
qui ne figurent pas encore dans dw.fact_collectes.
"""

from __future__ import annotations

import logging
from decimal import Decimal
from typing import Any

from config import settings
from database import check_table_exists, readonly_session
from exceptions import ExtractionError, SchemaNotFoundError

logger = logging.getLogger(__name__)

REQUIRED_COLUMNS = {
    "id",
    "id_pret",
    "agent_id",
    "montant",
    "canal",
    "date_collecte",
    "statut",
}


def extract_collectes_confirmees(since_id: int = 0) -> list[dict[str, Any]]:
    """
    Extrait les collectes CONFIRMEES depuis app.collectes_terrain,
    à partir du dernier ID traité (delta load).

    Args:
        since_id: ID de la dernière collecte déjà chargée dans le DW.
                  0 = chargement complet (full load).

    Returns:
        Liste de dicts représentant chaque collecte à charger.

    Raises:
        SchemaNotFoundError: si la table n'existe pas.
        ExtractionError: si la requête échoue.
    """
    app_schema = settings.db.app_schema
    table = "collectes_terrain"
    source = f"{app_schema}.{table}"

    if not check_table_exists(app_schema, table):
        raise SchemaNotFoundError(app_schema, table)

    sql = f"""
        SELECT
            c.id,
            c.id_pret,
            c.agent_id,
            u.username         AS nom_agent,
            c.montant,
            c.canal,
            c.latitude,
            c.longitude,
            c.date_collecte,
            c.statut,
            c.created_at
        FROM {app_schema}.{table} c
        LEFT JOIN {app_schema}.utilisateurs u ON c.agent_id = u.id
        WHERE c.statut = 'CONFIRMEE'
          AND c.id > %s
        ORDER BY c.id ASC
    """

    try:
        with readonly_session() as cur:
            cur.execute(sql, (since_id,))
            rows = cur.fetchall()
    except SchemaNotFoundError:
        raise
    except Exception as exc:
        raise ExtractionError(
            source, "requête collectes échouée", details=str(exc)
        ) from exc

    result: list[dict[str, Any]] = []
    for row in rows:
        d = dict(row)
        for col in REQUIRED_COLUMNS:
            if col not in d:
                # Montant manquant = collecte invalide, on skip avec warning
                logger.warning(
                    "Colonne manquante '%s' dans collecte id=%s — ignorée",
                    col,
                    d.get("id"),
                )
                break
        else:
            d["montant"] = Decimal(str(d["montant"]))
            result.append(d)

    logger.info(
        "Extrait %d collecte(s) CONFIRMEE depuis %s (since_id=%d)",
        len(result),
        source,
        since_id,
    )
    return result
