"""
Extraction des champs de la Carte Nationale d'Identité camerounaise.

Recto : pas de MRZ — nom, prénoms, date de naissance, sexe, dates de
délivrance/expiration lus par OCR + reconnaissance d'étiquettes bilingues
(FR/EN, mise en page CNI Cameroun).

Verso : contient une zone MRZ (format TD1, 3×30 caractères) — utilisée en
priorité pour numéro de pièce / date de naissance / sexe / date d'expiration
(fiabilité proche de 100% si l'OCR a bien lu la zone). Les champs qui ne sont
pas encodés dans la MRZ (lieu de naissance, profession, nom de la mère) sont
lus par étiquette.
"""

from __future__ import annotations

from .. import mrz, ocr
from ..schema import ChampExtrait, ResultatExtraction, TypePiece
from ._utils import chercher_apres_etiquette, normaliser_date


def extraire_recto(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.CNI_RECTO)
    try:
        lignes = ocr.extraire_lignes(image_bytes)
        resultat.texte_brut = "\n".join(lignes)
        resultat.mrz_valide = None  # pas de MRZ sur le recto

        nom = chercher_apres_etiquette(
            lignes, ["NOM/SURNAME", "NOM/", "SURNAME", "NOM"]
        )
        if nom:
            resultat.champs["nom"] = ChampExtrait(nom.strip(), 0.7, "ocr_layout")

        prenom = chercher_apres_etiquette(
            lignes, ["PRENOMS/GIVEN NAMES", "PRENOM/", "GIVEN NAMES", "PRENOMS"]
        )
        if prenom:
            resultat.champs["prenom"] = ChampExtrait(prenom.strip(), 0.7, "ocr_layout")

        naissance_brut = chercher_apres_etiquette(
            lignes, ["DATE DE NAISSANCE", "DATE OF BIRTH"]
        )
        if naissance_brut:
            date_norm = normaliser_date(naissance_brut)
            resultat.champs["dateNaissance"] = ChampExtrait(
                date_norm or naissance_brut, 0.75 if date_norm else 0.4, "ocr_regex"
            )

        expiration_brut = chercher_apres_etiquette(
            lignes, ["DATE D'EXPIRY", "DATE OF EXPIRY", "EXPIRY"]
        )
        if expiration_brut:
            date_norm = normaliser_date(expiration_brut)
            resultat.champs["dateExpirationPiece"] = ChampExtrait(
                date_norm or expiration_brut, 0.75 if date_norm else 0.4, "ocr_regex"
            )

        for ligne in lignes:
            if ligne.strip().upper() in ("M", "F", "SEXE/SEX", "SEX"):
                continue
            if ligne.strip() in ("M", "F"):
                resultat.champs["sexe"] = ChampExtrait(ligne.strip(), 0.6, "ocr_layout")
                break

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat


def extraire_verso(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.CNI_VERSO)
    try:
        lignes_mrz = ocr.extraire_zone_mrz(image_bytes)
        parsed = mrz.detecter_et_parser(lignes_mrz)
        if parsed is None:
            lignes_pleine_page_pour_mrz = ocr.extraire_lignes(image_bytes)
            parsed = mrz.detecter_et_parser(lignes_pleine_page_pour_mrz)

        if parsed is not None:
            resultat.mrz_valide = parsed.valide
            confiance = 0.98 if parsed.valide else 0.6
            for champ, valeur in parsed.champs.items():
                if valeur:
                    resultat.champs[champ] = ChampExtrait(valeur, confiance, "mrz")
            resultat.erreurs.extend(parsed.erreurs)
        else:
            resultat.mrz_valide = False

        lignes = ocr.extraire_lignes(image_bytes)
        resultat.texte_brut = "\n".join(lignes)

        lieu_naissance = chercher_apres_etiquette(
            lignes, ["LIEU DE NAISSANCE", "PLACE OF BIRTH"]
        )
        if lieu_naissance and "lieuNaissance" not in resultat.champs:
            resultat.champs["lieuNaissance"] = ChampExtrait(
                lieu_naissance.strip(), 0.65, "ocr_layout"
            )

        profession = chercher_apres_etiquette(
            lignes, ["PROFESSION/OCCUPATION", "PROFESSION", "OCCUPATION"]
        )
        if profession:
            resultat.champs["profession"] = ChampExtrait(
                profession.strip(), 0.6, "ocr_layout"
            )

        delivrance_brut = chercher_apres_etiquette(
            lignes, ["DATE DE DELIVRANCE", "DATE OF ISSUE"]
        )
        if delivrance_brut:
            date_norm = normaliser_date(delivrance_brut)
            resultat.champs["dateEmissionPiece"] = ChampExtrait(
                date_norm or delivrance_brut, 0.7 if date_norm else 0.4, "ocr_regex"
            )

        lieu_delivrance = chercher_apres_etiquette(
            lignes, ["LE DGSN/THE DGSN", "LIEU DE DELIVRANCE"]
        )
        if lieu_delivrance:
            resultat.champs["lieuEmissionPiece"] = ChampExtrait(
                lieu_delivrance.strip(), 0.55, "ocr_layout"
            )

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat
