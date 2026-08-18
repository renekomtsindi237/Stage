"""Extraction des champs d'un passeport (biométrique, format TD3 ICAO)."""

from __future__ import annotations

from .. import mrz, ocr
from ..schema import ChampExtrait, ResultatExtraction, TypePiece


def extraire(image_bytes: bytes) -> ResultatExtraction:
    resultat = ResultatExtraction(type_piece=TypePiece.PASSEPORT)

    try:
        # 1) Zone MRZ dédiée (bas de page) — la plus fiable
        lignes_mrz = ocr.extraire_zone_mrz(image_bytes)
        parsed = mrz.detecter_et_parser(lignes_mrz)

        if parsed is None:
            # 2) Repli : OCR pleine page, la MRZ peut apparaître ailleurs
            #    selon le cadrage du scan
            lignes_pleine_page = ocr.extraire_lignes(image_bytes)
            parsed = mrz.detecter_et_parser(lignes_pleine_page)

        if parsed is not None:
            resultat.mrz_valide = parsed.valide
            confiance = 0.98 if parsed.valide else 0.6
            for champ, valeur in parsed.champs.items():
                if valeur:
                    resultat.champs[champ] = ChampExtrait(
                        valeur=valeur, confiance=confiance, source="mrz"
                    )
            resultat.erreurs.extend(parsed.erreurs)
        else:
            resultat.mrz_valide = False
            resultat.erreurs.append(
                "Zone MRZ non détectée — vérifier le cadrage/la qualité du scan."
            )

        resultat.texte_brut = "\n".join(lignes_mrz)

    except ocr.OcrIndisponible as exc:
        resultat.erreurs.append(str(exc))

    return resultat
