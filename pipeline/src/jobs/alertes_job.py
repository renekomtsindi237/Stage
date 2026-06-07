"""
alertes_job.py — Job de détection des impayés et création des alertes.

Séquence :
1. Extraire les prêts en retard >= ALERTE_MIN_JOURS_RETARD jours
2. Pour chaque prêt, appeler POST /internal/alertes
3. Ignorer les doublons (409 CONFLICT)
4. Logger le résultat dans app.sync_logs
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime

from api_client import SpringAPIClient
from config import settings
from database import db_session
from exceptions import (
    DuplicateAlertError,
    ExtractionError,
    JobError,
    NetworkError,
    SchemaNotFoundError,
)
from extractors.pret_extractor import extract_prets_en_retard

logger = logging.getLogger(__name__)


@dataclass
class AlertesJobResult:
    """Rapport d'exécution du job alertes."""

    total_prets_en_retard: int = 0
    alertes_creees: int = 0
    doublons_ignores: int = 0
    erreurs: int = 0
    debut: datetime = field(default_factory=datetime.utcnow)
    fin: datetime | None = None
    succes: bool = False
    message: str = ""

    @property
    def duree_secondes(self) -> float:
        if self.fin is None:
            return 0.0
        return (self.fin - self.debut).total_seconds()


def run_alertes_job() -> AlertesJobResult:
    """
    Exécute le job de détection des alertes impayés.

    Returns:
        AlertesJobResult avec le bilan d'exécution.

    Raises:
        JobError: si une erreur bloquante empêche l'exécution.
    """
    result = AlertesJobResult()
    logger.info("=== Démarrage job alertes_impayes ===")

    # 1. Extraction des prêts en retard
    try:
        prets_en_retard = extract_prets_en_retard()
    except SchemaNotFoundError as exc:
        raise JobError("alertes_impayes", f"Table staging manquante : {exc}", cause=exc) from exc
    except ExtractionError as exc:
        raise JobError("alertes_impayes", f"Extraction échouée : {exc}", cause=exc) from exc

    result.total_prets_en_retard = len(prets_en_retard)

    if not prets_en_retard:
        result.succes = True
        result.message = "Aucun prêt en retard — aucune alerte générée"
        result.fin = datetime.utcnow()
        logger.info(result.message)
        _log_sync(result, "alertes_impayes")
        return result

    logger.info("%d prêts en retard détectés", result.total_prets_en_retard)

    # 2. Création des alertes via l'API Spring Boot
    with SpringAPIClient() as client:
        for pret in prets_en_retard:
            id_pret = pret["id_pret"]
            jours_retard = pret["jours_retard"]
            montant_en_retard = pret["solde_restant"]

            try:
                client.creer_alerte(id_pret, jours_retard, montant_en_retard)
                result.alertes_creees += 1
                logger.debug("Alerte créée : prêt=%s, jours=%d", id_pret, jours_retard)

            except DuplicateAlertError:
                result.doublons_ignores += 1
                logger.debug("Alerte doublon ignorée : prêt=%s", id_pret)

            except NetworkError as exc:
                # Erreur réseau non récupérable après retry
                result.erreurs += 1
                logger.error("Erreur réseau pour prêt %s : %s", id_pret, exc)
                # On continue avec les autres prêts (best-effort)

            except Exception as exc:
                result.erreurs += 1
                logger.error("Erreur inattendue pour prêt %s : %s", id_pret, exc)

    # 3. Bilan
    result.succes = result.erreurs == 0
    result.fin = datetime.utcnow()
    result.message = (
        f"Alertes : {result.alertes_creees} créées, "
        f"{result.doublons_ignores} doublons, "
        f"{result.erreurs} erreur(s) — durée {result.duree_secondes:.1f}s"
    )
    logger.info("=== Fin job alertes_impayes : %s ===", result.message)

    _log_sync(result, "alertes_impayes")
    return result


def _log_sync(result: AlertesJobResult, job_name: str) -> None:
    """Enregistre le résultat dans app.sync_logs."""
    statut = "SUCCESS" if result.succes else "PARTIAL_ERROR" if result.erreurs > 0 else "SUCCESS"
    details = result.message

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
                    result.alertes_creees,
                    details[:500],
                    result.debut,
                    result.fin or datetime.utcnow(),
                ),
            )
    except Exception as exc:
        # Le logging d'audit ne doit pas faire échouer le job
        logger.warning("Impossible d'enregistrer dans sync_logs : %s", exc)
