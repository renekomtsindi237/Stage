"""
Tests du parseur MRZ — utilisent exclusivement l'exemple officiel publié dans
la norme ICAO Doc 9303 Part 4 (identité fictive "Anna Maria ERIKSSON",
utilisée dans la documentation publique ICAO elle-même) : aucune donnée
personnelle réelle dans ce fichier.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from document_extraction import mrz  # noqa: E402

# Exemple officiel ICAO 9303 Part 4, section 4.2.2 (document type P, TD3)
ICAO_LIGNE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
ICAO_LIGNE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"


def test_lignes_officielles_font_44_caracteres():
    assert len(ICAO_LIGNE1) == 44
    assert len(ICAO_LIGNE2) == 44


def test_check_digit_numero_document():
    # "L898902C3" + check digit '6' — exemple ICAO officiel
    assert mrz.check_digit("L898902C3") == 6


def test_parser_td3_exemple_icao():
    resultat = mrz.parser_td3(ICAO_LIGNE1, ICAO_LIGNE2)
    assert resultat is not None
    assert resultat.valide, resultat.erreurs
    assert resultat.champs["nom"] == "ERIKSSON"
    assert resultat.champs["prenom"] == "ANNA MARIA"
    assert resultat.champs["numeroPiece"] == "L898902C3"
    assert resultat.champs["nationalite"] == "UTO"
    assert resultat.champs["dateNaissance"] == "1974-08-12"
    assert resultat.champs["sexe"] == "F"
    assert resultat.champs["dateExpirationPiece"] == "2012-04-15"


def test_parser_td3_detecte_check_digit_invalide():
    ligne2_corrompue = ICAO_LIGNE2[:9] + "9" + ICAO_LIGNE2[10:]  # check digit faussé
    resultat = mrz.parser_td3(ICAO_LIGNE1, ligne2_corrompue)
    assert resultat is not None
    assert not resultat.valide
    assert any("numéro" in e.lower() for e in resultat.erreurs)


def test_parser_td3_rejette_format_invalide():
    assert mrz.parser_td3("trop court", ICAO_LIGNE2) is None
    assert mrz.parser_td3(ICAO_LIGNE1, "I<UTO...") is None  # ne commence pas par 'P'


def test_detecter_et_parser_ignore_lignes_bruit():
    lignes = [
        "REPUBLIQUE DU CAMEROUN",
        "",
        "   ",
        ICAO_LIGNE1,
        ICAO_LIGNE2,
        "signature illisible",
    ]
    resultat = mrz.detecter_et_parser(lignes)
    assert resultat is not None
    assert resultat.champs["nom"] == "ERIKSSON"


def test_parser_td1_rejette_format_invalide():
    assert mrz.parser_td1("trop court", "x" * 30, "x" * 30) is None


if __name__ == "__main__":
    import traceback

    tests = [v for k, v in list(globals().items()) if k.startswith("test_")]
    echecs = 0
    for t in tests:
        try:
            t()
            print(f"OK   {t.__name__}")
        except Exception:
            echecs += 1
            print(f"FAIL {t.__name__}")
            traceback.print_exc()
    print(f"\n{len(tests) - echecs}/{len(tests)} tests passés")
    sys.exit(1 if echecs else 0)
