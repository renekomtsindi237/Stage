# PLAN03 — Pipeline CI/CD
## GitHub Actions · Docker Registry · Staging · Production

---

| Champ | Valeur |
|---|---|
| **Document** | Pipeline CI/CD (PLAN03) |
| **Version** | 1.0 |
| **Date** | 2026-04-01 |
| **Outil CI/CD** | GitHub Actions |
| **Registry** | Docker Hub ou GitHub Container Registry (ghcr.io) |

---

## TABLE DES MATIÈRES

1. [Vue d'ensemble du pipeline](#1-vue-densemble-du-pipeline)
2. [Stratégie de branches](#2-stratégie-de-branches)
3. [Workflow CI — Intégration Continue](#3-workflow-ci--intégration-continue)
4. [Workflow CD — Déploiement Staging](#4-workflow-cd--déploiement-staging)
5. [Workflow CD — Déploiement Production](#5-workflow-cd--déploiement-production)
6. [Procédure de Rollback](#6-procédure-de-rollback)
7. [Configuration GitHub Actions](#7-configuration-github-actions)

---

## 1. Vue d'ensemble du pipeline

```
Développeur
    │
    │  git push feature/*
    ▼
GitHub Actions — CI
    ├─ Lint (checkstyle, dartanalyze, eslint)
    ├─ Tests unitaires (JUnit, Jasmine, Flutter test)
    ├─ Tests dbt (unicité, not null, FK)
    └─ Build Docker images
            │
            │  Push images :sha-commit
            ▼
    Docker Registry (ghcr.io)
            │
            │  Pull Request → merge develop
            ▼
GitHub Actions — CD Staging
    ├─ Pull image :latest
    ├─ docker compose up -d
    ├─ Flyway migration staging
    └─ Smoke tests (/health, /api/auth)
            │
            │  UAT validés → approbation manuelle
            ▼
GitHub Actions — CD Production
    ├─ Tag release vX.Y.Z
    ├─ Pull image :vX.Y.Z
    ├─ Maintenance window (23h–01h)
    ├─ docker compose up -d
    ├─ Flyway migration production
    └─ Tests de santé production
            │
            ├─ OK  → Monitoring actif, clôture sprint
            └─ KO  → Rollback automatique (image N-1)
```

---

## 2. Stratégie de branches

| Branche | Rôle | Protection |
|---|---|---|
| `main` | Code production stable — taggé `vX.Y.Z` | Requiert PR + 1 approbation + CI verte |
| `develop` | Intégration continue — déployé sur staging | Requiert PR + CI verte |
| `feature/*` | Développement d'une fonctionnalité | Libre |
| `hotfix/*` | Correctif urgent en production | Requiert PR vers `main` + `develop` |
| `release/*` | Préparation d'une version | Merge vers `main` et `develop` |

### Flux de travail standard

```
feature/ajout-scoring
        │
        │  PR → develop
        ▼
    develop  ──────► Staging (CD automatique)
        │
        │  PR → main (après UAT)
        ▼
     main  ──────► Production (CD manuel)
        │
        │  git tag v1.0.0
        ▼
    Release
```

---

## 3. Workflow CI — Intégration Continue

**Déclencheur :** `push` sur `feature/*`, `develop`, `hotfix/*`

### Étape 1 — Lint & Analyse statique

| Composant | Outil | Commande |
|---|---|---|
| Spring Boot (Java) | Checkstyle | `mvn checkstyle:check` |
| Angular | ESLint + ng lint | `ng lint` |
| Flutter | Dart Analyzer | `flutter analyze` |
| Python (Pipeline) | Flake8 | `flake8 dags/ scripts/` |
| dbt | sqlfluff | `sqlfluff lint models/` |

**Condition de passage :** 0 erreur. Les warnings sont autorisés.

### Étape 2 — Tests unitaires

| Composant | Framework | Commande | Seuil couverture |
|---|---|---|---|
| Spring Boot | JUnit 5 + Mockito | `mvn test` | ≥ 70 % |
| Angular | Jasmine + Karma | `ng test --watch=false` | ≥ 60 % |
| Flutter | flutter test | `flutter test` | ≥ 60 % |
| Python / dbt | pytest | `pytest tests/` | ≥ 60 % |

**Rapport JUnit** exporté en XML et affiché dans GitHub Actions.

### Étape 3 — Tests dbt

```bash
dbt deps
dbt compile --profiles-dir ./profiles
dbt test --profiles-dir ./profiles
```

Tests vérifiés : `unique`, `not_null`, `relationships`, `accepted_values`.

### Étape 4 — Build Docker

```bash
# Pipeline
docker build -t ghcr.io/org/imf-pipeline:$SHA ./pipeline

# Spring Boot
mvn package -DskipTests
docker build -t ghcr.io/org/imf-api:$SHA ./backend

# Angular
ng build --configuration=production
docker build -t ghcr.io/org/imf-web:$SHA ./frontend

# Flutter APK
flutter build apk --release
```

**Tagging :** chaque image reçoit deux tags :
- `:${{ github.sha }}` — identifiant unique du commit
- `:latest` — dernier build de `develop`

### Étape 5 — Push Registry & Notification

```bash
docker push ghcr.io/org/imf-pipeline:$SHA
docker push ghcr.io/org/imf-api:$SHA
docker push ghcr.io/org/imf-web:$SHA
```

**Notification :** email ou Slack `"✅ Build SUCCESS — commit $SHA"`

### Résumé CI

```
push feature/* ou develop
        │
        ├─ Lint ──────────► KO → notification "Lint échoué" + stop
        │
        ├─ Tests unitaires ─► KO → notification "Tests KO + rapport" + stop
        │
        ├─ dbt test ────────► KO → notification "dbt tests KO" + stop
        │
        └─ Docker build + push registry
                    │
                    └─ OK → notification "Build SUCCESS ✅"
```

---

## 4. Workflow CD — Déploiement Staging

**Déclencheur :** `merge` dans `develop` (après PR approuvée)

### Étape 1 — Pull des images

```bash
ssh deploy@192.168.1.50
docker compose -f docker-compose.staging.yml pull
```

### Étape 2 — Démarrage des services

```bash
docker compose -f docker-compose.staging.yml up -d
```

**Ordre de démarrage :**
1. `postgres-staging`
2. `airflow-staging` (après postgres healthy)
3. `springboot-staging` (après postgres healthy)
4. `nginx-staging` (après springboot healthy)

### Étape 3 — Migration Flyway Staging

La migration Flyway s'exécute automatiquement au démarrage du conteneur Spring Boot si des migrations nouvelles sont détectées.

**Vérification manuelle possible :**
```bash
docker exec imf-springboot-staging java -jar app.jar --spring.flyway.validate-on-migrate=true
```

### Étape 4 — Smoke Tests Automatiques

```bash
# Santé API
curl -f https://staging.imf-app.local/api/health

# Auth
curl -X POST https://staging.imf-app.local/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_dsi","password":"test_pass"}'

# Frontend
curl -f https://staging.imf-app.local/
```

**Si smoke tests KO :** rollback automatique vers l'image précédente.

### Étape 5 — Tests UAT

| Acteur | Action | Durée |
|---|---|---|
| Développeur | Notifie le maître de stage que staging est prêt | — |
| Maître de stage | Teste les scénarios métier sur staging | 1–2 j |
| Maître de stage | Approuve ou signale des corrections | — |

**Approbation :** via GitHub Actions — "Manual approval required" avant déploiement production.

---

## 5. Workflow CD — Déploiement Production

**Déclencheur :** approbation manuelle dans GitHub Actions (après UAT validés)

### Étape 1 — Tag de la release

```bash
git tag -a v1.0.0 -m "Release v1.0.0 — collectes digitales + recouvrement"
git push origin v1.0.0
docker tag ghcr.io/org/imf-api:latest ghcr.io/org/imf-api:v1.0.0
docker push ghcr.io/org/imf-api:v1.0.0
```

**Versioning Sémantique :** `vMAJEUR.MINEUR.CORRECTIF`
- MAJEUR : changement incompatible de l'API
- MINEUR : nouvelle fonctionnalité rétrocompatible
- CORRECTIF : bug fix

### Étape 2 — Fenêtre de maintenance

- **Horaire :** 23h00 – 01h00 (impact minimal sur les utilisateurs)
- **Notification préalable :** email aux DSI des IMF clientes 24h à l'avance
- **Durée estimée :** 20–30 minutes

### Étape 3 — Déploiement Serveur Pipeline (192.168.1.10)

```bash
ssh deploy@192.168.1.10
docker compose -f docker-compose.pipeline.yml pull
docker compose -f docker-compose.pipeline.yml up -d --no-deps airflow-scheduler
docker compose -f docker-compose.pipeline.yml up -d --no-deps airflow-webserver
```

### Étape 4 — Déploiement Serveur Application (192.168.1.11)

```bash
ssh deploy@192.168.1.11
docker compose -f docker-compose.app.yml pull
docker compose -f docker-compose.app.yml up -d --no-deps postgres-app
docker compose -f docker-compose.app.yml up -d --no-deps springboot-api
docker compose -f docker-compose.app.yml up -d --no-deps nginx
```

### Étape 5 — Migration Flyway Production

La migration Flyway s'exécute automatiquement. En cas d'échec, elle est rejoée à la prochaine tentative (pas de déploiement partiel).

**Vérification :**
```bash
docker logs imf-springboot-prod | grep -i "flyway"
# → Attendu : "Successfully applied N migration(s)"
```

### Étape 6 — Tests de Santé Production

```bash
# API
curl -f https://imf-app.local/api/health
# → {"status":"UP","db":"UP","redis":"UP"}

# Frontend
curl -f https://imf-app.local/

# Connexion DB Pipeline
docker exec imf-airflow-prod airflow db check

# FCM (ping Firebase)
curl -f https://fcm.googleapis.com/
```

**Si tests de santé KO :** rollback automatique (voir section 6).

---

## 6. Procédure de Rollback

### Rollback automatique (pipeline CI/CD)

Déclenché si les smoke tests ou les tests de santé post-déploiement échouent.

```bash
# Identifier la version précédente
PREVIOUS_TAG=$(git describe --tags --abbrev=0 HEAD^)
# Ex : v0.9.2

# Rollback sur le serveur application
docker compose -f docker-compose.app.yml stop springboot-api
docker tag ghcr.io/org/imf-api:$PREVIOUS_TAG ghcr.io/org/imf-api:rollback
docker compose -f docker-compose.app.yml up -d springboot-api

# Rollback Flyway si nécessaire
docker exec imf-springboot-prod java -jar app.jar --flyway.target=$PREVIOUS_SCHEMA_VERSION
```

### Rollback manuel

En cas d'incident découvert après la fenêtre de déploiement :

1. Informer le DSI de l'IMF concernée
2. Passer en mode maintenance (page statique Nginx)
3. Rollback de l'image Docker vers `vX.Y.Z-1`
4. Vérifier la cohérence des données (migration Flyway non destructive)
5. Communiquer la reprise de service

### Politique de rétention des images

| Tag | Rétention |
|---|---|
| `vX.Y.Z` (releases) | Permanente |
| `:latest` | 30 dernières versions |
| `:sha-commit` | 14 jours glissants |

---

## 7. Configuration GitHub Actions

### Structure des fichiers workflows

```
.github/workflows/
├── ci.yml           # Lint + tests + build (push sur feature/*, develop)
├── cd-staging.yml   # Deploy staging (merge develop)
└── cd-prod.yml      # Deploy prod (approbation manuelle)
```

### Variables & Secrets GitHub

| Variable / Secret | Type | Valeur |
|---|---|---|
| `REGISTRY_TOKEN` | Secret | Token GitHub Container Registry |
| `SSH_STAGING_HOST` | Variable | `192.168.1.50` |
| `SSH_STAGING_KEY` | Secret | Clé privée SSH staging |
| `SSH_PROD_PIPELINE_HOST` | Variable | `192.168.1.10` |
| `SSH_PROD_APP_HOST` | Variable | `192.168.1.11` |
| `SSH_PROD_KEY` | Secret | Clé privée SSH production |
| `SLACK_WEBHOOK_URL` | Secret | URL webhook Slack (optionnel) |
| `DB_PROD_PASSWORD` | Secret | Mot de passe PostgreSQL production |
| `JWT_SECRET_PROD` | Secret | Secret JWT production (≥ 256 bits) |
| `FCM_SERVER_KEY` | Secret | Clé serveur Firebase |

### Environnements GitHub

| Environnement | Protection | Approbateurs |
|---|---|---|
| `staging` | Aucune (automatique) | — |
| `production` | Approbation requise | Développeur + Maître de stage |
