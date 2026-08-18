"""
rag — recherche documentaire (Retrieval-Augmented Generation) sur la
documentation du projet (conception, cahier des charges, analyse).

Auto-hébergé : recherche par TF-IDF (scikit-learn, déjà une dépendance du
pipeline), aucune API externe, aucun modèle d'embeddings à télécharger.
L'index est précalculé (voir ingestion.py) et chargé au démarrage du
service ml-api — la génération de la réponse finale reste déléguée à Groq
(AiChatController.java), ce module ne fait que la RÉCUPÉRATION du contexte
pertinent, pas la génération de texte.

Usage :
    from rag import RagService
    service = RagService()
    resultats = service.rechercher("Qu'est-ce que le PAR30 ?", k=3)
"""

from .schema import ChunkDocument, ResultatRecherche
from .service import RagService

__all__ = ["RagService", "ChunkDocument", "ResultatRecherche"]
