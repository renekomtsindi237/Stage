# document_extraction

Extraction de texte et de champs structurés depuis des pièces d'identité
scannées (CNI, passeport, permis de conduire, carte de séjour) — auto-hébergé,
sans dépendance à une API externe payante.

## Pourquoi pas un modèle entraîné ?

Un OCR/extracteur entraîné from scratch nécessite des centaines/milliers
d'exemples annotés par type de document pour être fiable. Ce module utilise
à la place deux approches déterministes, éprouvées et documentées
publiquement :

1. **Parsing MRZ** (`mrz.py`) conforme à la norme **ICAO Doc 9303** — la
   zone lisible en machine des passeports (format TD3) et cartes d'identité
   (format TD1) suit un format fixe avec chiffres de contrôle vérifiables.
   Fiabilité proche de 100% dès lors que l'OCR a correctement lu la zone.
   Inclut une correction des confusions OCR les plus courantes sur police
   OCR-B (`O`↔`0`, `I`↔`1`, `B`↔`8`, `S`↔`5`, `Z`↔`2`, `G`↔`6`).
2. **OCR + reconnaissance d'étiquettes** (`ocr.py` + `extracteurs/`) pour les
   champs non couverts par la MRZ (lieu de naissance, profession, etc.),
   avec gestion des libellés bilingues FR/EN sur une même ligne.

## Structure

```
document_extraction/
├── schema.py           # Types partagés (TypePiece, NiveauKyc, ResultatExtraction, ChampExtrait)
├── mrz.py               # Parseur MRZ ICAO 9303 (TD1 + TD3), déterministe
├── ocr.py                # Wrapper Tesseract (prétraitement + extraction texte/zone MRZ)
├── niveaux_kyc.py        # Exigences d'extraction par niveau KYC (1/2/3, COBAC R-2005/01)
├── service.py             # Point d'entrée : DocumentExtractionService.extraire(image, type)
├── extracteurs/           # Un extracteur par type de pièce
│   ├── passeport.py
│   ├── cni.py             # recto (OCR+étiquettes) + verso (MRZ TD1)
│   ├── permis.py
│   └── carte_sejour.py
└── tests/
    └── test_mrz.py         # Tests unitaires — vecteur de test officiel ICAO uniquement
```

## Usage

```python
from document_extraction import DocumentExtractionService, TypePiece

service = DocumentExtractionService()
resultat = service.extraire(image_bytes, TypePiece.PASSEPORT)

resultat.champs["nom"].valeur       # "KOMTSINDI"
resultat.champs["nom"].confiance    # 0.98 (source MRZ, chiffre de contrôle valide)
resultat.mrz_valide                  # True/False/None (None = pas de MRZ sur ce type/face)
resultat.to_dict()                    # sérialisable JSON tel quel
```

Complétude d'un dossier KYC (fusion de plusieurs documents) :

```python
from document_extraction import NiveauKyc
from document_extraction.niveaux_kyc import evaluer_completude

rapport = evaluer_completude([resultat_recto, resultat_verso], NiveauKyc.NIVEAU_2)
rapport.champs_manquants                # ex: ["dateExpirationPiece"]
rapport.documents_complementaires_manquants  # ex: ["JUSTIFICATIF_DOMICILE"]
```

## Dépendances

- Binaire système `tesseract-ocr` + paquet de langue `tesseract-ocr-fra`
  (voir `pipeline/Dockerfile.ml` pour l'installation en conteneur)
- Python : voir `pipeline/requirements-ocr.txt`

## Réutilisation dans un autre projet

Le package ne dépend d'aucun autre module de MicroRecouv (pas d'import vers
`cm.imf.*`, pas de connexion base de données). Il suffit de copier le dossier
`document_extraction/` et d'installer ses dépendances pour l'utiliser
ailleurs. `niveaux_kyc.py` encode des règles spécifiques COBAC/Cameroun — à
adapter si réutilisé dans un contexte réglementaire différent.

## Limites connues

- Le champ `numeroPiece` alphanumérique n'est pas auto-corrigé en cas
  d'échec du chiffre de contrôle (contrairement aux dates, purement
  numériques) — l'espace de correction serait trop large et risquerait de
  produire une fausse confiance. Un échec ici reste signalé pour vérification
  visuelle par le DSI.
- `permis.py` et une partie de `carte_sejour.py` reposent uniquement sur la
  reconnaissance d'étiquettes (pas de MRZ standardisée) — confiance plus
  faible, cohérent avec leur statut de pièces "en complément" dans le
  workflow KYC.
