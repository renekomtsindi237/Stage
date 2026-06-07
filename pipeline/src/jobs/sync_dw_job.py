"""
sync_dw_job.py — Job ETL : staging → Data Warehouse.

Séquence :
1. Extraire tous les prêts actifs depuis staging.stg_prets
2. Transformer en FactRemboursement (calcul PAR30/PAR90)
3. Charger dans dw.fact_remboursements
4. Extraire les collectes CONFIRMEES non encore chargées
5. Transformer en FactCollecte
6. Charger dans dw.fact_collectes
7. Logger le résultat dans app.sync_logs
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime

from config import settings
from database import db_session
from exceptions import (
    EmptyDatasetError,
    ExtractionError,
    JobError,
    LoadingError,
    SchemaNotFoundError,
)
from extractors.collecte_extractor import extract_collectes_confirmees
from extractors.pret_extractor import extract_all_prets_actifs
from loaders.dw_loader import load_fact_collectes, load_fact_remboursements
from loaders.staging_loader import update_statuts_retard
from transformers.collecte_transformer import transform_collectes
from transformers.par_transformer import transform_prets_to_fact

logger = logging.getLogger(__name__)


@dataclass
class SyncDwJobResult:
    """Bilan d'exécution du job sync_dw."""

    prets_extraits: int = 0
    facts_remb_charges: int = 0
    collectes_extraites: int = 0
    facts_col_charges: int = 0
    statuts_mis_a_jour: int = 0
    debut: datetime = field(default_factory=datetime.utcnow)
    fin: datetime | None = None
    succes: bool = False
    message: str = ""

    @property
    def duree_secondes(self) -> float:
        if self.fin is None:
            return 0.0
        return (self.fin - self.debut).total_seconds()


def run_sync_dw_job(since_collecte_id: int = 0) -> SyncDwJobResult:
    """
    Exécute la synchronisation staging → Data Warehouse.

    Args:
        since_collecte_id: delta load — ID de la dernière collecte déjà chargée.

    Returns:
        SyncDwJobResult avec le bilan d'exécution.

    Raises:
        JobError: si une erreur bloquante empêche l'exécution complète.
    """
    result = SyncDwJobResult()
    logger.info("=== Démarrage job sync_dw ===")

    # ── Phase 1 : Prêts → fact_remboursements ────────────────────────────────
    try:
        prets = extract_all_prets_actifs()
        result.prets_extraits = len(prets)
        logger.info("Phase 1 : %d prêts actifs extraits", result.prets_extraits)

        facts_remb = transform_prets_to_fact(prets)
        result.facts_remb_charges = load_fact_remboursements(facts_remb)
        logger.info(
            "Phase 1 : %d enregistrements chargés dans fact_remboursements",
            result.facts_remb_charges,
        )

    except EmptyDatasetError as exc:
        logger.warning("Phase 1 : staging vide — %s", exc)
        # On continue avec les collectes
    except (SchemaNotFoundError, ExtractionError) as exc:
        raise JobError(
            "sync_dw", f"Phase 1 extraction échouée : {exc}", cause=exc
        ) from exc
    except LoadingError as exc:
        raise JobError(
            "sync_dw", f"Phase 1 chargement échoué : {exc}", cause=exc
        ) from exc

    # ── Phase 2 : Mise à jour statuts retard dans staging ────────────────────
    try:
        from extractors.pret_extractor import extract_prets_en_retard

        prets_retard = extract_prets_en_retard()
        result.statuts_mis_a_jour = update_statuts_retard(prets_retard)
        logger.info(
            "Phase 2 : %d statuts de retard mis à jour", result.statuts_mis_a_jour
        )
    except Exception as exc:
        logger.warning("Phase 2 (statuts retard) — erreur non bloquante : %s", exc)

    # ── Phase 3 : Collectes → fact_collectes ─────────────────────────────────
    try:
        collectes = extract_collectes_confirmees(since_id=since_collecte_id)
        result.collectes_extraites = len(collectes)
        logger.info(
            "Phase 3 : %d collectes extraites (since_id=%d)",
            result.collectes_extraites,
            since_collecte_id,
        )

        if collectes:
            facts_col = transform_collectes(collectes)
            result.facts_col_charges = load_fact_collectes(facts_col)
            logger.info(
                "Phase 3 : %d enregistrements chargés dans fact_collectes",
                result.facts_col_charges,
            )

    except (SchemaNotFoundError, ExtractionError) as exc:
        logger.error("Phase 3 extraction collectes échouée : %s", exc)
        # Non bloquant — le job est partiellement réussi
    except LoadingError as exc:
        logger.error("Phase 3 chargement collectes échoué : %s", exc)

    # ── Fin ───────────────────────────────────────────────────────────────────
    result.succes = True
    result.fin = datetime.utcnow()
    result.message = (
        f"Prêts: {result.prets_extraits} extraits → {result.facts_remb_charges} chargés | "
        f"Collectes: {result.collectes_extraites} extraites → {result.facts_col_charges} chargées | "
        f"Durée: {result.duree_secondes:.1f}s"
    )
    logger.info("=== Fin job sync_dw : %s ===", result.message)

    _log_sync(result, "sync_dw")
    return result


def _log_sync(result: SyncDwJobResult, job_name: str) -> None:
    """Enregistre le résultat dans app.sync_logs."""
    total = result.facts_remb_charges + result.facts_col_charges
    statut = "SUCCESS" if result.succes else "ERROR"

    try:
        with db_session() as cur:
            cur.execute(
                f"""
                INSERT INTO {settings.db.app_schema}.sync_logs
                    (source, statut, nb_enregistrements, details, started_at, completed_at)
                VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (
                    job_name,
                    statut,
                    total,
                    result.message[:500],
                    result.debut,
                    result.fin or datetime.utcnow(),
                ),
            )
    except Exception as exc:
        logger.warning("Impossible d'enregistrer dans sync_logs : %s", exc)
