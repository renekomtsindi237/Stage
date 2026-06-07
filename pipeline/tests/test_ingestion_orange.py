"""
test_ingestion_orange.py
Tests unitaires pour l'ingestion Orange Money.
"""
import pytest
import pandas as pd
from scripts.ingestion_utils import lire_csv_orange, calculer_hash_ligne

CSV_ORANGE_VALIDE = """txn_id,date_heure,montant_xaf,msisdn_payeur,prenom_nom,ref_paiement,etat,nature
ORG001,2026-04-01 08:30,7500,237690001122,Fatima Abbo,PREF001,success,paiement
ORG002,01/04/2026,15000,237680445566,Hamidou Waziri,PREF002,completed,paiement
ORG003,2026-04-01,250,237690773311,Test Petit,PREF003,ok,paiement
"""

CSV_ORANGE_ENCODAGE_BOM = "\ufeff" + CSV_ORANGE_VALIDE


@pytest.fixture
def fichier_orange_valide(tmp_path):
    f = tmp_path / "orange_20260401.csv"
    f.write_text(CSV_ORANGE_VALIDE, encoding="utf-8")
    return str(f)


@pytest.fixture
def fichier_orange_bom(tmp_path):
    f = tmp_path / "orange_bom.csv"
    f.write_text(CSV_ORANGE_ENCODAGE_BOM, encoding="utf-8-sig")
    return str(f)


def test_lecture_orange_retourne_dataframe(fichier_orange_valide):
    df = lire_csv_orange(fichier_orange_valide)
    assert isinstance(df, pd.DataFrame)
    assert len(df) == 3


def test_lecture_orange_colonnes_presentes(fichier_orange_valide):
    df = lire_csv_orange(fichier_orange_valide)
    for col in ["transaction_id", "date_transaction", "montant", "hash_sha256"]:
        assert col in df.columns


def test_lecture_orange_mapping_colonnes(fichier_orange_valide):
    """Vérifie que le renommage des colonnes Orange fonctionne."""
    df = lire_csv_orange(fichier_orange_valide)
    assert "transaction_id" in df.columns       # renommé depuis txn_id
    assert "telephone_payeur" in df.columns     # renommé depuis msisdn_payeur


def test_lecture_orange_gere_bom(fichier_orange_bom):
    """Un fichier UTF-8 avec BOM doit être lu sans erreur."""
    df = lire_csv_orange(fichier_orange_bom)
    assert len(df) > 0
    # La première colonne ne doit pas avoir de BOM dans son nom
    assert not df.columns[0].startswith("\ufeff")


def test_hashes_orange_uniques(fichier_orange_valide):
    df = lire_csv_orange(fichier_orange_valide)
    assert df["hash_sha256"].is_unique
