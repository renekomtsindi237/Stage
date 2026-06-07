"""
staging_loader.py — Mise à jour des statuts dans staging.stg_prets.

Après détection des prêts en retard, met à jour leurs statuts
(EN_RETARD, EN_RETARD_GRAVE) et la date de dernière synchronisation.
"""

from __future__ import annotations

import logging
from typing import Any

from config import settings
from database import db_session, check_table_exists
from exceptions import LoadingError, SchemaNotFoundError

logger = logging.getLogger(__name__)


def update_statuts_retard(prets_en_retard: list[dict[str, Any]]) -> int:
    """
    Met à jour le statut des prêts en retard dans staging.stg_prets.

    Règle métier :
    - jours_retard in [30, 89] → statut = 'EN_RETARD'
    - jours_retard >= 90       → statut = 'EN_RETARD_GRAVE'

    Returns:
        Nombre de lignes mises à jour.

    Raises:
        SchemaNotFoundError: si staging.stg_prets n'existe pas.
        LoadingError: si la mise à jour SQL échoue.
    """
    if not prets_en_retard:
        return 0

    schema = settings.db.staging_schema
    table = "stg_prets"
    target = f"{schema}.{table}"

    if not check_table_exists(schema, table):
        raise SchemaNotFoundError(schema, table)

    par30 = settings.pipeline.par30_threshold_days
    par90 = settings.pipeline.par90_threshold_days

    updated = 0
    try:
        with db_session() as cur:
            for pret in prets_en_retard:
                id_pret = pret["id_pret"]
                jours = int(pret["jours_retard"])
                nouveau_statut = "EN_RETARD_GRAVE" if jours >= par90 else "EN_RETARD"

                cur.execute(
                    f"""
                    UPDATE {target}
                    SET statut_pret = %s
                    WHERE id_pret = %s
                      AND statut_pret NOT IN ('SOLDE', 'PERTE', 'ANNULE')
                    """,
                    (nouveau_statut, id_pret),
                )
                if cur.rowcount > 0:
                    updated += 1

    except (SchemaNotFoundError, LoadingError):
        raise
    except Exception as exc:
        raise LoadingError(target, "mise à jour statuts échouée", details=str(exc)) from exc

    logger.info("Mis à jour %d statuts de retard dans %s", updated, target)
    return updated
