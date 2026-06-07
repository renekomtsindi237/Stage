"""
test_extractors.py — Tests des extracteurs (mocking de la base de données).
"""

from __future__ import annotations

from decimal import Decimal
from unittest.mock import MagicMock, patch

import pytest

from exceptions import (
    ColumnMissingError,
    EmptyDatasetError,
    ExtractionError,
    SchemaNotFoundError,
)
from extractors.collecte_extractor import extract_collectes_confirmees
from extractors.pret_extractor import (
    REQUIRED_COLUMNS,
    _validate_row,
    extract_all_prets_actifs,
    extract_prets_en_retard,
)


def _sample_pret_row(id_pret: str = "PRE-001", jours_retard: int = 45) -> dict:
    return {
        "id_pret": id_pret,
        "id_client": "CLI-001",
        "nom_client": "Dupont",
        "nom_agence": "Yaoundé Centre",
        "nom_agent": "agent01",
        "montant_pret": Decimal("1000000"),
        "date_deblocage": "2024-01-01",
        "date_echeance": "2025-01-01",
        "montant_rembourse": Decimal("200000"),
        "solde_restant": Decimal("800000"),
        "statut_pret": "ACTIF",
        "jours_retard": jours_retard,
    }


# ── Tests _validate_row ───────────────────────────────────────────────────────


class TestValidateRow:

    def test_row_complet_valide(self):
        row = _sample_pret_row()
        _validate_row(row, "test_source")  # ne doit pas lever

    def test_colonne_manquante_leve_column_missing(self):
        row = _sample_pret_row()
        del row["solde_restant"]
        with pytest.raises(ColumnMissingError) as exc_info:
            _validate_row(row, "staging.stg_prets")
        assert exc_info.value.column == "solde_restant"

    def test_toutes_colonnes_requises_presentes(self):
        row = _sample_pret_row()
        for col in REQUIRED_COLUMNS:
            assert col in row, f"Colonne requise '{col}' absente du fixture"


# ── Tests extract_prets_en_retard ─────────────────────────────────────────────


class TestExtractPretsEnRetard:

    @patch("extractors.pret_extractor.check_table_exists", return_value=False)
    def test_table_absente_leve_schema_not_found(self, _):
        with pytest.raises(SchemaNotFoundError):
            extract_prets_en_retard()

    @patch("extractors.pret_extractor.check_table_exists", return_value=True)
    @patch("extractors.pret_extractor.readonly_session")
    def test_retourne_liste_vide_si_aucun_retard(self, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = []
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = extract_prets_en_retard()
        assert result == []

    @patch("extractors.pret_extractor.check_table_exists", return_value=True)
    @patch("extractors.pret_extractor.readonly_session")
    def test_retourne_prets_avec_conversion_decimal(self, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = [_sample_pret_row("PRE-001", 45)]
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = extract_prets_en_retard(min_jours_retard=30)
        assert len(result) == 1
        assert isinstance(result[0]["solde_restant"], Decimal)
        assert result[0]["jours_retard"] == 45

    @patch("extractors.pret_extractor.check_table_exists", return_value=True)
    @patch("extractors.pret_extractor.readonly_session")
    def test_exception_sql_leve_extraction_error(self, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.side_effect = RuntimeError("connection lost")
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        with pytest.raises(ExtractionError):
            extract_prets_en_retard()


# ── Tests extract_all_prets_actifs ────────────────────────────────────────────


class TestExtractAllPretsActifs:

    @patch("extractors.pret_extractor.check_table_exists", return_value=True)
    @patch("extractors.pret_extractor.readonly_session")
    def test_aucun_pret_actif_leve_empty_dataset(self, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = []
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        with pytest.raises(EmptyDatasetError):
            extract_all_prets_actifs()

    @patch("extractors.pret_extractor.check_table_exists", return_value=True)
    @patch("extractors.pret_extractor.readonly_session")
    def test_retourne_prets_actifs(self, mock_session, _):
        rows = [_sample_pret_row(f"PRE-{i:03d}", i * 10) for i in range(3)]
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = rows
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = extract_all_prets_actifs()
        assert len(result) == 3


# ── Tests extract_collectes_confirmees ────────────────────────────────────────


class TestExtractCollectesConfirmees:

    @patch("extractors.collecte_extractor.check_table_exists", return_value=False)
    def test_table_absente_leve_schema_not_found(self, _):
        with pytest.raises(SchemaNotFoundError):
            extract_collectes_confirmees()

    @patch("extractors.collecte_extractor.check_table_exists", return_value=True)
    @patch("extractors.collecte_extractor.readonly_session")
    def test_retourne_collectes(self, mock_session, _):
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = [
            {
                "id": 1,
                "id_pret": "PRE-001",
                "agent_id": 1,
                "nom_agent": "ag01",
                "montant": Decimal("50000"),
                "canal": "ESPECES",
                "latitude": None,
                "longitude": None,
                "date_collecte": "2024-03-01",
                "statut": "CONFIRMEE",
                "created_at": "2024-03-01",
            }
        ]
        mock_session.return_value.__enter__ = MagicMock(return_value=mock_cur)
        mock_session.return_value.__exit__ = MagicMock(return_value=False)

        result = extract_collectes_confirmees(since_id=0)
        assert len(result) == 1
        assert result[0]["montant"] == Decimal("50000")
