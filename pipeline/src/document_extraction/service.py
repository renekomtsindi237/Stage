"""Point d'entrée unique du module document_extraction."""

from __future__ import annotations

import logging

from .extracteurs import carte_sejour, cni, passeport, permis
from .schema import ResultatExtraction, TypePiece

logger = logging.getLogger(__name__)


class DocumentExtractionService:
    """
    Service d'extraction de texte et de champs structurés depuis une pièce
    d'identité scannée. Aucune dépendance à une API externe payante — tout
    tourne en local (Tesseract OCR + règles déterministes MRZ/layout).

    Exemple :
        service = DocumentExtractionService()
        resultat = service.extraire(image_bytes, TypePiece.CNI_RECTO)
        if resultat.confiance_globale < 0.5:
            ...  # signaler pour revue humaine
    """

    def extraire(self, image_bytes: bytes, type_piece: TypePiece) -> ResultatExtraction:
        try:
            if type_piece == TypePiece.PASSEPORT:
                return passeport.extraire(image_bytes)
            if type_piece == TypePiece.CNI_RECTO:
                return cni.extraire_recto(image_bytes)
            if type_piece == TypePiece.CNI_VERSO:
                return cni.extraire_verso(image_bytes)
            if type_piece == TypePiece.PERMIS_CONDUIRE:
                return permis.extraire(image_bytes)
            if type_piece == TypePiece.CARTE_SEJOUR:
                return carte_sejour.extraire(image_bytes)
        except Exception:
            logger.exception("Échec extraction document type=%s", type_piece)

        resultat = ResultatExtraction(type_piece=type_piece)
        resultat.erreurs.append(f"Type de pièce non pris en charge : {type_piece}")
        return resultat
