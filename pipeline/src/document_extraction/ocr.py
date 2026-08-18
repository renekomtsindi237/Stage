"""
Wrapper OCR — prétraitement d'image + appel Tesseract.

Auto-hébergé : ne dépend d'aucune API externe payante. Nécessite le binaire
Tesseract (+ le paquet de langue français `fra`) installé sur la machine
hôte — cf. pipeline/Dockerfile.ml pour l'installation en conteneur.
"""

from __future__ import annotations

import io
import logging
import os

import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)

try:
    import cv2

    _HAS_CV2 = True
except ImportError:  # pragma: no cover — opencv optionnel, fallback PIL-only
    _HAS_CV2 = False

try:
    import pytesseract

    _tesseract_cmd = os.getenv("TESSERACT_CMD")
    if _tesseract_cmd:
        pytesseract.pytesseract.tesseract_cmd = _tesseract_cmd
    _HAS_TESSERACT = True
except ImportError:  # pragma: no cover
    _HAS_TESSERACT = False


class OcrIndisponible(RuntimeError):
    """Levée quand Tesseract n'est pas installé/accessible sur la machine."""


# Tesseract vise ~300 DPI / une hauteur de caractère d'au moins ~25-30px.
# Les photos de pièces prises au téléphone dépassent rarement cette densité
# une fois le texte rapporté à la taille réelle des champs — sur-échantillonner
# avant OCR améliore nettement la précision sur les mises en page denses
# (permis, CNI multi-champs).
_LARGEUR_MIN_CIBLE = 2000


def _suréchantillonner(img: Image.Image) -> Image.Image:
    largeur, hauteur = img.size
    if largeur >= _LARGEUR_MIN_CIBLE:
        return img
    facteur = _LARGEUR_MIN_CIBLE / largeur
    nouvelle_taille = (int(largeur * facteur), int(hauteur * facteur))
    return img.resize(nouvelle_taille, Image.LANCZOS)


def _pretraiter(image_bytes: bytes) -> Image.Image:
    """
    Améliore la lisibilité pour l'OCR : sur-échantillonnage, niveaux de gris,
    contraste, débruitage. Réduit significativement le taux d'erreur sur des
    scans de qualité variable (photos prises au téléphone, compression JPEG,
    éclairage inégal, mises en page à champs denses).
    """
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    img = _suréchantillonner(img)

    if not _HAS_CV2:
        return img.convert("L")

    arr = np.array(img)
    gris = cv2.cvtColor(arr, cv2.COLOR_RGB2GRAY)
    # Débruitage léger puis seuillage adaptatif — robuste aux ombres/éclairage inégal
    gris = cv2.fastNlMeansDenoising(gris, h=10)
    seuil = cv2.adaptiveThreshold(
        gris, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 11
    )
    return Image.fromarray(seuil)


def extraire_texte(image_bytes: bytes, langues: str = "fra+eng") -> str:
    """Retourne le texte brut détecté sur l'image (pleine page)."""
    if not _HAS_TESSERACT:
        raise OcrIndisponible(
            "pytesseract non installé — voir pipeline/requirements-ocr.txt"
        )
    img = _pretraiter(image_bytes)
    try:
        return pytesseract.image_to_string(img, lang=langues)
    except pytesseract.TesseractNotFoundError as exc:
        raise OcrIndisponible(
            "Binaire tesseract introuvable — définir TESSERACT_CMD ou installer "
            "tesseract-ocr (voir pipeline/Dockerfile.ml)."
        ) from exc


def extraire_lignes(image_bytes: bytes, langues: str = "fra+eng") -> list[str]:
    """Texte brut découpé en lignes non vides — pratique pour la détection de MRZ."""
    texte = extraire_texte(image_bytes, langues=langues)
    return [ligne for ligne in texte.splitlines() if ligne.strip()]


def extraire_zone_mrz(image_bytes: bytes) -> list[str]:
    """
    Isole le tiers inférieur de l'image (où se trouve conventionnellement la
    MRZ sur passeports et CNI) et lance un OCR dédié, avec une liste de
    caractères restreinte (police OCR-B : A-Z, 0-9, '<') pour maximiser la
    précision de lecture sur cette zone critique.
    """
    if not _HAS_TESSERACT:
        raise OcrIndisponible(
            "pytesseract non installé — voir pipeline/requirements-ocr.txt"
        )
    img = Image.open(io.BytesIO(image_bytes)).convert("L")
    largeur, hauteur = img.size
    zone = img.crop((0, int(hauteur * 0.62), largeur, hauteur))
    zone = _suréchantillonner(zone)

    config = "--psm 6 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
    try:
        texte = pytesseract.image_to_string(zone, lang="eng", config=config)
    except pytesseract.TesseractNotFoundError as exc:
        raise OcrIndisponible("Binaire tesseract introuvable.") from exc
    return [ligne for ligne in texte.splitlines() if ligne.strip()]
