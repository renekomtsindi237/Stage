"""
Extraction des champs d'une carte de séjour (ressortissants étrangers).

Beaucoup de cartes de séjour suivent aussi le format TD1 (MRZ 3×30) — on
tente la MRZ en premier, puis un repli par étiquette comme pour la CNI.
"""

from __future__ import annotations

from .. import mrz, ocr
from ..schema import ChampExtrait, ResultatExtraction, TypePiece
from ._utils import chercher_apres_etiquette, normaliser_date


def extraire(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.CARTE_SEJOUR)
    try:
        lignes_mrz = ocr.extraire_zone_mrz(image_bytes)
        parsed = mrz.detecter_et_parser(lignes_mrz)

        if parsed is not None:
            resultat.mrz_valide = parsed.valide
            confiance = 0.95 if parsed.valide else 0.55
            for champ, valeur in parsed.champs.items():
                if valeur:
                    resultat.champs[champ] = ChampExtrait(valeur, confiance, "mrz")
            resultat.erreurs.extend(parsed.erreurs)
        else:
            resultat.mrz_valide = False

        lignes = ocr.extraire_lignes(image_bytes)
        resultat.texte_brut = "\n".join(lignes)

        if "nom" not in resultat.champs:
            nom = chercher_apres_etiquette(lignes, ["NOM", "SURNAME"])
            if nom:
                resultat.champs["nom"] = ChampExtrait(nom.strip(), 0.5, "ocr_layout")

        if "numeroPiece" not in resultat.champs:
            numero = chercher_apres_etiquette(lignes, ["N°", "NUMERO"])
            if numero:
                resultat.champs["numeroPiece"] = ChampExtrait(
                    numero.strip(), 0.5, "ocr_layout"
                )

        if "dateExpirationPiece" not in resultat.champs:
            expiration_brut = chercher_apres_etiquette(
                lignes, ["EXPIRATION", "VALABLE"]
            )
            if expiration_brut:
                date_norm = normaliser_date(expiration_brut)
                if date_norm:
                    resultat.champs["dateExpirationPiece"] = ChampExtrait(
                        date_norm, 0.55, "ocr_regex"
                    )

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat
