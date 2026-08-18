"""
Parseur MRZ (Machine Readable Zone) conforme à la norme ICAO Doc 9303.

Aucune dépendance à un service externe ni à un modèle entraîné : la MRZ est
une zone à format fixe et un algorithme de chiffre de contrôle publics et
déterministes — l'extraction est fiable à 100% dès lors que l'OCR a
correctement lu la zone (police OCR-B, contraste élevé, conçue pour ça).

Formats pris en charge :
  - TD3 (passeports)      : 2 lignes de 44 caractères
  - TD1 (cartes d'identité) : 3 lignes de 30 caractères

Référence : ICAO Doc 9303 Part 4 (TD3) et Part 5 (TD1).
"""

from __future__ import annotations

import re
from dataclasses import dataclass

_WEIGHTS = (7, 3, 1)


def _valeur_caractere(c: str) -> int:
    """0-9 → valeur ; A-Z → 10-35 ; '<' (filler) → 0."""
    if c == "<":
        return 0
    if c.isdigit():
        return int(c)
    if "A" <= c <= "Z":
        return ord(c) - ord("A") + 10
    return 0


def check_digit(chaine: str) -> int:
    """Chiffre de contrôle ICAO 9303 : somme pondérée (7,3,1 répété) mod 10."""
    total = 0
    for i, c in enumerate(chaine):
        total += _valeur_caractere(c) * _WEIGHTS[i % 3]
    return total % 10


def _digit_valide(chaine: str, digit_attendu: str) -> bool:
    if not digit_attendu.isdigit():
        return False
    return check_digit(chaine) == int(digit_attendu)


# Confusions OCR les plus fréquentes sur la police OCR-B des MRZ, entre une
# lettre et le chiffre qui lui ressemble visuellement. Utilisées uniquement
# pour corriger des ZONES CENSÉES ÊTRE NUMÉRIQUES (dates) quand le chiffre
# de contrôle échoue — jamais sur les zones alphabétiques (nom/prénom).
_CONFUSIONS_OCR_NUMERIQUES = {
    "O": "0",
    "I": "1",
    "B": "8",
    "S": "5",
    "Z": "2",
    "G": "6",
}


def _corriger_zone_numerique(chaine: str, digit_attendu: str) -> str | None:
    """
    Si le chiffre de contrôle échoue sur une zone censée être 100% numérique
    (dates), tente les substitutions de confusion OCR usuelles une par une —
    dès qu'une combinaison rend le chiffre de contrôle valide, on l'accepte.
    Retourne la chaîne corrigée, ou None si aucune correction ne fonctionne.
    """
    positions_ambigues = [
        i for i, c in enumerate(chaine) if c in _CONFUSIONS_OCR_NUMERIQUES
    ]
    if not positions_ambigues or not digit_attendu.isdigit():
        return None

    from itertools import combinations

    for taille in range(1, len(positions_ambigues) + 1):
        for combo in combinations(positions_ambigues, taille):
            essai = list(chaine)
            for pos in combo:
                essai[pos] = _CONFUSIONS_OCR_NUMERIQUES[essai[pos]]
            essai_str = "".join(essai)
            if essai_str.isdigit() and check_digit(essai_str) == int(digit_attendu):
                return essai_str
    return None


def _parse_nom(champ_nom: str) -> tuple[str, str]:
    """'KOMTSINDI<<RENE<ALBAN<<<...' → ('KOMTSINDI', 'RENE ALBAN')."""
    parts = champ_nom.rstrip("<").split("<<", 1)
    nom = parts[0].replace("<", " ").strip()
    prenoms = parts[1].replace("<", " ").strip() if len(parts) > 1 else ""
    return nom, prenoms


def _parse_date_yymmdd(yymmdd: str, pivot: int = 30) -> str | None:
    """'030109' → '2003-01-09'. Pivot : <30 → 20xx, >=30 → 19xx (dates de naissance)."""
    if not re.fullmatch(r"\d{6}", yymmdd):
        return None
    yy, mm, dd = int(yymmdd[0:2]), yymmdd[2:4], yymmdd[4:6]
    siecle = 2000 if yy < pivot else 1900
    return f"{siecle + yy:04d}-{mm}-{dd}"


@dataclass
class ResultatMrz:
    champs: dict[str, str]
    valide: bool
    erreurs: list[str]


def parser_td3(ligne1: str, ligne2: str) -> ResultatMrz | None:
    """MRZ passeport (2×44 caractères). Retourne None si le format ne correspond pas."""
    ligne1 = ligne1.strip().upper().replace(" ", "")
    ligne2 = ligne2.strip().upper().replace(" ", "")
    if len(ligne1) != 44 or len(ligne2) != 44:
        return None
    if ligne1[0] != "P":
        return None

    erreurs: list[str] = []
    pays_emission = ligne1[2:5]
    nom, prenoms = _parse_nom(ligne1[5:44])

    numero_piece = ligne2[0:9].replace("<", "").strip()
    if not _digit_valide(ligne2[0:9], ligne2[9]):
        erreurs.append("Chiffre de contrôle invalide sur le numéro de passeport")

    nationalite = ligne2[10:13]

    date_naissance_brute = ligne2[13:19]
    if not _digit_valide(date_naissance_brute, ligne2[19]):
        corrige = _corriger_zone_numerique(date_naissance_brute, ligne2[19])
        if corrige:
            date_naissance_brute = corrige
        else:
            erreurs.append("Chiffre de contrôle invalide sur la date de naissance")
    date_naissance = _parse_date_yymmdd(date_naissance_brute, pivot=30)

    sexe = ligne2[20] if ligne2[20] in ("M", "F") else None

    date_expiration_brute = ligne2[21:27]
    if not _digit_valide(date_expiration_brute, ligne2[27]):
        corrige = _corriger_zone_numerique(date_expiration_brute, ligne2[27])
        if corrige:
            date_expiration_brute = corrige
        else:
            erreurs.append("Chiffre de contrôle invalide sur la date d'expiration")
    date_expiration = _parse_date_yymmdd(date_expiration_brute, pivot=100)

    champs = {
        "nom": nom,
        "prenom": prenoms,
        "numeroPiece": numero_piece,
        "nationalite": nationalite,
        "paysEmission": pays_emission,
        "dateNaissance": date_naissance,
        "sexe": sexe,
        "dateExpirationPiece": date_expiration,
    }
    return ResultatMrz(champs=champs, valide=(len(erreurs) == 0), erreurs=erreurs)


