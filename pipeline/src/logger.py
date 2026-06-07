"""
logger.py — Configuration du logging structuré pour le pipeline.
Utilise structlog pour des logs JSON en production, lisibles en dev.
"""

from __future__ import annotations

import logging
import sys
from typing import Any

import structlog
from structlog.types import EventDict, WrappedLogger

from config import settings


def _add_job_context(
    logger: WrappedLogger,  # noqa: ARG001
    method_name: str,  # noqa: ARG001
    event_dict: EventDict,
) -> EventDict:
    """Processor structlog : ajoute l'environnement au contexte."""
    event_dict.setdefault("env", settings.pipeline.env)
    return event_dict


def setup_logging(level: str = "INFO") -> None:
    """
    Configure structlog + logging stdlib.
    - En production → JSON renderer
    - En dev → ConsoleRenderer coloré
    """
    log_level = getattr(logging, level.upper(), logging.INFO)

    shared_processors: list[Any] = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        structlog.processors.TimeStamper(fmt="iso"),
        _add_job_context,
    ]

    if settings.is_production:
        renderer: Any = structlog.processors.JSONRenderer()
    else:
        renderer = structlog.dev.ConsoleRenderer()

    structlog.configure(
        processors=shared_processors
        + [
            structlog.stdlib.ProcessorFormatter.wrap_for_formatter,
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )

    formatter = structlog.stdlib.ProcessorFormatter(
        processor=renderer,
        foreign_pre_chain=shared_processors,
    )

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.addHandler(handler)
    root_logger.setLevel(log_level)

    # Réduire le bruit des bibliothèques tierces
    logging.getLogger("psycopg2").setLevel(logging.WARNING)
    logging.getLogger("requests").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)
    logging.getLogger("tenacity").setLevel(logging.WARNING)


def get_logger(name: str) -> structlog.stdlib.BoundLogger:
    """Retourne un logger structlog lié au module donné."""
    return structlog.get_logger(name)
