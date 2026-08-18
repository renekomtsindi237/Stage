"""Types partagés du module RAG."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ChunkDocument:
    """Un extrait de documentation, unité de recherche/retour du RAG."""

    id: int
    texte: str
    source: str  # chemin relatif du fichier d'origine, ex: "conception/03_conception_pipeline.md"
    titre_section: str  # titre du dernier heading markdown avant ce chunk

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "texte": self.texte,
            "source": self.source,
            "titreSection": self.titre_section,
        }


@dataclass
class ResultatRecherche:
    chunk: ChunkDocument
    score: float  # similarité cosinus TF-IDF, ∈ [0, 1]

    def to_dict(self) -> dict:
        return {**self.chunk.to_dict(), "score": round(self.score, 4)}
