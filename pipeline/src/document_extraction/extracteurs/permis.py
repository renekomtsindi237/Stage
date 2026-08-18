"""
Extraction des champs d'un permis de conduire camerounais.

Pas de zone MRZ standardisée sur ce type de document — extraction par
étiquette uniquement (confiance plus faible que CNI/passeport, document
accepté "en complément" seulement — cf. TypeDocumentKyc côté backend).
"""

from __future__ import annotations

from .. import ocr
from ..schema import ChampExtrait, ResultatExtraction, TypePiece
from ._utils import chercher_apres_etiquette, normaliser_date


def extraire(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.PERMIS_CONDUIRE)
    try:
        lignes = ocr.extraire_lignes(image_bytes)
        resultat.texte_brut = "\n".join(lignes)
        resultat.mrz_valide = None

        nom = chercher_apres_etiquette(lignes, ["NOM", "SURNAME"])
        if nom:
            resultat.champs["nom"] = ChampExtrait(nom.strip(), 0.55, "ocr_layout")

        prenom = chercher_apres_etiquette(lignes, ["PRENOMS", "PRENOM", "GIVEN NAMES"])
        if prenom:
            resultat.champs["prenom"] = ChampExtrait(prenom.strip(), 0.55, "ocr_layout")

        naissance_brut = chercher_apres_etiquette(
            lignes, ["DATE DE NAISSANCE", "NE(E) LE", "DATE OF BIRTH"]
        )
        if naissance_brut:
            date_norm = normaliser_date(naissance_brut)
            resultat.champs["dateNaissance"] = ChampExtrait(
                date_norm or naissance_brut, 0.6 if date_norm else 0.3, "ocr_regex"
            )

        numero = chercher_apres_etiquette(lignes, ["N°", "NUMERO", "N DE PERMIS"])
        if numero:
            resultat.champs["numeroPiece"] = ChampExtrait(
                numero.strip(), 0.5, "ocr_layout"
            )

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat
