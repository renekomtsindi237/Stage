"""Point d'entrée du module RAG — chargement de l'index précalculé + recherche."""

from __future__ import annotations

import logging
from pathlib import Path

from .schema import ResultatRecherche

logger = logging.getLogger(__name__)

_INDEX_PATH = Path(__file__).resolve().parent / "data" / "index.pkl"


class RagService:
    """
    Service de recherche documentaire pour le chatbot IA.

    Charge l'index TF-IDF précalculé (voir ingestion.py) au premier appel.
    Si l'index est absent (ex: documentation pas encore indexée), le
    service reste utilisable mais renvoie toujours une liste vide — le
    chatbot continue de fonctionner avec les seuls outils de requête DB,
    dégradation silencieuse plutôt que panne.
    """

    def __init__(self) -> None:
        self._index = None
        self._tentative_chargement = False

    def _charger_si_necessaire(self):
        if self._index is not None or self._tentative_chargement:
            return
        self._tentative_chargement = True
        if not _INDEX_PATH.exists():
            logger.warning(
                "Index RAG introuvable (%s) — recherche documentaire désactivée.",
                _INDEX_PATH,
            )
            return
        try:
            from .index import IndexRag

            self._index = IndexRag.charger(_INDEX_PATH)
            logger.info("Index RAG chargé : %d chunks.", len(self._index.chunks))
        except Exception:
            logger.exception("Échec du chargement de l'index RAG.")

    def rechercher(self, requete: str, k: int = 4) -> list[ResultatRecherche]:
        self._charger_si_necessaire()
        if self._index is None:
            return []
        return self._index.rechercher(requete, k=k)

    def disponible(self) -> bool:
        self._charger_si_necessaire()
        return self._index is not None
