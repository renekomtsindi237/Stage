"""
dw_loader.py — Chargement des données transformées dans le Data Warehouse.

Insère par batch dans dw.fact_remboursements et dw.fact_collectes.
Utilise une stratégie INSERT … ON CONFLICT DO NOTHING pour l'idempotence.
"""

from __future__ import annotations

import logging
from typing import Any

from config import settings
from database import check_schema_exists, db_session
from exceptions import BatchInsertError, SchemaNotFoundError
from transformers.collecte_transformer import FactCollecte
from transformers.par_transformer import FactRemboursement

logger = logging.getLogger(__name__)

BATCH_SIZE = settings.pipeline.batch_size


def load_fact_remboursements(facts: list[FactRemboursement]) -> int:
    """
    Charge les enregistrements FactRemboursement dans dw.fact_remboursements.

    Stratégie : INSERT … ON CONFLICT (id_pret, date_valeur) DO UPDATE
    pour mettre à jour les valeurs si les données source ont changé.

    Returns:
        Nombre total de lignes upsertées.

    Raises:
        SchemaNotFoundError: si le schéma dw n'existe pas.
        BatchInsertError: si un batch échoue partiellement.
        LoadingError: si une erreur SQL survient.
    """
    if not facts:
        logger.info("fact_remboursements : aucune donnée à charger")
        return 0

    schema = settings.db.dw_schema
    target = f"{schema}.fact_remboursements"

    if not check_schema_exists(schema):
        raise SchemaNotFoundError(schema)

    sql = f"""
        INSERT INTO {target}
            (id_pret, id_agence, date_valeur, montant_pret, montant_rembourse,
             solde_restant, statut_pret, jours_retard, encours_par30, encours_par90)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (id_pret, date_valeur) DO UPDATE SET
            montant_rembourse = EXCLUDED.montant_rembourse,
            solde_restant     = EXCLUDED.solde_restant,
            statut_pret       = EXCLUDED.statut_pret,
            jours_retard      = EXCLUDED.jours_retard,
            encours_par30     = EXCLUDED.encours_par30,
            encours_par90     = EXCLUDED.encours_par90
    """

    return _batch_upsert(target, sql, [_fact_remb_to_tuple(f) for f in facts])


def load_fact_collectes(facts: list[FactCollecte]) -> int:
    """
    Charge les FactCollecte dans dw.fact_collectes.

    Utilise source_id comme clé de déduplication (ON CONFLICT DO NOTHING).

    Returns:
        Nombre de lignes insérées.

    Raises:
        SchemaNotFoundError: si le schéma dw n'existe pas.
        BatchInsertError: si un batch échoue.
        LoadingError: si une erreur SQL survient.
    """
    if not facts:
        logger.info("fact_collectes : aucune donnée à charger")
        return 0

    schema = settings.db.dw_schema
    target = f"{schema}.fact_collectes"

    if not check_schema_exists(schema):
        raise SchemaNotFoundError(schema)

    sql = f"""
        INSERT INTO {target}
            (source_id, id_pret, id_agence, date_valeur, canal, montant, nom_agent)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_id) DO NOTHING
    """

    return _batch_upsert(target, sql, [_fact_col_to_tuple(f) for f in facts])


# ── Utilitaires internes ──────────────────────────────────────────────────────


def _batch_upsert(target: str, sql: str, rows: list[tuple[Any, ...]]) -> int:
    """
    Exécute l'upsert par batches de BATCH_SIZE lignes.

    Returns:
        Nombre total de lignes traitées.

    Raises:
        BatchInsertError: si un batch SQL échoue.
    """
    total = len(rows)
    inserted = 0

    try:
        with db_session() as cur:
            for i in range(0, total, BATCH_SIZE):
                batch = rows[i : i + BATCH_SIZE]
                cur.executemany(sql, batch)
                inserted += len(batch)
                logger.debug("Batch %d/%d inséré dans %s", inserted, total, target)
    except Exception as exc:
        raise BatchInsertError(target, inserted, total, cause=exc) from exc

    logger.info("Chargé %d/%d enregistrements dans %s", inserted, total, target)
    return inserted


def _fact_remb_to_tuple(f: FactRemboursement) -> tuple[Any, ...]:
    return (
        f.id_pret,
        f.id_agence,
        f.date_valeur,
        f.montant_pret,
        f.montant_rembourse,
        f.solde_restant,
        f.statut_pret,
        f.jours_retard,
        f.encours_par30,
        f.encours_par90,
    )


def _fact_col_to_tuple(f: FactCollecte) -> tuple[Any, ...]:
    return (
        f.source_id,
        f.id_pret,
        f.id_agence,
        f.date_valeur,
        f.canal,
        f.montant,
        f.nom_agent,
    )
