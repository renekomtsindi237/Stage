"""
test_echeances_job.py — Tests du job de mise à jour des échéances.
"""

from __future__ import annotations

from datetime import date
from unittest.mock import MagicMock, patch

import pytest
from exceptions import JobError, SchemaNotFoundError
from jobs.echeances_job import EcheancesJobResult, run_echeances_job


class TestRunEcheancesJob:

    @patch("jobs.echeances_job.check_table_exists", return_value=True)
    @patch("jobs.echeances_job.db_session")
    @patch("jobs.echeances_job._log_sync")
    def test_mise_a_jour_echeances_en_retard(self, mock_log, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = [{"id": 1}, {"id": 2}, {"id": 3}]
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = run_echeances_job(reference_date=date(2024, 12, 31))

        assert result.succes is True
        assert result.mises_a_jour == 3

    @patch("jobs.echeances_job.check_table_exists", return_value=True)
    @patch("jobs.echeances_job.db_session")
    @patch("jobs.echeances_job._log_sync")
    def test_aucune_echeance_en_retard(self, mock_log, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = []
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = run_echeances_job(reference_date=date(2020, 1, 1))

        assert result.succes is True
        assert result.mises_a_jour == 0

    @patch("jobs.echeances_job.check_table_exists", return_value=False)
    @patch("jobs.echeances_job._log_sync")
    def test_table_manquante_leve_job_error(self, mock_log, _):
        with pytest.raises(JobError) as exc_info:
            run_echeances_job()
        assert "echeances" in exc_info.value.job_name

    @patch("jobs.echeances_job.check_table_exists", return_value=True)
    @patch("jobs.echeances_job.db_session")
    @patch("jobs.echeances_job._log_sync")
    def test_erreur_sql_leve_job_error(self, mock_log, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.side_effect = RuntimeError("SQL error")
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(
            side_effect=RuntimeError("SQL error")
        )

        with pytest.raises(JobError):
            run_echeances_job()

    @patch("jobs.echeances_job.check_table_exists", return_value=True)
    @patch("jobs.echeances_job.db_session")
    @patch("jobs.echeances_job._log_sync")
    def test_date_reference_defaut_est_aujourd_hui(self, mock_log, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = []
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = run_echeances_job()  # sans date → date.today()

        assert result.succes is True
        assert result.fin is not None

    @patch("jobs.echeances_job.check_table_exists", return_value=True)
    @patch("jobs.echeances_job.db_session")
    @patch("jobs.echeances_job._log_sync")
    def test_duree_positive(self, mock_log, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = []
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = run_echeances_job()
        assert result.duree_secondes >= 0.0
