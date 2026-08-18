"""
Construction de l'index RAG à partir de la documentation du projet.

Ce script n'est PAS exécuté au runtime du service ml-api (il a besoin des
dossiers conception/, cahier_des_charges/, analyse/, docs/presentation/ —
hors du contexte de build Docker de ml-api, qui ne copie que pipeline/). Il
se lance manuellement depuis la racine du dépôt quand la documentation
change, et produit un artefact précalculé (data/index.pkl) qui, lui, est
committé sous pipeline/src/rag/ et donc bien inclus dans l'image Docker.

Usage :
    cd <racine du dépôt>
    python pipeline/src/rag/ingestion.py
"""

from __future__ import annotations

import sys
from pathlib import Path

# Dossiers sources relatifs à la racine du dépôt — documentation de
# conception/analyse, pas le code applicatif (pas de valeur pour un RAG
# conversationnel, et volume bien trop important).
SOURCES_RELATIVES = [
    "conception",
    "cahier_des_charges",
    "analyse",
    "docs/presentation",
]

INDEX_PATH_RELATIF = "pipeline/src/rag/data/index.pkl"


def construire_corpus(racine: Path):
    from .chunking import decouper_document

    chunks = []
    for dossier_rel in SOURCES_RELATIVES:
        dossier = racine / dossier_rel
        if not dossier.exists():
            print(f"  (absent, ignoré : {dossier_rel})")
            continue
        for fichier in sorted(dossier.glob("*.md")):
            texte = fichier.read_text(encoding="utf-8")
            chemin_relatif = fichier.relative_to(racine)
            chunks_fichier = decouper_document(chemin_relatif, texte)
            chunks.extend(chunks_fichier)
            print(f"  {chemin_relatif} -> {len(chunks_fichier)} chunks")
    return chunks


def main() -> None:
    from .index import construire_index

    racine = (
        Path(__file__).resolve().parents[3]
    )  # pipeline/src/rag/ingestion.py -> racine du dépôt
    print(f"Racine du dépôt détectée : {racine}")
    print("Lecture des documents source...")
    chunks = construire_corpus(racine)
    print(f"\n{len(chunks)} chunks au total.")

    if not chunks:
        print(
            "ERREUR : aucun chunk généré, vérifier SOURCES_RELATIVES et la racine détectée."
        )
        sys.exit(1)

    print("Construction de l'index TF-IDF...")
    index = construire_index(chunks)

    chemin_index = racine / INDEX_PATH_RELATIF
    index.sauvegarder(chemin_index)
    print(
        f"Index sauvegardé : {chemin_index} ({chemin_index.stat().st_size / 1024:.1f} Ko)"
    )


if __name__ == "__main__":
    main()
