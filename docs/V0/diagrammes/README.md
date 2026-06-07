# Diagrammes UML — Plateforme IMF Cameroun

Tous les diagrammes sont en notation **PlantUML**.

## Comment les générer

### Option 1 — VS Code (recommandé)
1. Installer l'extension **PlantUML** (jebbs.plantuml)
2. Ouvrir un fichier `.puml`
3. `Alt + D` pour prévisualiser
4. Clic droit → "Export Current Diagram" pour exporter en PNG/SVG/PDF

### Option 2 — En ligne
Aller sur https://plantuml.com/plantuml (copier-coller le contenu du fichier)

### Option 3 — CLI
```bash
java -jar plantuml.jar diagrammes/**/*.puml
```

---

## Index des diagrammes

### Use Cases (UC)
| Fichier | Description |
|---|---|
| `01_use_cases/UC00_global.puml` | Vue d'ensemble — tous les acteurs et UC |
| `01_use_cases/UC01_collectes_digitales.puml` | UC01 — Gestion des collectes digitales |
| `01_use_cases/UC02_portefeuille_creances.puml` | UC02 — Suivi du portefeuille de créances |
| `01_use_cases/UC03_recouvrement_relances.puml` | UC03 — Recouvrement et relances |
| `01_use_cases/UC04_reporting_dashboards.puml` | UC04 — Reporting et tableaux de bord |
| `01_use_cases/UC05_administration_pipeline.puml` | UC05 — Administration du pipeline |
| `01_use_cases/UC06_mobile_agent.puml` | UC06 — Application mobile (agent terrain) |
| `01_use_cases/UC07_api_backend.puml` | UC07 — Backend API Spring Boot |

### Activité (ACT)
| Fichier | Description |
|---|---|
| `02_activite/ACT01_collecte_ASIS.puml` | Processus collecte digitale — Situation actuelle |
| `02_activite/ACT02_collecte_TOBE.puml` | Processus collecte digitale — Situation cible |
| `02_activite/ACT03_recouvrement_ASIS.puml` | Processus recouvrement — Situation actuelle |
| `02_activite/ACT04_recouvrement_TOBE.puml` | Processus recouvrement — Situation cible |
| `02_activite/ACT05_pipeline_ingestion.puml` | Pipeline d'ingestion quotidien |
| `02_activite/ACT06_saisie_mobile.puml` | Saisie collecte mobile (offline + sync) |

### Séquence (SEQ)
| Fichier | Description |
|---|---|
| `03_sequence/SEQ01_ingestion_mtn.puml` | Ingestion quotidienne transactions MTN |
| `03_sequence/SEQ02_calcul_par.puml` | Calcul PAR30/PAR90 (dbt) |
| `03_sequence/SEQ03_alertes_impayes.puml` | Génération alertes impayés |
| `03_sequence/SEQ04_rafraichissement_dashboard.puml` | Rafraîchissement dashboards Superset |
| `03_sequence/SEQ05_authentification_web.puml` | Authentification Angular via Spring Boot |
| `03_sequence/SEQ06_saisie_collecte_mobile.puml` | Saisie collecte Flutter (online + offline) |
| `03_sequence/SEQ07_push_notification.puml` | Notification push FCM vers Flutter |
| `03_sequence/SEQ08_consultation_kpi_mobile.puml` | Consultation KPIs depuis Flutter |

### Classe (CLS)
| Fichier | Description |
|---|---|
| `04_classe/CLS01_pipeline_python.puml` | Modèle objet du pipeline Python |
| `04_classe/CLS02_backend_springboot.puml` | Modèle objet backend Spring Boot (entités JPA) |
| `04_classe/CLS03_flutter_app.puml` | Modèle objet application Flutter |

### États (ETT)
| Fichier | Description |
|---|---|
| `05_etats/ETT01_cycle_pret.puml` | Cycle de vie d'un prêt |
| `05_etats/ETT02_cycle_alerte.puml` | Cycle de vie d'une alerte impayé |
| `05_etats/ETT03_cycle_collecte_mobile.puml` | Cycle de vie d'une collecte mobile (sync) |

### Architecture (ARCH)
| Fichier | Description |
|---|---|
| `06_architecture/ARCH01_composants_pipeline.puml` | Composants du pipeline de données |
| `06_architecture/ARCH02_composants_systeme_complet.puml` | Composants système complet (4 couches) |
| `06_architecture/ARCH03_deploiement.puml` | Diagramme de déploiement (serveurs, Docker) |
| `06_architecture/ARCH04_contexte_SI.puml` | Diagramme de contexte SI |
| `06_architecture/ARCH05_MCD.puml` | Modèle Conceptuel des Données |
