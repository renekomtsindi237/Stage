"""
main.py — Point d'entrée du pipeline ETL IMF.

Usage :
    python main.py --job alertes          # Détection des impayés
    python main.py --job sync_dw          # Synchronisation staging → DW
    python main.py --job echeances        # Mise à jour statuts échéances
    python main.py --job all              # Tous les jobs dans l'ordre
    python main.py --schedule             # Mode démon (planifié)
"""

from __future__ import annotations

import argparse
import sys
import time
from datetime import datetime

import schedule

from config import settings
from exceptions import ConfigurationError, JobError, PipelineException
from jobs.alertes_job import run_alertes_job
from jobs.echeances_job import run_echeances_job
from jobs.sync_dw_job import run_sync_dw_job
from logger import setup_logging, get_logger

setup_logging(level="DEBUG" if not settings.is_production else "INFO")
logger = get_logger(__name__)


def run_job(job_name: str) -> bool:
    """
    Exécute un job identifié par son nom.

    Returns:
        True si le job a réussi, False sinon.
    """
    logger.info("Démarrage du job : %s", job_name)
    start = datetime.utcnow()

    try:
        if job_name == "alertes":
            result = run_alertes_job()
            success = result.succes
        elif job_name == "sync_dw":
            result = run_sync_dw_job()
            success = result.succes
        elif job_name == "echeances":
            result = run_echeances_job()
            success = result.succes
        elif job_name == "all":
            success = _run_all_jobs()
        else:
            logger.error("Job inconnu : '%s'", job_name)
            return False

        elapsed = (datetime.utcnow() - start).total_seconds()
        if success:
            logger.info("Job '%s' terminé avec succès en %.1fs", job_name, elapsed)
        else:
            logger.warning("Job '%s' terminé avec des erreurs en %.1fs", job_name, elapsed)
        return success

    except JobError as exc:
        logger.error("Job '%s' échoué : %s", job_name, exc)
        return False
    except PipelineException as exc:
        logger.error("Erreur pipeline dans '%s' : %s", job_name, exc)
        return False
    except Exception as exc:
        logger.exception("Erreur inattendue dans '%s' : %s", job_name, exc)
        return False


def _run_all_jobs() -> bool:
    """Exécute tous les jobs dans l'ordre logique."""
    ok_sync = run_job("sync_dw")
    ok_alertes = run_job("alertes")
    ok_echeances = run_job("echeances")
    return ok_sync and ok_alertes and ok_echeances


def start_scheduler() -> None:
    """
    Mode démon : exécute les jobs selon un calendrier fixe.

    Planification :
    - sync_dw     : toutes les heures
    - alertes     : tous les jours à 06h00
    - echeances   : tous les jours à 00h30
    """
    logger.info("=== Pipeline IMF démarré en mode planifié (env=%s) ===", settings.pipeline.env)

    schedule.every().hour.at(":00").do(run_job, "sync_dw")
    schedule.every().day.at("06:00").do(run_job, "alertes")
    schedule.every().day.at("00:30").do(run_job, "echeances")

    logger.info(
        "Jobs planifiés : sync_dw (horaire), alertes (06:00), echeances (00:30)"
    )

    while True:
        try:
            schedule.run_pending()
            time.sleep(30)
        except KeyboardInterrupt:
            logger.info("Arrêt du pipeline (SIGINT)")
            sys.exit(0)
        except Exception as exc:
            logger.error("Erreur dans la boucle du scheduler : %s", exc)
            time.sleep(60)  # Attente avant retry


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Pipeline ETL IMF — détection impayés et synchronisation DW"
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--job",
        choices=["alertes", "sync_dw", "echeances", "all"],
        help="Nom du job à exécuter",
    )
    group.add_argument(
        "--schedule",
        action="store_true",
        help="Démarrer en mode démon avec planification automatique",
    )
    return parser.parse_args()


def main() -> int:
    """
    Point d'entrée principal.

    Returns:
        0 si succès, 1 si échec.
    """
    try:
        args = parse_args()
    except SystemExit as exc:
        return int(exc.code) if exc.code is not None else 1

    try:
        if args.schedule:
            start_scheduler()
            return 0
        else:
            success = run_job(args.job)
            return 0 if success else 1

    except ConfigurationError as exc:
        logger.error("Erreur de configuration : %s", exc)
        return 2
    except KeyboardInterrupt:
        logger.info("Interruption utilisateur")
        return 0


if __name__ == "__main__":
    sys.exit(main())