def parser_td1(ligne1: str, ligne2: str, ligne3: str) -> ResultatMrz | None:
    """MRZ carte d'identité (3×30 caractères). Retourne None si le format ne correspond pas."""
    ligne1 = ligne1.strip().upper().replace(" ", "")
    ligne2 = ligne2.strip().upper().replace(" ", "")
    ligne3 = ligne3.strip().upper().replace(" ", "")
    if len(ligne1) != 30 or len(ligne2) != 30 or len(ligne3) != 30:
        return None
    if ligne1[0] not in ("I", "A", "C"):
        return None

    erreurs: list[str] = []
    pays_emission = ligne1[2:5]

    numero_piece = ligne1[5:14].replace("<", "").strip()
    if not _digit_valide(ligne1[5:14], ligne1[14]):
        erreurs.append("Chiffre de contrôle invalide sur le numéro de pièce")

    date_naissance_brute = ligne2[0:6]
    if not _digit_valide(date_naissance_brute, ligne2[6]):
        corrige = _corriger_zone_numerique(date_naissance_brute, ligne2[6])
        if corrige:
            date_naissance_brute = corrige
        else:
            erreurs.append("Chiffre de contrôle invalide sur la date de naissance")
    date_naissance = _parse_date_yymmdd(date_naissance_brute, pivot=30)

    sexe = ligne2[7] if ligne2[7] in ("M", "F") else None

    date_expiration_brute = ligne2[8:14]
    if not _digit_valide(date_expiration_brute, ligne2[14]):
        corrige = _corriger_zone_numerique(date_expiration_brute, ligne2[14])
        if corrige:
            date_expiration_brute = corrige
        else:
            erreurs.append("Chiffre de contrôle invalide sur la date d'expiration")
    date_expiration = _parse_date_yymmdd(date_expiration_brute, pivot=100)

    nationalite = ligne2[15:18]
    nom, prenoms = _parse_nom(ligne3)

    champs = {
        "nom": nom,
        "prenom": prenoms,
        "numeroPiece": numero_piece,
        "nationalite": nationalite,
        "paysEmission": pays_emission,
        "dateNaissance": date_naissance,
        "sexe": sexe,
        "dateExpirationPiece": date_expiration,
    }
    return ResultatMrz(champs=champs, valide=(len(erreurs) == 0), erreurs=erreurs)


_MRZ_CHARS = re.compile(r"[A-Z0-9<]+")


def _plus_longue_sous_chaine_mrz(ligne: str) -> str:
    """
    Isole la plus longue sous-chaîne composée uniquement de caractères MRZ
    valides (A-Z, 0-9, '<'). L'OCR ajoute parfois un caractère parasite en
    début/fin de ligne (accent mal lu, artefact de bordure de carte) — on ne
    veut pas rejeter toute la ligne pour ça.
    """
    correspondances = _MRZ_CHARS.findall(ligne)
    return max(correspondances, key=len, default="")


def detecter_et_parser(lignes_candidates: list[str]) -> ResultatMrz | None:
    """
    Cherche dans une liste de lignes de texte OCR une zone MRZ valide
    (2 lignes de 44 = TD3, ou 3 lignes de 30 = TD1) et la parse.

    Les lignes sont normalisées (espaces retirés, bruit en bordure ignoré)
    avant comparaison de longueur, car l'OCR insère parfois des espaces ou
    caractères parasites autour de la MRZ.
    """
    mrz_like = []
    for ligne_brute in lignes_candidates:
        if not ligne_brute.strip():
            continue
        sous_chaine = _plus_longue_sous_chaine_mrz(
            re.sub(r"\s+", "", ligne_brute.upper())
        )
        if len(sous_chaine) >= 28:
            mrz_like.append(sous_chaine)

    # TD3 : 2 lignes consécutives de 44
    for i in range(len(mrz_like) - 1):
        if len(mrz_like[i]) == 44 and len(mrz_like[i + 1]) == 44:
            resultat = parser_td3(mrz_like[i], mrz_like[i + 1])
            if resultat:
                return resultat

    # TD1 : 3 lignes consécutives de 30
    for i in range(len(mrz_like) - 2):
        if (
            len(mrz_like[i]) == 30
            and len(mrz_like[i + 1]) == 30
            and len(mrz_like[i + 2]) == 30
        ):
            resultat = parser_td1(mrz_like[i], mrz_like[i + 1], mrz_like[i + 2])
            if resultat:
                return resultat

    return None
