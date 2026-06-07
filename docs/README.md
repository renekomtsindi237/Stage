# Documentation du Projet

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé

---

## Structure de la documentation

```
docs/
├── uml/                        ← Diagrammes UML PlantUML (VERSION ACTUELLE)
│   ├── 01_use_case.puml
│   ├── 02_sequence_collecte.puml
│   ├── 03_sequence_scoring_mcrs.puml
│   ├── 04_classes_domaine.puml
│   ├── 05_composants.puml
│   ├── 06_deploiement.puml
│   ├── 07_activite_recouvrement.puml
│   └── 08_sequence_auth.puml
│
├── ROADMAP.md                  ← Feuille de route V1 → V4
└── V0/                         ← Archive (ancienne vision du projet — obsolète)
```

---

## Diagrammes UML (`docs/uml/`)

| Fichier | Type | Contenu |
|---|---|---|
| `01_use_case.puml` | Cas d'utilisation | Tous les acteurs et cas d'utilisation du système |
| `02_sequence_collecte.puml` | Séquence | Flux collecte offline-first (saisie → sync → validation → pipeline) |
| `03_sequence_scoring_mcrs.puml` | Séquence | Pipeline MCRS journalier (features → scoring → SHAP → alertes → drift) |
| `04_classes_domaine.puml` | Classes | Modèle de domaine complet (toutes les entités et relations) |
| `05_composants.puml` | Composants | Vue architecture par composants (Flutter, Angular, Spring Boot, Airflow, dbt, ML, PostgreSQL, Redis) |
| `06_deploiement.puml` | Déploiement | Architecture Docker Compose cible |
| `07_activite_recouvrement.puml` | Activité | Workflow COBAC de recouvrement (RELANCE → MISE_EN_DEMEURE → CONTENTIEUX → REECHELONNEMENT/RADIATION) |
| `08_sequence_auth.puml` | Séquence | Authentification JWT multi-tenant (login, requêtes, refresh) |

### Générer les diagrammes

```bash
# PlantUML CLI (Java requis)
java -jar plantuml.jar docs/uml/*.puml

# VS Code : extension PlantUML (jebbs.plantuml)
# Ouvrir le fichier .puml → Alt+D pour prévisualiser
```

---

## Documents associés

- `cahier_des_charges/` — Contexte, objectifs, acteurs, exigences fonctionnelles/non-fonctionnelles, contraintes.
- `conception/` — Architecture globale, modèle de données, pipeline, API, sécurité, choix technologiques.
- `analyse/` — Analyse de l'existant, benchmark, besoins métier, cas d'utilisation, règles de gestion.
- `docs/ROADMAP.md` — Feuille de route V1 → V4.

---

## Note sur le dossier `V0/`

Le dossier `V0/` contient les diagrammes et documents de l'ancienne vision du projet (avant restructuration de mai 2026). Il est conservé à titre d'archive mais **ne reflète pas l'architecture actuelle**. Les documents actuels sont dans `docs/uml/` et les dossiers `cahier_des_charges/`, `conception/`, `analyse/`.
