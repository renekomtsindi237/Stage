"""
database.py — Gestionnaire de connexion PostgreSQL.

Fournit un context manager thread-safe pour obtenir et libérer
des connexions via psycopg2. Les erreurs psycopg2 sont converties
en exceptions du pipeline pour une gestion uniforme.
"""

from __future__ import annotations

import logging
from contextlib import contextmanager
from typing import Generator

import psycopg2
import psycopg2.extensions
import psycopg2.extras
from config import settings
from exceptions import (
    DatabaseConnectionError,
    DatabaseQueryError,
    SchemaNotFoundError,
    TransactionError,
)
from psycopg2 import DatabaseError as Psycopg2DBError
from psycopg2 import OperationalError

logger = logging.getLogger(__name__)

# Type alias pour la clarté
Connection = psycopg2.extensions.connection
Cursor = psycopg2.extensions.cursor


def get_connection() -> Connection:
    """
    Ouvre une connexion PostgreSQL à partir des settings.

    Raises:
        DatabaseConnectionError: si psycopg2 ne peut pas se connecter.
    """
    db = settings.db
    try:
        conn = psycopg2.connect(
            host=db.host,
            port=db.port,
            dbname=db.db,
            user=db.user,
            password=db.password,
            connect_timeout=10,
            options=f"-c search_path={db.app_schema},public",
        )
        conn.autocommit = False
        logger.debug("Connexion PostgreSQL établie : %s:%d/%s", db.host, db.port, db.db)
        return conn
    except OperationalError as exc:
        raise DatabaseConnectionError(db.host, db.port, db.db, cause=exc) from exc


@contextmanager
def db_session() -> Generator[Cursor, None, None]:
    """
    Context manager fournissant un curseur dict-like dans une transaction.

    Usage::

        with db_session() as cur:
            cur.execute("SELECT * FROM staging.stg_prets WHERE id_pret = %s", (id_pret,))
            rows = cur.fetchall()

    Commit automatique à la sortie ; rollback si une exception est levée.

    Raises:
        DatabaseConnectionError: si la connexion échoue.
        DatabaseQueryError: si une requête SQL échoue.
        TransactionError: si le commit/rollback échoue.
    """
    conn = get_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        yield cur
        conn.commit()
        logger.debug("Transaction committée avec succès")
    except (Psycopg2DBError, psycopg2.Error) as exc:
        _safe_rollback(conn)
        _translate_psycopg2_error(exc)
    except Exception:
        _safe_rollback(conn)
        raise
    finally:
        cur.close()
        conn.close()


@contextmanager
def readonly_session() -> Generator[Cursor, None, None]:
    """
    Context manager pour les requêtes en lecture seule.
    Autocommit désactivé ; aucun commit n'est effectué.
    """
    conn = get_connection()
    conn.set_session(readonly=True, autocommit=True)
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        yield cur
    except (Psycopg2DBError, psycopg2.Error) as exc:
        _translate_psycopg2_error(exc)
    finally:
        cur.close()
        conn.close()


def _safe_rollback(conn: Connection) -> None:
    """Rollback silencieux — logue l'erreur mais ne la propage pas."""
    try:
        conn.rollback()
        logger.debug("Transaction rollbackée")
    except Exception as rb_exc:
        logger.error("Échec du rollback : %s", rb_exc)


def _translate_psycopg2_error(exc: psycopg2.Error) -> None:
    """
    Convertit une exception psycopg2 en exception du pipeline.

    - UndefinedTable / UndefinedColumn → SchemaNotFoundError
    - Toutes les autres → DatabaseQueryError
    """
    pgcode = getattr(exc, "pgcode", "") or ""

    # 42P01 = undefined_table, 42703 = undefined_column, 3F000 = invalid_schema_name
    if pgcode in ("42P01", "42703", "3F000"):
        # Extraire le nom de la relation depuis le message
        msg = str(exc)
        raise SchemaNotFoundError(schema="unknown", table=msg[:200]) from exc

    raise DatabaseQueryError(query_hint=str(exc)[:300], cause=exc) from exc


def check_schema_exists(schema: str) -> bool:
    """
    Vérifie qu'un schéma PostgreSQL est accessible.

    Returns:
        True si le schéma existe et est visible avec les credentials courants.

    Raises:
        DatabaseConnectionError: si la connexion échoue.
    """
    try:
        with readonly_session() as cur:
            cur.execute(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = %s",
                (schema,),
            )
            return cur.fetchone() is not None
    except DatabaseQueryError:
        return False


def check_table_exists(schema: str, table: str) -> bool:
    """Vérifie qu'une table est accessible dans le schéma donné."""
    try:
        with readonly_session() as cur:
            cur.execute(
                """
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = %s AND table_name = %s
                """,
                (schema, table),
            )
            return cur.fetchone() is not None
    except DatabaseQueryError:
        return False
