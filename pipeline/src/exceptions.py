"""
exceptions.py — Hiérarchie des exceptions métier du pipeline ETL IMF.

Toutes les exceptions héritent de PipelineException pour permettre
une gestion centralisée dans le runner principal.
"""

from __future__ import annotations

from typing import Any


class PipelineException(Exception):
    """
    Exception racine du pipeline ETL.
    Tous les cas d'erreur métier en héritent.
    """

    def __init__(self, message: str, *, details: Any = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details

    def __str__(self) -> str:
        if self.details:
            return f"{self.message} — détails : {self.details}"
        return self.message


# ── Configuration ─────────────────────────────────────────────────────────────

class ConfigurationError(PipelineException):
    """
    Variable d'environnement ou paramètre de configuration manquant/invalide.

    Exemples :
    - DB_HOST non défini
    - SPRING_API_KEY vide
    - PORT en dehors de la plage valide
    """


# ── Base de données ───────────────────────────────────────────────────────────

class DatabaseError(PipelineException):
    """Classe parente pour toutes les erreurs base de données."""


class DatabaseConnectionError(DatabaseError):
    """
    Impossible d'établir une connexion avec PostgreSQL.

    Exemples :
    - Hôte injoignable
    - Credentials invalides
    - Pool de connexions épuisé
    """

    def __init__(self, host: str, port: int, dbname: str, *, cause: Exception | None = None) -> None:
        super().__init__(
            f"Connexion PostgreSQL échouée — {host}:{port}/{dbname}",
            details=str(cause) if cause else None,
        )
        self.host = host
        self.port = port
        self.dbname = dbname
        self.cause = cause


class DatabaseQueryError(DatabaseError):
    """
    Erreur lors de l'exécution d'une requête SQL.

    Exemples :
    - Relation introuvable (schéma manquant)
    - Violation de contrainte
    - Timeout de requête
    """

    def __init__(self, query_hint: str, *, cause: Exception | None = None) -> None:
        super().__init__(
            f"Erreur SQL — {query_hint}",
            details=str(cause) if cause else None,
        )
        self.query_hint = query_hint
        self.cause = cause


class SchemaNotFoundError(DatabaseError):
    """
    Le schéma PostgreSQL attendu (staging / dw) n'existe pas.

    Se produit si les migrations Flyway ne se sont pas exécutées
    ou si la connexion pointe vers la mauvaise base.
    """

    def __init__(self, schema: str, table: str | None = None) -> None:
        target = f"{schema}.{table}" if table else schema
        super().__init__(f"Schéma/table introuvable : {target}")
        self.schema = schema
        self.table = table


class TransactionError(DatabaseError):
    """
    Impossible de committer ou de rollback une transaction.
    Indique généralement une connexion perdue en cours de traitement.
    """


# ── Extraction ────────────────────────────────────────────────────────────────

class ExtractionError(PipelineException):
    """
    Échec de l'extraction de données depuis la source.

    Exemples :
    - Résultat vide alors que des données sont attendues
    - Colonne manquante dans le résultat SQL
    - Type de données inattendu
    """

    def __init__(self, source: str, message: str, *, details: Any = None) -> None:
        super().__init__(f"Extraction {source} échouée — {message}", details=details)
        self.source = source


class EmptyDatasetError(ExtractionError):
    """
    La source n'a retourné aucune ligne alors que le traitement en requiert.
    Peut être un warning ou une erreur selon le contexte (ex. : table staging vide).
    """

    def __init__(self, source: str) -> None:
        super().__init__(source, "aucune donnée disponible")


class ColumnMissingError(ExtractionError):
    """
    Une colonne requise est absente du résultat de la requête SQL.
    Indique un changement de schéma non rétro-compatible.
    """

    def __init__(self, source: str, column: str) -> None:
        super().__init__(source, f"colonne manquante : '{column}'")
        self.column = column


# ── Transformation ────────────────────────────────────────────────────────────

class TransformationError(PipelineException):
    """
    Erreur lors de la transformation / calcul métier.

    Exemples :
    - Division par zéro dans le calcul PAR
    - Valeur numérique hors plage acceptable
    - Données incohérentes (date_echeance < date_deblocage)
    """

    def __init__(self, step: str, message: str, *, record_id: Any = None) -> None:
        detail = f"record_id={record_id}" if record_id is not None else None
        super().__init__(f"Transformation '{step}' échouée — {message}", details=detail)
        self.step = step
        self.record_id = record_id


class DataValidationError(TransformationError):
    """
    Un enregistrement ne satisfait pas les règles de validation métier.
    L'enregistrement est ignoré (skip) et loggué ; le traitement continue.
    """

    def __init__(self, step: str, field: str, value: Any, reason: str) -> None:
        super().__init__(step, f"champ '{field}'={value!r} — {reason}")
        self.field = field
        self.value = value
        self.reason = reason


# ── Chargement ───────────────────────────────────────────────────────────────

class LoadingError(PipelineException):
    """
    Erreur lors de l'insertion / mise à jour dans la cible (staging ou DW).

    Exemples :
    - Violation de clé unique
    - Contrainte FK non satisfaite
    - Timeout lors du batch insert
    """

    def __init__(self, target: str, message: str, *, details: Any = None) -> None:
        super().__init__(f"Chargement {target} échoué — {message}", details=details)
        self.target = target


class BatchInsertError(LoadingError):
    """
    L'insertion en lot (batch) a échoué partiellement ou totalement.
    Contient le nombre de lignes traitées avec succès avant l'échec.
    """

    def __init__(self, target: str, success_count: int, total: int, *, cause: Exception | None = None) -> None:
        super().__init__(
            target,
            f"{success_count}/{total} lignes insérées avant échec",
            details=str(cause) if cause else None,
        )
        self.success_count = success_count
        self.total = total


# ── API Spring Boot ───────────────────────────────────────────────────────────

class APIError(PipelineException):
    """Classe parente pour les erreurs d'appel à l'API Spring Boot."""


class NetworkError(APIError):
    """
    Connexion au backend Spring Boot impossible.

    Exemples :
    - Timeout réseau
    - DNS non résolu
    - Refus de connexion (service non démarré)
    """

    def __init__(self, url: str, *, cause: Exception | None = None) -> None:
        super().__init__(
            f"Erreur réseau — {url}",
            details=str(cause) if cause else None,
        )
        self.url = url


class AuthenticationError(APIError):
    """
    La clé API interne (X-Internal-Api-Key) est refusée par le backend.
    Indique une configuration incorrecte ou une rotation de clé non appliquée.
    """

    def __init__(self, url: str) -> None:
        super().__init__(f"Clé API interne invalide — {url} → 403")
        self.url = url


class BackendAPIError(APIError):
    """
    Le backend a retourné un code HTTP d'erreur non attendu.

    status_code : code HTTP reçu (4xx ou 5xx)
    response_body : corps de la réponse (pour le débogage)
    """

    def __init__(self, url: str, status_code: int, response_body: str = "") -> None:
        super().__init__(
            f"Erreur API {status_code} — {url}",
            details=response_body[:500] if response_body else None,
        )
        self.url = url
        self.status_code = status_code
        self.response_body = response_body


class DuplicateAlertError(APIError):
    """
    Le backend a retourné 409 CONFLICT : une alerte ACTIVE existe déjà
    pour ce prêt. Le pipeline doit ignorer cet enregistrement.
    """

    def __init__(self, id_pret: str) -> None:
        super().__init__(f"Alerte ACTIVE déjà existante pour le prêt {id_pret}")
        self.id_pret = id_pret


# ── Jobs / Orchestration ─────────────────────────────────────────────────────

class JobError(PipelineException):
    """
    Erreur de haut niveau levée par un job ETL.
    Encapsule les exceptions sous-jacentes pour le reporting.
    """

    def __init__(self, job_name: str, message: str, *, cause: Exception | None = None) -> None:
        super().__init__(
            f"Job '{job_name}' échoué — {message}",
            details=str(cause) if cause else None,
        )
        self.job_name = job_name
        self.cause = cause


class RetryExhaustedError(JobError):
    """
    Toutes les tentatives de retry ont échoué.
    Le job est marqué ECHEC dans sync_logs.
    """

    def __init__(self, job_name: str, attempts: int, *, last_error: Exception | None = None) -> None:
        super().__init__(
            job_name,
            f"échec après {attempts} tentative(s)",
            cause=last_error,
        )
        self.attempts = attempts
