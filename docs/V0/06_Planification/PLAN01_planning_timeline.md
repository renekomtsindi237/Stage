# PLAN01 — Planning & Timeline de Développement
## Plateforme IMF — Pipeline · Backend · Web · Mobile · ML

---

| Champ | Valeur |
|---|---|
| **Document** | Planning Timeline (PLAN01) |
| **Version** | 1.0 |
| **Date début** | 2026-04-01 |
| **Durée totale** | 18 semaines |
| **Statut** | Actif |

---

## TABLE DES MATIÈRES

1. [Vue d'ensemble du planning](#1-vue-densemble-du-planning)
2. [Phase 0 — Setup & Infrastructure](#2-phase-0--setup--infrastructure)
3. [Phase 1 — Pipeline de Données](#3-phase-1--pipeline-de-données)
4. [Phase 2 — Backend Spring Boot](#4-phase-2--backend-spring-boot)
5. [Phase 3 — Application Web Angular](#5-phase-3--application-web-angular)
6. [Phase 4 — Application Mobile Flutter](#6-phase-4--application-mobile-flutter)
7. [Phase 5 — Modèle ML Scoring](#7-phase-5--modèle-ml-scoring)
8. [Phase 6 — Intégration & Tests](#8-phase-6--intégration--tests)
9. [Phase 7 — Déploiement](#9-phase-7--déploiement)
10. [Jalons & Livrables](#10-jalons--livrables)

---

## 1. Vue d'ensemble du planning

### Résumé des phases

| Phase | Contenu | Semaines | Dates estimées | Durée |
|---|---|---|---|---|
| **Phase 0** | Setup & Infrastructure | S1 | 01/04 – 07/04/2026 | 1 sem. |
| **Phase 1** | Pipeline de Données | S2–S6 | 08/04 – 10/05/2026 | 5 sem. |
| **Phase 2** | Backend Spring Boot | S7–S10 | 11/05 – 07/06/2026 | 4 sem. |
| **Phase 3** | Application Web Angular | S11–S13 | 08/06 – 28/06/2026 | 3 sem. |
| **Phase 4** | Application Mobile Flutter | S14–S16 | 29/06 – 19/07/2026 | 3 sem. |
| **Phase 5** | ML Scoring XGBoost | S17–S18 | 20/07 – 02/08/2026 | 2 sem. |
| **Phase 6** | Intégration & Tests E2E | S19–S20 | 03/08 – 16/08/2026 | 2 sem. |
| **Phase 7** | Déploiement & Go-live | S21–S22 | 17/08 – 31/08/2026 | 2 sem. |

### Conventions de travail

- **Jours ouvrés** : lundi–vendredi (samedis et dimanches exclus)
- **Branches Git** : `main` (prod) / `develop` (intégration) / `feature/*` (développement)
- **Commits** : Conventional Commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`)
- **Revues de code** : Pull Request obligatoire avant merge dans `develop`
- **Tests** : chaque tâche inclut ses tests avant clôture

---

## 2. Phase 0 — Setup & Infrastructure

**Durée :** 1 semaine (S1 — 01/04 au 07/04/2026)
**Objectif :** Poser les fondations techniques avant tout développement fonctionnel.

### Tâches

| # | Tâche | Durée | Priorité |
|---|---|---|---|
| 0.1 | Installation Docker Engine + Docker Compose | 1 j | Critique |
| 0.2 | Création des dépôts Git (main / develop / feature/*) | 0.5 j | Critique |
| 0.3 | Rédaction `.gitignore`, `.env.example`, `README.md` | 0.5 j | Haute |
| 0.4 | Définition schémas PostgreSQL (`raw`, `staging`, `dw`) | 1 j | Critique |
| 0.5 | Rédaction `docker-compose.dev.yml` (tous services) | 1 j | Critique |
| 0.6 | Test de démarrage complet de l'environnement local | 1 j | Haute |

### Checklist de sortie Phase 0

- [ ] `docker compose up` démarre sans erreur
- [ ] PostgreSQL accessible sur `:5432` avec les 3 schémas créés
- [ ] Airflow UI accessible sur `:8080`
- [ ] Superset accessible sur `:8088`
- [ ] Variables d'environnement documentées dans `.env.example`
- [ ] Premier commit sur `main` avec structure de répertoires

---

## 3. Phase 1 — Pipeline de Données

**Durée :** 5 semaines (S2–S6 — 08/04 au 10/05/2026)
**Technologies :** Python 3.11 · Apache Airflow 2.8 · dbt Core · PostgreSQL 15
**Objectif :** Automatiser l'ingestion, la transformation et le calcul des KPIs.

### Tâches

| # | Tâche | Durée | Dépendance |
|---|---|---|---|
| 1.1 | DAG `dag_ingestion_mtn` (CSV MTN → schéma `raw`) | 5 j | Phase 0 |
| 1.2 | DAG `dag_ingestion_orange` (CSV Orange → schéma `raw`) | 3 j | 1.1 |
| 1.3 | Déduplication SHA-256 + `ON CONFLICT DO NOTHING` | 2 j | 1.1, 1.2 |
| 1.4 | Modèles dbt `raw → staging` (`stg_collectes_mtn`, `stg_collectes_orange`, `stg_prets`) | 4 j | 1.3 |
| 1.5 | Modèles dbt `staging → dw` (schéma en étoile : `fact_collectes`, `fact_remboursements`, dimensions) | 4 j | 1.4 |
| 1.6 | DAG `dag_kpis_quotidien` — calcul PAR30/PAR90 par zone, produit, agent | 3 j | 1.5 |
| 1.7 | DAG `dag_alertes_impayes` — INSERT `staging.alertes_impayes` + notification SMTP/FCM | 3 j | 1.6 |
| 1.8 | Tests dbt (unicité, not null, FK) + tests DAGs | 3 j | 1.7 |

### Modèles dbt à livrer

```
models/
├── staging/
│   ├── stg_collectes_mtn.sql
│   ├── stg_collectes_orange.sql
│   └── stg_prets.sql
└── dw/
    ├── fact_collectes.sql
    ├── fact_remboursements.sql
    ├── dim_client.sql
    ├── dim_produit.sql
    └── dim_agence.sql
```

### Checklist de sortie Phase 1

- [ ] Ingestion MTN + Orange fonctionne en bout en bout sur fichiers CSV réels
- [ ] Déduplication vérifiée (re-ingestion du même fichier → 0 doublon)
- [ ] `dbt run` complète sans erreur
- [ ] `dbt test` : 0 test en échec
- [ ] PAR30 et PAR90 calculés et stockés dans `dw.kpis_par`
- [ ] Alerte générée automatiquement pour un prêt en retard de test

---

## 4. Phase 2 — Backend Spring Boot

**Durée :** 4 semaines (S7–S10 — 11/05 au 07/06/2026)
**Technologies :** Spring Boot 3 · Java 17 · Spring Security · JWT · JPA/Hibernate · Flyway · Redis
**Objectif :** Exposer les données du Data Warehouse via une API REST sécurisée.

### Tâches

| # | Tâche | Durée | Dépendance |
|---|---|---|---|
| 2.1 | Initialisation projet Spring Boot 3 (Java 17) + Flyway | 1 j | Phase 1 |
| 2.2 | Entités JPA : `Client`, `Pret`, `Collecte`, `Alerte`, `Agent`, `Agence`, `Produit`, `Utilisateur`, `Role` | 2 j | 2.1 |
| 2.3 | Spring Security + génération/validation JWT | 4 j | 2.2 |
| 2.4 | API REST — collectes & prêts (`GET /api/collectes`, `POST /api/collectes`, `GET /api/prets/{id}/historique`) | 4 j | 2.3 |
| 2.5 | API REST — alertes & recouvrement (`GET /api/alertes`, `PATCH /api/alertes/{id}/statut`) | 3 j | 2.4 |
| 2.6 | API REST — KPIs & reporting (`GET /api/kpis/par`, `GET /api/kpis/collectes`, `GET /api/reporting/export-csv`) | 3 j | 2.5 |
| 2.7 | Intégration Firebase Admin SDK (push FCM) + cache Redis | 2 j | 2.5 |
| 2.8 | Tests unitaires (JUnit 5 + Mockito) + tests d'intégration (Testcontainers) | 3 j | 2.7 |
| 2.9 | Documentation Swagger / OpenAPI 3 | 1 j | 2.8 |

### Endpoints API à livrer

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authentification, retourne JWT |
| `GET` | `/api/collectes` | MANAGER, DSI | Liste des collectes avec filtres |
| `POST` | `/api/collectes` | AGENT | Saisie collecte terrain |
| `GET` | `/api/prets/{id}/historique` | MANAGER | Historique remboursements |
| `GET` | `/api/alertes` | MANAGER, RR | Liste alertes impayés actives |
| `PATCH` | `/api/alertes/{id}/statut` | RR | Clôturer / escalader alerte |
| `GET` | `/api/kpis/par` | MANAGER, DSI | PAR30/PAR90 par dimension |
| `GET` | `/api/kpis/collectes` | MANAGER | Volume collectes par canal |
| `GET` | `/api/reporting/export-csv` | DSI | Export données en CSV |
| `GET` | `/api/scoring/{collecte_id}` | DSI | Score ML d'une transaction |

### Checklist de sortie Phase 2

- [ ] `POST /api/auth/login` retourne un JWT valide
- [ ] Endpoints protégés rejettent les requêtes sans JWT (401)
- [ ] `POST /api/collectes` accepté par l'app mobile (test Postman)
- [ ] Couverture tests unitaires ≥ 70 %
- [ ] Swagger accessible sur `/swagger-ui.html`
- [ ] Notification FCM envoyée lors d'une nouvelle alerte

---

## 5. Phase 3 — Application Web Angular

**Durée :** 3 semaines (S11–S13 — 08/06 au 28/06/2026)
**Technologies :** Angular 17 · TypeScript · Angular Material · Chart.js · Nginx
**Objectif :** Interface de gestion complète pour managers, DSI et responsables recouvrement.

### Tâches

| # | Tâche | Durée | Dépendance |
|---|---|---|---|
| 3.1 | Initialisation Angular 17 + routing + guards (`AuthGuard`, `RoleGuard`) | 2 j | Phase 2 |
| 3.2 | Module authentification (login, déconnexion, intercepteur JWT) | 3 j | 3.1 |
| 3.3 | Dashboard collectes digitales (graphiques Chart.js : canal, zone, période) + tableau réconciliation | 4 j | 3.2 |
| 3.4 | Dashboard recouvrement (liste alertes, carte thermique PAR par zone) | 4 j | 3.3 |
| 3.5 | Module reporting (export CSV/PDF, comparatif mensuel) | 3 j | 3.4 |
| 3.6 | Module administration pipeline (statut DAGs Airflow, logs d'ingestion) | 2 j | 3.5 |
| 3.7 | Tests Jasmine/Karma (composants critiques) | 2 j | 3.6 |

### Modules Angular à livrer

```
src/app/
├── auth/             (login, token service, intercepteur)
├── collectes/        (dashboard, tableau réconciliation)
├── recouvrement/     (alertes, PAR heatmap, relances)
├── portefeuille/     (détail client, historique prêt)
├── reporting/        (exports, comparatifs)
└── admin/            (pipeline DAGs, logs ingestion)
```

### Checklist de sortie Phase 3

- [ ] Login / logout fonctionnel avec token JWT stocké en mémoire
- [ ] Dashboard collectes affiche les données du pipeline
- [ ] Alertes impayés affichées et clôturables depuis le web
- [ ] Export CSV téléchargeable
- [ ] `ng build --prod` compile sans erreur
- [ ] Tests Jasmine : 0 échec

---

## 6. Phase 4 — Application Mobile Flutter

**Durée :** 3 semaines (S14–S16 — 29/06 au 19/07/2026)
**Technologies :** Flutter 3 · Dart · Riverpod · SQLite · Dio · Firebase Messaging (FCM)
**Objectif :** Application terrain pour les agents avec mode offline-first et push notifications.

### Tâches

| # | Tâche | Durée | Dépendance |
|---|---|---|---|
| 4.1 | Initialisation Flutter 3 + Riverpod + navigation GoRouter | 2 j | Phase 2 |
| 4.2 | Authentification JWT + `FlutterSecureStorage` + refresh token | 2 j | 4.1 |
| 4.3 | Formulaire saisie collecte online (validation Dart, `POST /api/collectes`) | 3 j | 4.2 |
| 4.4 | Mode offline SQLite (`statut = PENDING_SYNC`) + synchronisation automatique au retour réseau | 5 j | 4.3 |
| 4.5 | Réception push FCM (foreground overlay + background notification bar) | 3 j | 4.3 |
| 4.6 | Écran consultation KPIs mobile (PAR30/PAR90 du jour, collectes de la journée) | 3 j | 4.5 |
| 4.7 | Tests widget + tests d'intégration Flutter | 2 j | 4.6 |

### Statuts de synchronisation SQLite

| Statut | Description |
|---|---|
| `PENDING_SYNC` | Collecte enregistrée hors ligne, pas encore synchronisée |
| `CONFIRMED` | Synchronisée avec succès vers l'API |
| `DUPLICATE` | Rejetée par l'API (409 — déjà enregistrée) |
| `SYNC_ERROR` | Erreur réseau, retry à la prochaine tentative |

### Checklist de sortie Phase 4

- [ ] Login mobile fonctionne et token est persisté
- [ ] Collecte saisie online enregistrée dans PostgreSQL via API
- [ ] Collecte saisie offline stockée en SQLite et synchronisée au retour réseau
- [ ] Notification FCM reçue en foreground et en background
- [ ] APK de test généré et installable sur Android
- [ ] Tests Flutter : 0 échec

---

## 7. Phase 5 — Modèle ML Scoring

**Durée :** 2 semaines (S17–S18 — 20/07 au 02/08/2026)
**Technologies :** Python · scikit-learn · XGBoost · joblib · Airflow
**Objectif :** Scorer automatiquement chaque transaction pour détecter les anomalies.

### Tâches

| # | Tâche | Durée | Dépendance |
|---|---|---|---|
| 5.1 | Feature engineering depuis `staging` (montant, canal, heure, zone, historique client) | 3 j | Phase 1 |
| 5.2 | Entraînement et validation du modèle XGBoost (AUC-ROC, F1, Précision) | 4 j | 5.1 |
| 5.3 | DAG `dag_scoring_quotidien` — scoring de toutes les nouvelles transactions | 3 j | 5.2 |
| 5.4 | Endpoint `GET /api/scoring/{collecte_id}` dans Spring Boot | 2 j | 5.3 |
| 5.5 | Validation modèle en conditions réelles + feedback loop mensuel | 2 j | 5.4 |

### Features du modèle

| Feature | Type | Description |
|---|---|---|
| `montant_normalise` | Numérique | Montant de la transaction normalisé |
| `canal` | Catégoriel | MTN / Orange / Espèces |
| `heure_transaction` | Numérique | Heure de la journée (0–23) |
| `zone_geographique` | Catégoriel | Zone d'agence |
| `historique_retards` | Numérique | Nombre de retards passés du client |
| `anciennete_client` | Numérique | Nombre de mois depuis l'ouverture du compte |
| `ratio_remboursement` | Numérique | Taux de remboursement historique |

### Seuils de scoring

| Score | Niveau de risque | Traitement |
|---|---|---|
| 0.00 – 0.50 | Faible | Validation automatique (auto-posting) |
| 0.51 – 0.75 | Moyen | Validation automatique avec flag |
| 0.76 – 1.00 | Élevé | File de révision manuelle |

### Checklist de sortie Phase 5

- [ ] Modèle entraîné avec AUC-ROC ≥ 0.80 sur données de validation
- [ ] `dag_scoring_quotidien` s'exécute sans erreur
- [ ] Table `dw.scores_transactions` alimentée quotidiennement
- [ ] Endpoint API retourne le score en < 100 ms
- [ ] Feedback loop documenté et testé

---

## 8. Phase 6 — Intégration & Tests

**Durée :** 2 semaines (S19–S20 — 03/08 au 16/08/2026)
**Objectif :** Valider l'ensemble du système de bout en bout avant déploiement.

### Tâches

| # | Tâche | Durée | Type |
|---|---|---|---|
| 6.1 | Tests E2E scénario collecte : CSV MTN → Pipeline → API → Angular | 2 j | Fonctionnel |
| 6.2 | Tests E2E scénario mobile : saisie Flutter → API → Pipeline → Dashboard | 2 j | Fonctionnel |
| 6.3 | Tests E2E scénario alerte : DAG → FCM → Flutter + email DSI | 1 j | Fonctionnel |
| 6.4 | Tests de performance JMeter (100 utilisateurs simultanés) | 3 j | Performance |
| 6.5 | Audit sécurité OWASP Top 10 (injection, XSS, JWT, secrets) | 3 j | Sécurité |
| 6.6 | Correction des bugs et vulnérabilités identifiés | 4 j | Correctif |

### Critères de qualité

| Critère | Cible |
|---|---|
| Temps de réponse API (P95) | < 500 ms |
| Taux d'erreur API sous charge | < 1 % |
| Couverture tests unitaires Backend | ≥ 70 % |
| Score OWASP Top 10 | 0 vulnérabilité critique |
| Tests dbt | 100 % succès |

### Checklist de sortie Phase 6

- [ ] Tous les scénarios E2E passent
- [ ] JMeter : P95 < 500 ms sous 100 utilisateurs
- [ ] Aucune vulnérabilité OWASP critique non corrigée
- [ ] Rapport de tests consolidé rédigé
- [ ] Release candidate taguée sur Git (`v1.0.0-rc1`)

---

## 9. Phase 7 — Déploiement

**Durée :** 2 semaines (S21–S22 — 17/08 au 31/08/2026)
**Objectif :** Mise en production sur l'infrastructure de l'IMF cliente.

### Tâches

| # | Tâche | Durée | Responsable |
|---|---|---|---|
| 7.1 | Rédaction runbooks de déploiement + procédures de rollback | 2 j | Dev |
| 7.2 | Configuration serveurs Ubuntu 22.04 (firewall UFW, Docker, monitoring) | 2 j | Dev + DSI |
| 7.3 | Rédaction `docker-compose.prod.yml` (Pipeline + Application séparés) | 2 j | Dev |
| 7.4 | Configuration Nginx + certificat TLS Let's Encrypt | 1 j | Dev + DSI |
| 7.5 | Migration données CBS initiale (`pg_restore` + Flyway) | 2 j | Dev + DSI |
| 7.6 | Déploiement staging + tests UAT | 3 j | Dev + Maître de stage |
| 7.7 | Formation utilisateurs : RR + DSI (2 sessions de 2h) | 2 j | Dev |
| 7.8 | Go-live production (fenêtre 23h–01h) | 1 j | Dev + DSI |
| 7.9 | Support post-déploiement J+5 (hotfixes) | 5 j | Dev |

### Checklist de sortie Phase 7 (Go-live)

- [ ] Serveurs opérationnels (CPU, RAM, disque dans les limites)
- [ ] `docker compose up -d` sans erreur sur les 2 serveurs
- [ ] Nginx sert le frontend Angular via HTTPS `:443`
- [ ] API Spring Boot répond sur `/api/health` avec HTTP 200
- [ ] Flyway migrations appliquées sans erreur
- [ ] Premier DAG Airflow exécuté avec succès en production
- [ ] Application Flutter se connecte à l'API production
- [ ] Backup pg_dump configuré et testé
- [ ] Monitoring Netdata + Uptime Kuma actif
- [ ] `v1.0.0` tagué sur Git et CHANGELOG rédigé

---

## 10. Jalons & Livrables

### Jalons principaux

| Jalon | Date estimée | Critère de validation |
|---|---|---|
| **J0 — Environnement prêt** | 07/04/2026 | `docker compose up` démarre tous les services |
| **J1 — Pipeline opérationnel** | 10/05/2026 | Ingestion CSV → DW → KPIs automatisés |
| **J2 — API disponible** | 07/06/2026 | Tous les endpoints documentés Swagger répondent |
| **J3 — Web opérationnel** | 28/06/2026 | Dashboard accessible et alimenté par l'API |
| **J4 — Mobile opérationnel** | 19/07/2026 | APK fonctionnel en online et offline |
| **J5 — ML intégré** | 02/08/2026 | Scoring quotidien actif dans le pipeline |
| **J6 — Tests validés** | 16/08/2026 | 0 bug critique, performance conforme |
| **J7 — GO-LIVE** | 28/08/2026 | Système en production, utilisateurs formés |

### Livrables documentaires

| Livrable | Format | Phase |
|---|---|---|
| Cahier des Charges | Markdown | Pré-projet |
| Cahier d'Analyse | Markdown | Pré-projet |
| Architecture système | Markdown + PlantUML | Pré-projet |
| Conception données & UML | Markdown + PlantUML | Pré-projet |
| Documentation API (Swagger) | OpenAPI 3 JSON | Phase 2 |
| Manuel utilisateur | Markdown / PDF | Phase 7 |
| Runbook exploitation | Markdown | Phase 7 |
| Rapport de tests | Markdown | Phase 6 |
| CHANGELOG | Markdown | Phase 7 |
