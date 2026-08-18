"""
document_extraction — extraction de texte et de champs structurés depuis des
pièces d'identité scannées (CNI, passeport, permis, carte de séjour).

Package autonome : ne dépend d'aucun autre module de MicroRecouv (pas
d'import vers cm.imf.*, pas de dépendance à la base de données ou à un
service externe payant). Peut être copié tel quel dans un autre projet —
seule dépendance externe : le binaire Tesseract OCR + les bibliothèques
Python listées dans requirements.txt (pytesseract, pillow, numpy, opencv).

Usage typique :
    from document_extraction import DocumentExtractionService, TypePiece

    service = DocumentExtractionService()
    resultat = service.extraire(image_bytes, TypePiece.PASSEPORT)
    print(resultat.to_dict())
"""

from .niveaux_kyc import EXIGENCES_KYC, exigences_pour_niveau
from .schema import ChampExtrait, NiveauKyc, ResultatExtraction, TypePiece
from .service import DocumentExtractionService

__all__ = [
    "ChampExtrait",
    "NiveauKyc",
    "ResultatExtraction",
    "TypePiece",
    "DocumentExtractionService",
    "EXIGENCES_KYC",
    "exigences_pour_niveau",
]
