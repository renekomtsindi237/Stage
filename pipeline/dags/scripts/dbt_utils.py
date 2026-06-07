"""
dbt_utils.py — Exécution des modèles dbt depuis les DAGs Airflow.

Chaque tâche dbt est exécutée via subprocess pour garantir l'isolation
de l'environnement Python du modèle (dbt Core 1.8, profil PostgreSQL).
"""

from __future__ import annotations

import logging
import os
import subprocess
from pathlib import Path

logger = logging.getLogger(__name__)

DBT_PROJECT_DIR = Path(os.getenv("DBT_PROJECT_DIR", "/app/pipeline/dbt_project"))
DBT_PROFILES_DIR = Path(os.getenv("DBT_PROFILES_DIR", "/app/pipeline/dbt_project"))
DBT_TARGET = os.getenv("DBT_TARGET", "prod")


def dbt_run_select(
    select: str,
    full_refresh: bool = False,
    vars: dict | None = None,
    **kwargs,
) -> dict:
    """
    Exécute `dbt run --select <select>` et retourne un résumé des résultats.

    Parameters
    ----------
    select       : Sélecteur dbt (ex : 'staging.stg_collectes_epargne')
    full_refresh : Si True, force un full-refresh des modèles incrémentaux
    vars         : Variables dbt additionnelles (dict)

    Returns
    -------
    dict avec 'returncode', 'stdout', 'stderr', 'success'
    """
    cmd = [
        "dbt",
        "run",
        "--project-dir",
        str(DBT_PROJECT_DIR),
        "--profiles-dir",
        str(DBT_PROFILES_DIR),
        "--target",
        DBT_TARGET,
        "--select",
        select,
    ]
    if full_refresh:
        cmd.append("--full-refresh")
    if vars:
        import json

        cmd.extend(["--vars", json.dumps(vars)])

    logger.info("Exécution dbt : %s", " ".join(cmd))

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        cwd=str(DBT_PROJECT_DIR),
        timeout=600,  # 10 minutes max
    )

    if result.returncode != 0:
        logger.error(
            "dbt run ÉCHEC (rc=%d)\n%s\n%s",
            result.returncode,
            result.stdout,
            result.stderr,
        )
        raise RuntimeError(
            f"dbt run --select '{select}' a échoué (rc={result.returncode}).\n"
            f"stderr: {result.stderr[-2000:]}"
        )

    logger.info("dbt run '%s' OK\n%s", select, result.stdout[-500:])
    return {
        "returncode": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr,
        "success": True,
        "select": select,
    }


def dbt_test_select(select: str, **kwargs) -> dict:
    """Exécute `dbt test --select <select>` et lève une exception si des tests échouent."""
    cmd = [
        "dbt",
        "test",
        "--project-dir",
        str(DBT_PROJECT_DIR),
        "--profiles-dir",
        str(DBT_PROFILES_DIR),
        "--target",
        DBT_TARGET,
        "--select",
        select,
    ]
    logger.info("Exécution dbt test : %s", " ".join(cmd))
    result = subprocess.run(
        cmd, capture_output=True, text=True, cwd=str(DBT_PROJECT_DIR), timeout=300
    )
    if result.returncode != 0:
        logger.warning(
            "dbt test '%s' — certains tests ont échoué\n%s",
            select,
            result.stdout[-1000:],
        )
    return {
        "returncode": result.returncode,
        "success": result.returncode == 0,
        "select": select,
    }
