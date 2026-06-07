"""
test_exceptions.py — Tests de la hiérarchie d'exceptions du pipeline.
"""

import pytest
from exceptions import (
    PipelineException,
    ConfigurationError,
    DatabaseConnectionError,
    DatabaseQueryError,
    SchemaNotFoundError,
    TransactionError,
    ExtractionError,
    EmptyDatasetError,
    ColumnMissingError,
    TransformationError,
    DataValidationError,
    LoadingError,
    BatchInsertError,
    NetworkError,
    AuthenticationError,
    BackendAPIError,
    DuplicateAlertError,
    JobError,
    RetryExhaustedError,
)


class TestHierarchy:
    """Vérifie l'héritage de la hiérarchie d'exceptions."""

    def test_configuration_error_is_pipeline(self):
        exc = ConfigurationError("bad config")
        assert isinstance(exc, PipelineException)

    def test_database_connection_error_is_pipeline(self):
        exc = DatabaseConnectionError("localhost", 5432, "imf_db")
        assert isinstance(exc, PipelineException)

    def test_extraction_errors_inherit(self):
        assert issubclass(EmptyDatasetError, ExtractionError)
        assert issubclass(ColumnMissingError, ExtractionError)
        assert issubclass(ExtractionError, PipelineException)

    def test_transformation_errors_inherit(self):
        assert issubclass(DataValidationError, TransformationError)
        assert issubclass(TransformationError, PipelineException)

    def test_loading_errors_inherit(self):
        assert issubclass(BatchInsertError, LoadingError)
        assert issubclass(LoadingError, PipelineException)

    def test_api_errors_inherit(self):
        assert issubclass(NetworkError, PipelineException)
        assert issubclass(AuthenticationError, PipelineException)
        assert issubclass(BackendAPIError, PipelineException)
        assert issubclass(DuplicateAlertError, PipelineException)

    def test_job_errors_inherit(self):
        assert issubclass(JobError, PipelineException)
        assert issubclass(RetryExhaustedError, JobError)


class TestDatabaseConnectionError:
    def test_attributes(self):
        cause = ConnectionRefusedError("refused")
        exc = DatabaseConnectionError("db-host", 5432, "imf_db", cause=cause)
        assert exc.host == "db-host"
        assert exc.port == 5432
        assert exc.dbname == "imf_db"
        assert exc.cause is cause

    def test_message_contains_host_and_port(self):
        exc = DatabaseConnectionError("10.0.0.1", 5433, "prod_db")
        assert "10.0.0.1" in str(exc)
        assert "5433" in str(exc)
        assert "prod_db" in str(exc)


class TestSchemaNotFoundError:
    def test_schema_only(self):
        exc = SchemaNotFoundError("dw")
        assert exc.schema == "dw"
        assert exc.table is None
        assert "dw" in str(exc)

    def test_schema_and_table(self):
        exc = SchemaNotFoundError("staging", "stg_prets")
        assert exc.table == "stg_prets"
        assert "staging.stg_prets" in str(exc)


class TestExtractionErrors:
    def test_empty_dataset(self):
        exc = EmptyDatasetError("staging.stg_prets")
        assert "staging.stg_prets" in str(exc)
        assert "aucune donnée" in str(exc)

    def test_column_missing(self):
        exc = ColumnMissingError("staging.stg_prets", "solde_restant")
        assert "solde_restant" in str(exc)

    def test_extraction_error_with_details(self):
        exc = ExtractionError("source_table", "requête échouée", details="timeout")
        assert "source_table" in str(exc)
        assert "timeout" in str(exc)


class TestTransformationErrors:
    def test_data_validation_fields(self):
        exc = DataValidationError("par_transformer", "jours_retard", -5, "valeur négative")
        assert exc.field == "jours_retard"
        assert exc.value == -5
        assert exc.reason == "valeur négative"
        assert "jours_retard" in str(exc)
        assert "-5" in str(exc)

    def test_transformation_error_with_record_id(self):
        exc = TransformationError("step1", "division par zéro", record_id="PRE-001")
        assert exc.record_id == "PRE-001"
        assert "step1" in str(exc)


class TestLoadingErrors:
    def test_batch_insert_error(self):
        cause = RuntimeError("SQL error")
        exc = BatchInsertError("dw.fact_remboursements", 150, 500, cause=cause)
        assert exc.success_count == 150
        assert exc.total == 500
        assert "150/500" in str(exc)

    def test_loading_error_with_details(self):
        exc = LoadingError("dw.fact_collectes", "contrainte violée", details="FK manquante")
        assert "FK manquante" in str(exc)


class TestAPIErrors:
    def test_network_error_with_cause(self):
        cause = TimeoutError("read timeout")
        exc = NetworkError("http://localhost:8080/internal/alertes", cause=cause)
        assert "localhost:8080" in str(exc)
        assert exc.url == "http://localhost:8080/internal/alertes"

    def test_authentication_error(self):
        exc = AuthenticationError("http://api/internal/alertes")
        assert "403" in str(exc)

    def test_backend_api_error(self):
        exc = BackendAPIError("http://api/internal/alertes", 500, '{"error":"oops"}')
        assert exc.status_code == 500
        assert "500" in str(exc)

    def test_duplicate_alert_error(self):
        exc = DuplicateAlertError("PRE-001")
        assert exc.id_pret == "PRE-001"
        assert "PRE-001" in str(exc)


class TestJobErrors:
    def test_job_error(self):
        cause = RuntimeError("db down")
        exc = JobError("alertes_impayes", "extraction échouée", cause=cause)
        assert exc.job_name == "alertes_impayes"
        assert exc.cause is cause

    def test_retry_exhausted_error(self):
        last_err = NetworkError("http://api")
        exc = RetryExhaustedError("sync_dw", 3, last_error=last_err)
        assert exc.attempts == 3
        assert "3" in str(exc)


class TestPipelineExceptionStr:
    def test_no_details(self):
        exc = PipelineException("erreur simple")
        assert str(exc) == "erreur simple"

    def test_with_details(self):
        exc = PipelineException("erreur avec détails", details="contexte")
        assert "contexte" in str(exc)
        assert "erreur avec détails" in str(exc)
