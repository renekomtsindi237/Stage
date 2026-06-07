"""
IMF Pipeline — Utilitaires dbt pour les DAGs Airflow
=====================================================

Wrappeur autour de la CLI dbt pour exécuter des modèles
depuis les tâches PythonOperator d'Airflow.

Usage dans un DAG :
  from scripts.dbt_utils import dbt_run_select, dbt_test, dbt_source_freshness
"""
from __future__ import annotations

import logging
import os
import subprocess
from typing import Any

log = logging.getLogger("imf.dbt")

DBT_PROJECT_DIR  = os.environ.get("DBT_PROJECT_DIR", "/opt/airflow/dbt_project")
DBT_PROFILES_DIR = os.environ.get("DBT_PROFILES_DIR", "/opt/airflow/dbt_project")
DBT_TARGET       = os.environ.get("DBT_TARGET", "dev")


def _run_dbt(args: list[str], check: bool = True) -> subprocess.CompletedProcess:
    """Exécute une commande dbt dans le répertoire projet."""
    cmd = [
        "dbt", *args,
        "--project-dir", DBT_PROJECT_DIR,
        "--profiles-dir", DBT_PROFILES_DIR,
        "--target", DBT_TARGET,
    ]
    log.info("dbt command: %s", " ".join(cmd))
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.stdout:
        log.info("dbt stdout:\n%s", result.stdout[-3000:])  # derniers 3000 chars
    if result.returncode != 0:
        log.error("dbt stderr:\n%s", result.stderr[-2000:])
        if check:
            raise RuntimeError(f"dbt a échoué avec code {result.returncode}")
    return result


def dbt_run_select(select: str, full_refresh: bool = False, **kwargs: Any) -> None:
    """
    Exécute dbt run --select <select>.
    Callable directement depuis PythonOperator.
    """
    args = ["run", "--select", select]
    if full_refresh:
        args.append("--full-refresh")
    _run_dbt(args)
    log.info("dbt run termine — select='%s' full_refresh=%s", select, full_refresh)


def dbt_test(select: str | None = None, **kwargs: Any) -> None:
    """Exécute dbt test (optionnellement limité à un sous-ensemble de modèles)."""
    args = ["test"]
    if select:
        args += ["--select", select]
    _run_dbt(args)


def dbt_source_freshness(**kwargs: Any) -> None:
    """Vérifie la fraîcheur des sources dbt (seuils définis dans sources.yml)."""
    _run_dbt(["source", "freshness"])


def dbt_compile(**kwargs: Any) -> None:
    """Compile les modèles dbt sans les exécuter."""
    _run_dbt(["compile"])
