"""Découpage de documents Markdown en extraits (chunks) pour l'indexation RAG."""

from __future__ import annotations

import re
from pathlib import Path

from .schema import ChunkDocument

_HEADING_RE = re.compile(r"^#{1,6}\s+(.+)$", re.MULTILINE)

# Cible ~250-400 mots par chunk : assez pour donner du contexte à Groq, assez
# petit pour que la similarité TF-IDF reste précise (un chunk trop long dilue
# le signal des termes rares/spécifiques qui font la valeur du TF-IDF).
_MOTS_MIN_CHUNK = 60
_MOTS_MAX_CHUNK = 400


def _decouper_par_paragraphes(texte: str) -> list[str]:
    return [p.strip() for p in re.split(r"\n\s*\n", texte) if p.strip()]


def _grouper_paragraphes(paragraphes: list[str]) -> list[str]:
    """Regroupe des paragraphes consécutifs jusqu'à atteindre ~_MOTS_MAX_CHUNK mots."""
    groupes: list[str] = []
    courant: list[str] = []
    mots_courant = 0

    for p in paragraphes:
        mots_p = len(p.split())
        if courant and mots_courant + mots_p > _MOTS_MAX_CHUNK:
            groupes.append("\n\n".join(courant))
            courant = []
            mots_courant = 0
        courant.append(p)
        mots_courant += mots_p

    if courant:
        groupes.append("\n\n".join(courant))

    # Fusionne les groupes trop petits (dernier paragraphe isolé, etc.) avec
    # le groupe précédent plutôt que de garder un chunk quasi vide.
    fusionnes: list[str] = []
    for g in groupes:
        if fusionnes and len(g.split()) < _MOTS_MIN_CHUNK:
            fusionnes[-1] = fusionnes[-1] + "\n\n" + g
        else:
            fusionnes.append(g)
    return fusionnes


def decouper_document(chemin: Path, texte: str) -> list[ChunkDocument]:
    """
    Découpe un document Markdown en chunks, en gardant trace du dernier titre
    de section rencontré (utile pour donner du contexte à Groq et à
    l'utilisateur sur la provenance exacte d'une réponse).
    """
    chunks: list[ChunkDocument] = []
    positions_titres = [
        (m.start(), m.group(1).strip()) for m in _HEADING_RE.finditer(texte)
    ]

    # Découpe le texte en segments délimités par les titres, pour associer
    # chaque bloc de paragraphes à son titre de section.
    segments: list[tuple[str, str]] = []  # (titre, contenu)
    if not positions_titres:
        segments.append(("", texte))
    else:
        for i, (pos, titre) in enumerate(positions_titres):
            fin = (
                positions_titres[i + 1][0]
                if i + 1 < len(positions_titres)
                else len(texte)
            )
            contenu = texte[pos:fin]
            contenu = _HEADING_RE.sub(
                "", contenu, count=1
            )  # retire la ligne de titre elle-même
            segments.append((titre, contenu))

    for titre, contenu in segments:
        paragraphes = _decouper_par_paragraphes(contenu)
        for groupe in _grouper_paragraphes(paragraphes):
            if len(groupe.split()) < 10:  # bruit (ligne isolée, séparateur...)
                continue
            chunks.append(
                ChunkDocument(
                    id=-1,  # assigné par l'appelant (index global sur tout le corpus)
                    texte=groupe,
                    source=str(chemin),
                    titre_section=titre,
                )
            )
    return chunks
