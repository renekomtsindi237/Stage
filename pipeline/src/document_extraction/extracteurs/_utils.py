"""Utilitaires partagés par les extracteurs à base de règles (regex/layout)."""

from __future__ import annotations

import re

_DATE_RE = re.compile(r"\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})\b")


def normaliser_date(texte: str) -> str | None:
    """'22.07.2035' ou '22/07/35' → '2035-07-22' (ISO). None si aucun match."""
    m = _DATE_RE.search(texte)
    if not m:
        return None
    jour, mois, annee = m.groups()
    if len(annee) == 2:
        annee = ("19" if int(annee) > 50 else "20") + annee
    try:
        return f"{int(annee):04d}-{int(mois):02d}-{int(jour):02d}"
    except ValueError:
        return None


def _retirer_etiquettes(ligne: str, etiquettes: list[str]) -> str:
    """Retire toutes les variantes d'étiquette (souvent bilingues FR/EN sur
    une même ligne, ex: 'LIEU DE NAISSANCE / PLACE OF BIRTH') et les
    séparateurs associés, pour ne garder que ce qui reste potentiellement
    comme valeur."""
    reste = ligne
    for etq in sorted(etiquettes, key=len, reverse=True):
        reste = re.sub(re.escape(etq), "", reste, flags=re.IGNORECASE)
    return reste.strip(" :/|\t-")


def chercher_apres_etiquette(
    lignes: list[str], etiquettes: list[str], meme_ligne_dabord: bool = True
) -> str | None:
    """
    Cherche une étiquette (ex: "Date de naissance") dans les lignes OCR et
    retourne le texte utile associé.

    Gère le cas fréquent des libellés bilingues sur une même ligne
    ("LIEU DE NAISSANCE / PLACE OF BIRTH") en retirant TOUTES les variantes
    connues de l'étiquette avant de considérer le reste comme valeur — sinon
    la traduction anglaise du libellé serait prise à tort pour la valeur.
    Si rien d'exploitable ne reste sur la même ligne, prend la ligne
    suivante (mise en page où le libellé et la valeur sont séparés).
    """
    lignes_maj = [ligne.upper() for ligne in lignes]
    for i, ligne in enumerate(lignes):
        if not any(etq.upper() in lignes_maj[i] for etq in etiquettes):
            continue

        if meme_ligne_dabord:
            reste = _retirer_etiquettes(ligne, etiquettes)
            if len(reste) >= 2:
                return reste

        if i + 1 < len(lignes):
            suivante = lignes[i + 1].strip()
            suivante_nettoyee = _retirer_etiquettes(suivante, etiquettes)
            if len(suivante_nettoyee) >= 2:
                return suivante_nettoyee
    return None
