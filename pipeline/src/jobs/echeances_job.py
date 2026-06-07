"""
echeances_job.py — Job de mise à jour des statuts des échéances.

Identifie les échéances EN_ATTENTE dont la date est dépassée
et les bascule en EN_RETARD dans app.echeances_app.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date, datetime

from config import settings
from database import check_table_exists, db_session
from exceptions import JobError, LoadingError, SchemaNotFoundError

logger = logging.getLogger(__name__)


@dataclass
class EcheancesJobResult:
    """Bilan d'exécution du job echeances."""

    mises_a_jour: int = 0
    debut: datetime = field(default_factory=datetime.utcnow)
    fin: datetime | None = None
    succes: bool = False
    message: str = ""

    @property
    def duree_secondes(self) -> float:
        if self.fin is None:
            return 0.0
        return (self.fin - self.debut).total_seconds()


def run_echeances_job(reference_date: date | None = None) -> EcheancesJobResult:
    """
    Bascule en EN_RETARD toutes les échéances EN_ATTENTE dont la date est dépassée.

    Args:
        reference_date: Date de référence (défaut : aujourd'hui).

    Returns:
        EcheancesJobResult avec le bilan.

    Raises:
        JobError: si la table est inaccessible ou si la mise à jour SQL échoue.
    """
    result = EcheancesJobResult()
    ref_date = reference_date or date.today()
    logger.info("=== Démarrage job echeances (référence=%s) ===", ref_date)

    schema = settings.db.app_schema
    table = "echeances_app"
    target = f"{schema}.{table}"

    if not check_table_exists(schema, table):
        raise JobError(
            "echeances",
            f"Table {target} introuvable",
            cause=SchemaNotFoundError(schema, table),
        )

    try:
        with db_session() as cur:
            cur.execute(
                f"""
                UPDATE {target}
                SET statut = 'EN_RETARD'
                WHERE statut = 'EN_ATTENTE'
                  AND date_echeance < %s
                RETURNING id
                """,
                (ref_date,),
            )
            rows = cur.fetchall()
            result.mises_a_jour = len(rows)

    except (SchemaNotFoundError, LoadingError):
        raise
    except Exception as exc:
        raise JobError("echeances", "Mise à jour SQL échouée", cause=exc) from exc

    result.succes = True
    result.fin = datetime.utcnow()
    result.message = (
        f"{result.mises_a_jour} échéance(s) basculée(s) EN_RETARD "
        f"(ref={ref_date}) — durée {result.duree_secondes:.1f}s"
    )
    logger.info("=== Fin job echeances : %s ===", result.message)

    _log_sync(result, "echeances")
    return result


def _log_sync(result: EcheancesJobResult, job_name: str) -> None:
    """Enregistre le résultat dans app.sync_logs."""
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
                    result.mises_a_jour,
                    result.message[:500],
                    result.debut,
                    result.fin or datetime.utcnow(),
                ),
            )
    except Exception as exc:
        logger.warning("Impossible d'enregistrer dans sync_logs : %s", exc)
