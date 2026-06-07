"""
test_alertes_job.py — Tests unitaires du job de détection des alertes.
"""

from __future__ import annotations

from decimal import Decimal
from unittest.mock import MagicMock, patch

import pytest

from exceptions import (
    DuplicateAlertError,
    ExtractionError,
    JobError,
    NetworkError,
    SchemaNotFoundError,
)
from jobs.alertes_job import AlertesJobResult, run_alertes_job


class TestRunAlertesJob:

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job.SpringAPIClient")
    @patch("jobs.alertes_job._log_sync")
    def test_aucun_pret_en_retard(self, mock_log, MockClient, mock_extract):
        mock_extract.return_value = []

        result = run_alertes_job()

        assert result.succes is True
        assert result.alertes_creees == 0
        assert result.total_prets_en_retard == 0
        MockClient.assert_not_called()

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job.SpringAPIClient")
    @patch("jobs.alertes_job._log_sync")
    def test_alerte_creee_avec_succes(self, mock_log, MockClient, mock_extract):
        mock_extract.return_value = [
            {
                "id_pret": "PRE-001",
                "jours_retard": 45,
                "solde_restant": Decimal("300000"),
            },
        ]
        mock_client = MagicMock()
        mock_client.creer_alerte.return_value = {
            "id": 1,
            "id_pret": "PRE-001",
            "statut": "ACTIVE",
        }
        MockClient.return_value.__enter__ = MagicMock(return_value=mock_client)
        MockClient.return_value.__exit__ = MagicMock(return_value=False)

        result = run_alertes_job()

        assert result.alertes_creees == 1
        assert result.doublons_ignores == 0
        assert result.erreurs == 0
        assert result.succes is True

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job.SpringAPIClient")
    @patch("jobs.alertes_job._log_sync")
    def test_doublon_est_ignore(self, mock_log, MockClient, mock_extract):
        mock_extract.return_value = [
            {
                "id_pret": "PRE-001",
                "jours_retard": 45,
                "solde_restant": Decimal("300000"),
            },
        ]
        mock_client = MagicMock()
        mock_client.creer_alerte.side_effect = DuplicateAlertError("PRE-001")
        MockClient.return_value.__enter__ = MagicMock(return_value=mock_client)
        MockClient.return_value.__exit__ = MagicMock(return_value=False)

        result = run_alertes_job()

        assert result.doublons_ignores == 1
        assert result.alertes_creees == 0
        assert result.succes is True  # les doublons ne sont pas des erreurs

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job.SpringAPIClient")
    @patch("jobs.alertes_job._log_sync")
    def test_erreur_reseau_comptee_mais_job_continue(
        self, mock_log, MockClient, mock_extract
    ):
        mock_extract.return_value = [
            {
                "id_pret": "PRE-001",
                "jours_retard": 45,
                "solde_restant": Decimal("300000"),
            },
            {
                "id_pret": "PRE-002",
                "jours_retard": 60,
                "solde_restant": Decimal("200000"),
            },
        ]
        mock_client = MagicMock()
        # PRE-001 → erreur réseau, PRE-002 → succès
        mock_client.creer_alerte.side_effect = [
            NetworkError("http://api/internal/alertes"),
            {"id": 2, "id_pret": "PRE-002", "statut": "ACTIVE"},
        ]
        MockClient.return_value.__enter__ = MagicMock(return_value=mock_client)
        MockClient.return_value.__exit__ = MagicMock(return_value=False)

        result = run_alertes_job()

        assert result.erreurs == 1
        assert result.alertes_creees == 1
        # Avec des erreurs, succes = False
        assert result.succes is False

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job._log_sync")
    def test_schema_not_found_leve_job_error(self, mock_log, mock_extract):
        mock_extract.side_effect = SchemaNotFoundError("staging", "stg_prets")

        with pytest.raises(JobError) as exc_info:
            run_alertes_job()

        assert (
            "staging" in str(exc_info.value).lower()
            or "stg_prets" in str(exc_info.value).lower()
        )

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job._log_sync")
    def test_extraction_error_leve_job_error(self, mock_log, mock_extract):
        mock_extract.side_effect = ExtractionError(
            "staging.stg_prets", "requête échouée"
        )

        with pytest.raises(JobError):
            run_alertes_job()

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job.SpringAPIClient")
    @patch("jobs.alertes_job._log_sync")
    def test_multiple_prets_tous_crees(self, mock_log, MockClient, mock_extract):
        mock_extract.return_value = [
            {
                "id_pret": f"PRE-{i:03d}",
                "jours_retard": 30 + i,
                "solde_restant": Decimal("100000"),
            }
            for i in range(5)
        ]
        mock_client = MagicMock()
        mock_client.creer_alerte.return_value = {"id": 1, "statut": "ACTIVE"}
        MockClient.return_value.__enter__ = MagicMock(return_value=mock_client)
        MockClient.return_value.__exit__ = MagicMock(return_value=False)

        result = run_alertes_job()

        assert result.total_prets_en_retard == 5
        assert result.alertes_creees == 5
        assert result.succes is True

    @patch("jobs.alertes_job.extract_prets_en_retard")
    @patch("jobs.alertes_job.SpringAPIClient")
    @patch("jobs.alertes_job._log_sync")
    def test_result_a_duree_positive(self, mock_log, MockClient, mock_extract):
        mock_extract.return_value = []

        result = run_alertes_job()

        assert result.fin is not None
        assert result.duree_secondes >= 0
