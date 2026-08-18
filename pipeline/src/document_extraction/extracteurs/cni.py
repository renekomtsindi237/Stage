"""
Extraction des champs de la Carte Nationale d'Identité camerounaise.

Plusieurs mises en page circulent (ancien format vs. format à puce plus
récent) et la répartition exacte des champs entre recto et verso varie
selon la génération de carte — lieu de naissance, profession et taille
peuvent apparaître sur l'une ou l'autre face. L'extraction par étiquette
est donc appliquée aux DEUX faces indifféremment ; seule la zone MRZ
(présente uniquement au verso dans tous les formats observés) reste
spécifique à `extraire_verso`.

Verso : contient une zone MRZ (format TD1, 3×30 caractères) — utilisée en
priorité pour numéro de pièce / date de naissance / sexe / date d'expiration
(fiabilité proche de 100% si l'OCR a bien lu la zone).
"""

from __future__ import annotations

from .. import mrz, ocr
from ..schema import ChampExtrait, ResultatExtraction, TypePiece
from ._utils import chercher_apres_etiquette, normaliser_date


def _lire_champs_par_etiquette(resultat: ResultatExtraction, lignes: list[str]) -> None:
    """
    Champs susceptibles d'apparaître sur le recto OU le verso selon la
    génération de carte — appliqué aux deux faces, sans écraser une valeur
    déjà obtenue avec une confiance supérieure (ex: MRZ).
    """

    def ajouter(champ: str, valeur: str | None, confiance: float, source: str) -> None:
        if not valeur:
            return
        existant = resultat.champs.get(champ)
        if existant is None or confiance > existant.confiance:
            resultat.champs[champ] = ChampExtrait(valeur.strip(), confiance, source)

    ajouter(
        "nom",
        chercher_apres_etiquette(lignes, ["NOM/SURNAME", "NOM/", "SURNAME", "NOM"]),
        0.7,
        "ocr_layout",
    )
    ajouter(
        "prenom",
        chercher_apres_etiquette(
            lignes, ["PRENOMS/GIVEN NAMES", "PRENOM/", "GIVEN NAMES", "PRENOMS"]
        ),
        0.7,
        "ocr_layout",
    )
    ajouter(
        "lieuNaissance",
        chercher_apres_etiquette(lignes, ["LIEU DE NAISSANCE", "PLACE OF BIRTH"]),
        0.65,
        "ocr_layout",
    )
    ajouter(
        "profession",
        chercher_apres_etiquette(
            lignes, ["PROFESSION/OCCUPATION", "PROFESSION", "OCCUPATION"]
        ),
        0.6,
        "ocr_layout",
    )
    ajouter(
        "taille",
        chercher_apres_etiquette(lignes, ["TAILLE/HEIGHT", "TAILLE", "HEIGHT"]),
        0.55,
        "ocr_layout",
    )

    naissance_brut = chercher_apres_etiquette(
        lignes, ["DATE DE NAISSANCE", "DATE OF BIRTH"]
    )
    if naissance_brut:
        date_norm = normaliser_date(naissance_brut)
        ajouter(
            "dateNaissance",
            date_norm or naissance_brut,
            0.75 if date_norm else 0.4,
            "ocr_regex",
        )

    expiration_brut = chercher_apres_etiquette(
        lignes, ["DATE D'EXPIRATION", "DATE D'EXPIRY", "DATE OF EXPIRY", "EXPIRY"]
    )
    if expiration_brut:
        date_norm = normaliser_date(expiration_brut)
        ajouter(
            "dateExpirationPiece",
            date_norm or expiration_brut,
            0.75 if date_norm else 0.4,
            "ocr_regex",
        )

    delivrance_brut = chercher_apres_etiquette(
        lignes, ["DATE DE DELIVRANCE", "DATE OF ISSUE"]
    )
    if delivrance_brut:
        date_norm = normaliser_date(delivrance_brut)
        ajouter(
            "dateEmissionPiece",
            date_norm or delivrance_brut,
            0.7 if date_norm else 0.4,
            "ocr_regex",
        )

    lieu_delivrance = chercher_apres_etiquette(
        lignes, ["LE DGSN/THE DGSN", "LIEU DE DELIVRANCE"]
    )
    if lieu_delivrance:
        ajouter("lieuEmissionPiece", lieu_delivrance, 0.55, "ocr_layout")

    if "sexe" not in resultat.champs:
        for ligne in lignes:
            if ligne.strip().upper() in ("M", "F", "SEXE/SEX", "SEX"):
                continue
            if ligne.strip() in ("M", "F"):
                resultat.champs["sexe"] = ChampExtrait(ligne.strip(), 0.6, "ocr_layout")
                break


def extraire_recto(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.CNI_RECTO)
    try:
        lignes = ocr.extraire_lignes(image_bytes)
        resultat.texte_brut = "\n".join(lignes)
        resultat.mrz_valide = None  # pas de MRZ sur le recto (tous formats observés)
        _lire_champs_par_etiquette(resultat, lignes)
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
        _lire_champs_par_etiquette(resultat, lignes)

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat
