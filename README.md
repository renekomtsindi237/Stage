# MicroRecouv — V0

> Système intégré de pipeline de données, API REST et applications multiplateforme pour la gestion et le recouvrement de créances dans les institutions de microfinance au Cameroun.

**Auteur :** KOMTSINDI Réné Alban — Étudiant Ingénieur ISI 4e année  
**Structure :** Openxtech | **Année :** 2025–2026

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Angular 17 (Web)          Flutter 3.19 (Mobile)               │
│  Tableau de bord · Alertes  Agent terrain · Portefeuille mobile │
└──────────────────────────┬───────────────┬─────────────────────┘
                           │ REST/JWT      │ REST/JWT
                           ▼               ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Boot 3.2 — API REST (Java 21)               │
│  JWT · RBAC · SSE · Flyway · Swagger · i18n FR/EN              │
└──────────────────────────────────┬─────────────────────────────┘
                                   │ JDBC / SSL
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│           PostgreSQL 15 — Supabase (staging / prod)             │
│  Schéma app · staging · dw  |  Local Docker (dev)              │
└─────────────────────────────────────────────────────────────────┘
                                   ▲
                                   │ ETL quotidien
┌─────────────────────────────────────────────────────────────────┐
│       Apache Airflow 2.x + Python 3.11 + dbt Core 1.7          │
│  Extraction MTN/Orange/CBS → Staging → dbt → DW → Alertes      │
└─────────────────────────────────────────────────────────────────┘
```

| Couche | Technologie | Port local |
|---|---|---|
| Pipeline ETL | Python 3.11 + Airflow + dbt | 8090 (Airflow) |
| Backend API | Spring Boot 3.2 + Java 21 | 8080 |
| Application Web | Angular 17 + Material | 4200 |
| Application Mobile | Flutter 3.19 + Dart 3.3 | — |
| Base de données (dev) | PostgreSQL 15 Docker | 5432 |
| Base de données (prod) | Supabase PostgreSQL | — |

---

## Démarrage rapide

```bash
# 1. Copier l'environnement
cp .env.dev .env

# 2. Démarrer tous les services
make up ENV=dev

# 3. Vérifier (attendre ~60s)
make ps

# 4. Accéder
# API Swagger  : http://localhost:8080/swagger-ui.html
# App Web      : http://localhost:4200
# Airflow      : http://localhost:8090
# Adminer DB   : http://localhost:8888
```

## Commandes Makefile

```bash
make up ENV=dev|staging|prod    # Démarrer l'environnement
make down                        # Arrêter tous les services
make restart                     # Redémarrer
make logs                        # Logs en temps réel
make ps                          # Lister les conteneurs
make build-backend               # Build image Docker backend
make build-web                   # Build image Docker web
make test-backend                # Tests Spring Boot (Maven)
make test-web                    # Tests Angular (Karma)
make pipeline-test               # Tests Python (pytest)
make pipeline-lint               # Lint Python (ruff + mypy)
make dbt-run                     # Exécuter les modèles dbt
make migrate                     # Appliquer migrations Flyway
```

## Identifiants par défaut (dev uniquement)

| Service | URL | Identifiants |
|---|---|---|
| API REST | http://localhost:8080 | `admin` / `Admin2026!` |
| Airflow UI | http://localhost:8090 | `airflow` / `airflow` |
| Adminer | http://localhost:8888 | `imf_user` / `imf_dev_pass_2024` |

---

## Structure du monorepo

```
Stage/
├── backend/            Spring Boot 3.2 — API REST
│   ├── src/main/java/  17 controllers, 15 services, 7 entités JPA
│   └── src/main/resources/db/migration/  Flyway V1–V4
├── web/                Angular 17 — Application web
│   └── src/app/
│       ├── core/       Guards, interceptors, services, modèles
│       ├── modules/    Dashboard, Alertes, Prêts, Clients, Admin...
│       └── shared/     Navbar, Sidebar, Skeleton, Splash
├── mobile/             Flutter 3.19 — Application mobile
│   └── lib/
│       ├── core/       Models, services, providers
│       ├── screens/    11 écrans complets
│       └── widgets/    Composants réutilisables
├── pipeline/           Python + Airflow + dbt
│   ├── dags/           5 DAGs Airflow
│   ├── src/            ETL (extracteurs, transformateurs, loaders)
│   └── dbt_project/    Modèles dbt staging + dw
├── docker/             Configurations Docker spécifiques
├── docs/V0/            Documentation versionnée V0
├── docker-compose.dev.yml
├── docker-compose.staging.yml
├── docker-compose.prod.yml
├── .env.dev            Variables dev (PostgreSQL local)
├── .env.staging        Variables staging (Supabase)
├── .env.prod           Variables prod (Supabase)
└── Makefile            Orchestration complète
```

## Configuration Supabase (staging / prod)

```bash
# 1. Créer un projet sur https://supabase.com
# 2. Récupérer : Project URL, anon key, service_role key, DB password
# 3. Renseigner dans .env.staging ou .env.prod :
SUPABASE_URL=https://xxxxx.supabase.co
POSTGRES_HOST=db.xxxxx.supabase.co
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<votre-mdp-db>
POSTGRES_SSL_MODE=require
# 4. Lancer : make up ENV=staging
# Flyway appliquera automatiquement les migrations V1-V4
```

## Documentation

```
docs/V0/
├── 01_CDC/             Cahier des charges
├── 02_Analyse/         Analyse des besoins
├── 03_Conception_Architecture/  Architecture du système
├── 04_Conception_Donnees_UML/   Modèle de données & UML
├── 05_Algo_Complexite/ Algorithmes & complexité
├── 06_Doc_Systeme/     Guide technique & API Reference
├── 06_Planification/   Planning, WBS, CI/CD, déploiement
├── 07_Rapport_Final/   Rapport de stage complet
└── 08_Infrastructure/  Guide Supabase
```

---

*Projet de fin d'études — Institut Universitaire Saint Jean, Yaoundé — 2026*
