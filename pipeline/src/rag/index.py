"""Index de recherche TF-IDF — construction, sauvegarde, chargement, recherche."""

from __future__ import annotations

import pickle
from dataclasses import dataclass
from pathlib import Path

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from .schema import ChunkDocument, ResultatRecherche

# Mots vides français courants — TfidfVectorizer n'a pas de liste FR native
# (seulement 'english'), donc fournie explicitement. Volontairement courte :
# le vocabulaire technique (COBAC, MCRS, PAR30...) doit rester discriminant,
# une liste trop agressive risquerait de retirer des termes utiles.
STOP_WORDS_FR = [
    "le",
    "la",
    "les",
    "un",
    "une",
    "des",
    "de",
    "du",
    "au",
    "aux",
    "et",
    "ou",
    "mais",
    "donc",
    "or",
    "ni",
    "car",
    "ce",
    "cet",
    "cette",
    "ces",
    "il",
    "elle",
    "ils",
    "elles",
    "on",
    "je",
    "tu",
    "nous",
    "vous",
    "qui",
    "que",
    "quoi",
    "dont",
    "où",
    "est",
    "sont",
    "sera",
    "être",
    "avoir",
    "a",
    "ont",
    "été",
    "dans",
    "sur",
    "sous",
    "par",
    "pour",
    "avec",
    "sans",
    "entre",
    "plus",
    "moins",
    "très",
    "peu",
    "bien",
    "aussi",
    "ainsi",
    "donc",
    "se",
    "sa",
    "son",
    "ses",
    "leur",
    "leurs",
    "lui",
    "y",
    "en",
    "pas",
    "ne",
    "n",
    "l",
    "d",
    "s",
    "qu",
    "c",
    "à",
]


@dataclass
class IndexRag:
    vectorizer: TfidfVectorizer
    matrice: object  # scipy sparse matrix (n_chunks × n_features)
    chunks: list[ChunkDocument]

    def rechercher(
        self, requete: str, k: int = 4, score_min: float = 0.05
    ) -> list[ResultatRecherche]:
        if not self.chunks:
            return []
        vecteur_requete = self.vectorizer.transform([requete])
        scores = cosine_similarity(vecteur_requete, self.matrice)[0]
        indices_tries = scores.argsort()[::-1][:k]
        return [
            ResultatRecherche(chunk=self.chunks[i], score=float(scores[i]))
            for i in indices_tries
            if scores[i] >= score_min
        ]

    def sauvegarder(self, chemin: Path) -> None:
        chemin.parent.mkdir(parents=True, exist_ok=True)
        with open(chemin, "wb") as f:
            pickle.dump(
                {
                    "vectorizer": self.vectorizer,
                    "matrice": self.matrice,
                    "chunks": self.chunks,
                },
                f,
            )

    @classmethod
    def charger(cls, chemin: Path) -> "IndexRag":
        with open(chemin, "rb") as f:
            data = pickle.load(f)
        return cls(
            vectorizer=data["vectorizer"],
            matrice=data["matrice"],
            chunks=data["chunks"],
        )


def construire_index(chunks: list[ChunkDocument]) -> IndexRag:
    for i, c in enumerate(chunks):
        c.id = i
    vectorizer = TfidfVectorizer(
        stop_words=STOP_WORDS_FR,
        ngram_range=(1, 2),  # unigrammes + bigrammes ("PAR30", "score mcrs")
        max_df=0.85,  # ignore les termes présents dans >85% des chunks (bruit)
        min_df=1,
        sublinear_tf=True,  # atténue l'effet des mots très répétés dans un même chunk
    )
    matrice = vectorizer.fit_transform([c.texte for c in chunks])
    return IndexRag(vectorizer=vectorizer, matrice=matrice, chunks=chunks)
