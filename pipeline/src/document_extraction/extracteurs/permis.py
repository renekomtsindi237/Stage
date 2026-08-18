"""
Extraction des champs d'un permis de conduire camerounais.

Le permis camerounais utilise une mise en page à CHAMPS NUMÉROTÉS (norme
CEDEAO/CEMAC) plutôt que des étiquettes en toutes lettres sur le recto —
la légende (1=Nom, 2=Prénom, 3=Date et lieu de naissance, 4a=Délivré le,
4b=Expire le, 4c=Délivré par, 5=N° permis) est imprimée séparément (souvent
au verso) mais la numérotation est fixe et documentée officiellement, donc
exploitable directement sans avoir besoin de lire la légende sur chaque
scan. On retente un repli par étiquette classique si le motif numéroté
n'est pas détecté (variantes de mise en page plus anciennes).

Pas de zone MRZ standardisée sur ce type de document — confiance plus
faible que CNI/passeport, cohérent avec son statut de pièce "en
complément" côté backend (TypeDocumentKyc).
"""

from __future__ import annotations

import re

from .. import ocr
from ..schema import ChampExtrait, ResultatExtraction, TypePiece
from ._utils import chercher_apres_etiquette, normaliser_date

# Numérotation officielle des champs du permis de conduire (norme CEMAC) :
#   1 = Nom · 2 = Prénom · 3 = Date et lieu de naissance
#   4a = Délivré le · 4b = Expire le · 4c = Délivré par · 5 = N° permis
_CHAMP_NUMEROTE = re.compile(r"\b(\d[a-z]?)\.\s*([^\d]+?)(?=\s+\d[a-z]?\.|$)")


def _extraire_champs_numerotes(lignes: list[str]) -> dict[str, str]:
    """Fusionne toutes les paires 'code. valeur' trouvées sur l'ensemble des lignes."""
    champs: dict[str, str] = {}
    for ligne in lignes:
        for code, valeur in _CHAMP_NUMEROTE.findall(ligne):
            valeur = valeur.strip(" .,")
            if valeur:
                champs.setdefault(code.lower(), valeur)
    return champs


def extraire(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.PERMIS_CONDUIRE)
    try:
        lignes = ocr.extraire_lignes(image_bytes)
        resultat.texte_brut = "\n".join(lignes)
        resultat.mrz_valide = None

        numerotes = _extraire_champs_numerotes(lignes)

        if "1" in numerotes:
            resultat.champs["nom"] = ChampExtrait(numerotes["1"], 0.75, "ocr_regex")
        if "2" in numerotes:
            resultat.champs["prenom"] = ChampExtrait(numerotes["2"], 0.75, "ocr_regex")
        if "3" in numerotes:
            # "09-01-2003, YAOUNDE 5E" → date de naissance + lieu de naissance
            date_part, _, lieu_part = numerotes["3"].partition(",")
            date_norm = normaliser_date(date_part)
            if date_norm:
                resultat.champs["dateNaissance"] = ChampExtrait(
                    date_norm, 0.75, "ocr_regex"
                )
            if lieu_part.strip():
                resultat.champs["lieuNaissance"] = ChampExtrait(
                    lieu_part.strip(), 0.65, "ocr_regex"
                )
        if "4a" in numerotes:
            date_norm = normaliser_date(numerotes["4a"])
            if date_norm:
                resultat.champs["dateEmissionPiece"] = ChampExtrait(
                    date_norm, 0.75, "ocr_regex"
                )
        if "4b" in numerotes:
            date_norm = normaliser_date(numerotes["4b"])
            if date_norm:
                resultat.champs["dateExpirationPiece"] = ChampExtrait(
                    date_norm, 0.75, "ocr_regex"
                )
        if "4c" in numerotes:
            resultat.champs["lieuEmissionPiece"] = ChampExtrait(
                numerotes["4c"], 0.6, "ocr_regex"
            )
        if "5" in numerotes:
            resultat.champs["numeroPiece"] = ChampExtrait(
                numerotes["5"], 0.75, "ocr_regex"
            )

        # Repli étiquette classique pour les champs non trouvés via la numérotation
        # (anciennes mises en page, ou face verso ne contenant que la légende)
        if "nom" not in resultat.champs:
            nom = chercher_apres_etiquette(lignes, ["NOM", "SURNAME"])
            if nom:
                resultat.champs["nom"] = ChampExtrait(nom.strip(), 0.5, "ocr_layout")

        if "prenom" not in resultat.champs:
            prenom = chercher_apres_etiquette(
                lignes, ["PRENOMS", "PRENOM", "GIVEN NAMES"]
            )
            if prenom:
                resultat.champs["prenom"] = ChampExtrait(
                    prenom.strip(), 0.5, "ocr_layout"
                )

        if "dateNaissance" not in resultat.champs:
            naissance_brut = chercher_apres_etiquette(
                lignes, ["DATE DE NAISSANCE", "NE(E) LE", "DATE OF BIRTH"]
            )
            if naissance_brut:
                date_norm = normaliser_date(naissance_brut)
                if date_norm:
                    resultat.champs["dateNaissance"] = ChampExtrait(
                        date_norm, 0.55, "ocr_regex"
                    )

        if "numeroPiece" not in resultat.champs:
            numero = chercher_apres_etiquette(lignes, ["N°", "NUMERO", "N DE PERMIS"])
            if numero:
                resultat.champs["numeroPiece"] = ChampExtrait(
                    numero.strip(), 0.45, "ocr_layout"
                )

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat
