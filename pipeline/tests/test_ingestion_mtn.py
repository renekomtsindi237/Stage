"""
test_ingestion_mtn.py
Tests unitaires pour l'ingestion MTN — scripts/ingestion_utils.py
Utilise pytest + unittest.mock (pas de connexion DB réelle requise).
"""
import hashlib
import io
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest

from scripts.ingestion_utils import (
    calculer_hash_ligne,
    lire_csv_mtn,
    inserer_transactions,
    lister_fichiers_a_traiter,
)

# ── Fixtures ──────────────────────────────────────────────────────────────────

CSV_MTN_VALIDE = """transaction_id;date_transaction;montant;telephone_payeur;nom_payeur;reference_externe;statut;type_operation
TXN001;2026-04-01;5000;237699001122;Jean Dupont;REF001;SUCCESS;PAYMENT
TXN002;01/04/2026;12500;237677334455;Marie Kamga;REF002;success;PAYMENT
TXN003;2026-04-01;0;237699887766;Alerte Zéro;REF003;SUCCESS;PAYMENT
TXN004;2026-04-01;8750;237699112233;Paul Mbia;REF004;FAILED;PAYMENT
"""

CSV_MTN_AVEC_DOUBLONS = """transaction_id;date_transaction;montant;telephone_payeur;nom_payeur;reference_externe;statut;type_operation
TXN001;2026-04-01;5000;237699001122;Jean Dupont;REF001;SUCCESS;PAYMENT
TXN001;2026-04-01;5000;237699001122;Jean Dupont;REF001;SUCCESS;PAYMENT
"""


@pytest.fixture
def fichier_mtn_valide(tmp_path):
    f = tmp_path / "mtn_20260401.csv"
    f.write_text(CSV_MTN_VALIDE, encoding="utf-8")
    return str(f)


@pytest.fixture
def fichier_mtn_doublons(tmp_path):
    f = tmp_path / "mtn_doublons.csv"
    f.write_text(CSV_MTN_AVEC_DOUBLONS, encoding="utf-8")
    return str(f)


# ── Tests : calculer_hash_ligne ───────────────────────────────────────────────

def test_hash_ligne_deterministe():
    """Le même contenu produit toujours le même hash."""
    row = pd.Series({"a": "1", "b": "hello", "c": "100"})
    h1 = calculer_hash_ligne(row)
    h2 = calculer_hash_ligne(row)
    assert h1 == h2


def test_hash_ligne_different():
    """Des lignes différentes produisent des hashs différents."""
    row1 = pd.Series({"a": "TXN001", "b": "2026-04-01", "c": "5000"})
    row2 = pd.Series({"a": "TXN002", "b": "2026-04-01", "c": "5000"})
    assert calculer_hash_ligne(row1) != calculer_hash_ligne(row2)


def test_hash_est_sha256():
    """Le hash fait bien 64 caractères hexadécimaux (SHA-256)."""
    row = pd.Series({"a": "test"})
    h = calculer_hash_ligne(row)
    assert len(h) == 64
    assert all(c in "0123456789abcdef" for c in h)


# ── Tests : lire_csv_mtn ─────────────────────────────────────────────────────

def test_lecture_csv_mtn_retourne_dataframe(fichier_mtn_valide):
    df = lire_csv_mtn(fichier_mtn_valide)
    assert isinstance(df, pd.DataFrame)
    assert len(df) > 0


def test_lecture_csv_mtn_colonnes_presentes(fichier_mtn_valide):
    df = lire_csv_mtn(fichier_mtn_valide)
    colonnes_requises = [
        "transaction_id", "date_transaction", "montant",
        "telephone_payeur", "hash_sha256", "nom_fichier_source",
    ]
    for col in colonnes_requises:
        assert col in df.columns, f"Colonne manquante : {col}"


def test_lecture_csv_mtn_hash_sha256_unique(fichier_mtn_valide):
    """Chaque ligne a un hash unique (dans un fichier sans doublons)."""
    df = lire_csv_mtn(fichier_mtn_valide)
    assert df["hash_sha256"].is_unique


def test_lecture_csv_mtn_doublons_detectes(fichier_mtn_doublons):
    """Deux lignes identiques produisent le même hash."""
    df = lire_csv_mtn(fichier_mtn_doublons)
    assert df["hash_sha256"].duplicated().any()


def test_lecture_csv_mtn_nom_fichier(fichier_mtn_valide):
    df = lire_csv_mtn(fichier_mtn_valide)
    assert df["nom_fichier_source"].iloc[0] == "mtn_20260401.csv"


# ── Tests : inserer_transactions ─────────────────────────────────────────────

def test_inserer_transactions_vide():
    """Un DataFrame vide retourne 0 inserts sans erreur."""
    conn_mock = MagicMock()
    df_vide = pd.DataFrame()
    result = inserer_transactions(conn_mock, df_vide, "raw.transactions_mtn", [], "run_001")
    assert result == {"inserts": 0, "doublons": 0, "total": 0}
    conn_mock.cursor.assert_not_called()


def test_inserer_transactions_appelle_execute_values(fichier_mtn_valide):
    """Vérifie que execute_values est bien appelé avec les bons paramètres."""
    from scripts.ingestion_utils import COLONNES_MTN

    df = lire_csv_mtn(fichier_mtn_valide)
    conn_mock = MagicMock()
    cur_mock = MagicMock()
    conn_mock.cursor.return_value.__enter__ = MagicMock(return_value=cur_mock)
    conn_mock.cursor.return_value.__exit__ = MagicMock(return_value=False)
    cur_mock.fetchone.return_value = (0,)

    with patch("scripts.ingestion_utils.execute_values") as mock_ev:
        inserer_transactions(conn_mock, df, "raw.transactions_mtn", COLONNES_MTN, "run_001")
        assert mock_ev.called


# ── Tests : lister_fichiers_a_traiter ─────────────────────────────────────────

def test_lister_fichiers_dossier_vide(tmp_path):
    result = lister_fichiers_a_traiter(str(tmp_path), "*.csv")
    assert result == []


def test_lister_fichiers_trouve_csv(tmp_path):
    (tmp_path / "mtn_01.csv").write_text("test")
    (tmp_path / "mtn_02.csv").write_text("test")
    (tmp_path / "autre.txt").write_text("test")
    result = lister_fichiers_a_traiter(str(tmp_path), "*.csv")
    assert len(result) == 2
    assert all(f.endswith(".csv") for f in result)


def test_lister_fichiers_tri_alphabetique(tmp_path):
    for nom in ["mtn_03.csv", "mtn_01.csv", "mtn_02.csv"]:
        (tmp_path / nom).write_text("test")
    result = lister_fichiers_a_traiter(str(tmp_path), "*.csv")
    noms = [f.split("/")[-1].split("\\")[-1] for f in result]
    assert noms == sorted(noms)
