# PLAN02 — Work Breakdown Structure (WBS)
## Plateforme IMF — Décomposition complète des livrables

---

| Champ | Valeur |
|---|---|
| **Document** | Work Breakdown Structure (PLAN02) |
| **Version** | 1.0 |
| **Date** | 2026-04-01 |

---

## TABLE DES MATIÈRES

1. [Phase 0 — Setup](#1-phase-0--setup)
2. [Phase 1 — Pipeline de Données](#2-phase-1--pipeline-de-données)
3. [Phase 2 — Backend Spring Boot](#3-phase-2--backend-spring-boot)
4. [Phase 3 — Application Web Angular](#4-phase-3--application-web-angular)
5. [Phase 4 — Application Mobile Flutter](#5-phase-4--application-mobile-flutter)
6. [Phase 5 — ML Scoring](#6-phase-5--ml-scoring)
7. [Phase 6 — Intégration & Tests](#7-phase-6--intégration--tests)
8. [Phase 7 — Déploiement](#8-phase-7--déploiement)

---

## 1. Phase 0 — Setup

### 1.1 Environnement local
- [ ] Docker Engine + Docker Compose installés
- [ ] PostgreSQL 15 (conteneur) démarré
- [ ] Airflow 2.8 (conteneur) démarré
- [ ] Superset 3.0 (conteneur) démarré

### 1.2 Source Control
- [ ] Dépôt Git initialisé (`main` / `develop` / `feature/*`)
- [ ] `.gitignore` configuré (`.env`, `__pycache__`, `node_modules`, `target/`)
- [ ] `.env.example` documenté (toutes les variables requises)
- [ ] Conventions de commit définies (Conventional Commits)
- [ ] `README.md` rédigé (installation, lancement, arborescence)

### 1.3 Base de données
- [ ] Schéma `raw` créé (tables sources brutes)
- [ ] Schéma `staging` créé (données nettoyées et normalisées)
- [ ] Schéma `dw` créé (Data Warehouse en étoile)
- [ ] Schéma `reporting` créé (vues agrégées pour Superset)

---

## 2. Phase 1 — Pipeline de Données

### 2.1 Ingestion (Python + Airflow)
- [ ] `dag_ingestion_mtn.py` — lecture CSV MTN → INSERT `raw.transactions_mtn`
- [ ] `dag_ingestion_orange.py` — lecture CSV Orange → INSERT `raw.transactions_orange`
- [ ] Calcul hash SHA-256 par ligne de transaction
- [ ] Déduplication : `INSERT ... ON CONFLICT (hash) DO NOTHING`
- [ ] Gestion des erreurs (fichier manquant, colonnes inattendues) + alerte email DSI
- [ ] Archivage automatique des fichiers CSV traités dans `data/archives/`

### 2.2 Transformation (dbt Core)

#### Couche Staging
- [ ] `models/staging/stg_collectes_mtn.sql`
- [ ] `models/staging/stg_collectes_orange.sql`
- [ ] `models/staging/stg_prets.sql`
- [ ] `models/staging/stg_clients.sql`
- [ ] `models/staging/stg_agents.sql`

#### Couche Data Warehouse (schéma en étoile)
- [ ] `models/dw/fact_collectes.sql`
- [ ] `models/dw/fact_remboursements.sql`
- [ ] `models/dw/dim_client.sql`
- [ ] `models/dw/dim_produit.sql`
- [ ] `models/dw/dim_agence.sql`
- [ ] `models/dw/dim_date.sql`

#### Tests dbt
- [ ] Unicité des clés primaires (`unique`)
- [ ] Valeurs non nulles (`not_null`)
- [ ] Intégrité référentielle (`relationships`)
- [ ] Valeurs acceptées (`accepted_values` pour `statut`, `canal`)

### 2.3 Calcul KPIs
- [ ] `dag_kpis_quotidien.py` — déclenché quotidiennement à 07h00
- [ ] Calcul PAR30 (portefeuille à risque > 30 jours)
- [ ] Calcul PAR90 (portefeuille à risque > 90 jours)
- [ ] Taux de collecte digitale par canal (MTN / Orange / Espèces)
- [ ] Volume de collectes par zone géographique et par agent
- [ ] Stockage dans `dw.kpis_par` et `dw.kpis_collectes`

### 2.4 Alertes automatiques
- [ ] `dag_alertes_impayes.py` — déclenché après `dag_kpis_quotidien`
- [ ] Détection prêts dépassant le seuil PAR30 configuré
- [ ] `INSERT INTO staging.alertes_impayes` (id_pret, jours_retard, montant, statut=ACTIVE)
- [ ] Envoi email SMTP au responsable recouvrement de la zone
- [ ] Envoi push FCM via Spring Boot API (`POST /internal/alertes/notify`)

---

## 3. Phase 2 — Backend Spring Boot

### 3.1 Infrastructure
- [ ] Projet Spring Boot 3 initialisé (Java 17, Maven)
- [ ] Dépendances : Spring Web, Spring Security, Spring Data JPA, Flyway, Redis, Swagger
- [ ] Migrations Flyway : `V1__init_schema.sql`, `V2__seed_roles.sql`
- [ ] Configuration Spring Security (`SecurityFilterChain`)
- [ ] Swagger / OpenAPI 3 configuré et accessible sur `/swagger-ui.html`

### 3.2 Entités JPA

| Entité | Table | Description |
|---|---|---|
| `Client` | `app.clients` | Client de l'IMF |
| `Pret` | `app.prets` | Prêt accordé |
| `Collecte` | `app.collectes` | Remboursement ou collecte |
| `Alerte` | `app.alertes` | Impayé signalé |
| `Agent` | `app.agents` | Agent de terrain |
| `Agence` | `app.agences` | Agence de l'IMF |
| `Produit` | `app.produits` | Produit financier |
| `Utilisateur` | `app.utilisateurs` | Compte applicatif |
| `Role` | `app.roles` | Rôle (AGENT, MANAGER, DSI, RR, DIRECTEUR) |

### 3.3 API REST

#### Auth
- [ ] `POST /api/auth/login` — retourne `{token, expiresIn}`
- [ ] `POST /api/auth/refresh` — rafraîchit le token JWT

#### Collectes
- [ ] `GET /api/collectes` — liste paginée avec filtres (agent, date, canal)
- [ ] `POST /api/collectes` — création collecte terrain (rôle AGENT)
- [ ] `GET /api/collectes/{id}` — détail + score ML

#### Prêts
- [ ] `GET /api/prets` — liste avec PAR courant
- [ ] `GET /api/prets/{id}/historique` — tous les remboursements

#### Alertes
- [ ] `GET /api/alertes` — liste filtrée par zone / statut
- [ ] `PATCH /api/alertes/{id}/statut` — ACTIVE → RESOLUE / ESCALADEE / WRITE_OFF

#### KPIs
- [ ] `GET /api/kpis/par` — PAR30/PAR90 par zone, produit, agent
- [ ] `GET /api/kpis/collectes` — volume par canal et par période

#### Reporting
- [ ] `GET /api/reporting/export-csv` — export CSV des collectes filtrées
- [ ] `GET /api/scoring/{collecte_id}` — score ML de la transaction

### 3.4 Services transversaux
- [ ] `PushNotificationService` — Firebase Admin SDK (envoi FCM)
- [ ] `EmailService` — JavaMailSender (alertes SMTP)
- [ ] `ScoringService` — lecture `dw.scores_transactions`
- [ ] `KpiCacheService` — mise en cache Redis (TTL 1h)
- [ ] `DuplicateDetectionService` — vérification doublon sur `reference + date`

---

## 4. Phase 3 — Application Web Angular

### 4.1 Infrastructure
- [ ] Angular 17 initialisé avec Angular Material
- [ ] Routing avec `RouterModule` + lazy loading par module
- [ ] `AuthGuard` et `RoleGuard` configurés
- [ ] `JwtInterceptor` (ajout header `Authorization: Bearer`)
- [ ] `ErrorInterceptor` (gestion 401, 403, 500)
- [ ] Service `AuthService` (login, logout, stockage token en mémoire)

### 4.2 Module Auth
- [ ] Page de login (formulaire Angular Material)
- [ ] Redirection post-login selon le rôle utilisateur
- [ ] Déconnexion + purge token
- [ ] Gestion de l'expiration du token (redirection automatique)

### 4.3 Module Collectes Digitales
- [ ] Graphique volume collectes par canal (Chart.js — barres empilées)
- [ ] Graphique évolution temporelle (Chart.js — courbes)
- [ ] Tableau des collectes avec filtres (date, canal, zone, agent)
- [ ] Tableau réconciliation (transactions MTN/Orange vs CBS)
- [ ] Export CSV du tableau courant

### 4.4 Module Recouvrement
- [ ] Liste des alertes impayés (triées par jours de retard)
- [ ] Actions : clôturer / escalader / write-off une alerte
- [ ] Carte thermique PAR par zone géographique (Chart.js)
- [ ] Historique des relances d'un client

### 4.5 Module Portefeuille Créances
- [ ] Fiche client (informations + prêts actifs)
- [ ] Historique des remboursements avec statut PAR
- [ ] Timeline des relances associées

### 4.6 Module Reporting
- [ ] Export CSV collectes avec filtres personnalisés
- [ ] Comparatif mensuel (tableau mois N vs mois N-1)
- [ ] Indicateurs globaux (PAR30 global, taux de collecte digital)

### 4.7 Module Administration Pipeline
- [ ] Statut des DAGs Airflow (embed Airflow UI ou API Airflow REST)
- [ ] Logs des dernières ingestions (succès / erreurs)
- [ ] Déclenchement manuel d'un DAG (bouton + confirmation)

---

## 5. Phase 4 — Application Mobile Flutter

### 5.1 Infrastructure
- [ ] Flutter 3 initialisé avec architecture Feature-First
- [ ] Riverpod pour la gestion d'état
- [ ] GoRouter pour la navigation
- [ ] Dio pour les appels HTTP + intercepteur JWT
- [ ] `FlutterSecureStorage` pour stocker le token JWT
- [ ] `ConnectivityPlus` pour la détection réseau

### 5.2 Authentification
- [ ] Écran de login (formulaire + validation)
- [ ] Appel `POST /api/auth/login` + stockage JWT dans `FlutterSecureStorage`
- [ ] Refresh token automatique (intercepteur Dio)
- [ ] Déconnexion + purge token + retour login

### 5.3 Saisie Collecte
- [ ] Formulaire de saisie (sélection client, prêt, montant, canal, référence MTN/Orange)
- [ ] Validation Dart (montant > 0, référence non vide, client requis)
- [ ] **Mode online :** `POST /api/collectes` → confirmation avec ID `COL-2026-XXXX`
- [ ] **Mode offline :** INSERT SQLite avec `statut = PENDING_SYNC`
- [ ] Affichage du mode actif (icône réseau dans l'AppBar)

### 5.4 Synchronisation Offline
- [ ] Détection retour de connectivité (`ConnectivityPlus`)
- [ ] Lecture `SELECT * FROM collectes WHERE statut = PENDING_SYNC`
- [ ] Pour chaque collecte : `POST /api/collectes`
  - [ ] Succès (201) → `UPDATE statut = CONFIRMED`
  - [ ] Doublon (409) → `UPDATE statut = DUPLICATE`
  - [ ] Erreur réseau → `UPDATE statut = SYNC_ERROR` (retry suivant)
- [ ] Notification in-app "N collecte(s) synchronisée(s)"

### 5.5 Alertes Push FCM
- [ ] Configuration `firebase_messaging` dans `pubspec.yaml`
- [ ] Gestion foreground : overlay avec détail de l'alerte
- [ ] Gestion background : notification bar système
- [ ] Navigation vers l'écran détail alerte au clic
- [ ] Badge sur l'icône de l'app (non lues)

### 5.6 Consultation KPIs Mobile
- [ ] PAR30 et PAR90 de la journée (carte synthèse)
- [ ] Volume de collectes de la journée par l'agent connecté
- [ ] Top 5 clients en retard de la zone de l'agent

---

## 6. Phase 5 — ML Scoring

### 6.1 Préparation des données
- [ ] Script `feature_engineering.py` — extraction et normalisation des features depuis `staging`
- [ ] Encodage des variables catégorielles (canal, zone) — One-Hot Encoding
- [ ] Normalisation des variables numériques (StandardScaler)
- [ ] Gestion des valeurs manquantes (imputation médiane)
- [ ] Séparation train/validation (80/20)

### 6.2 Modèle
- [ ] Entraînement XGBoost (`xgboost.XGBClassifier`)
- [ ] Recherche d'hyperparamètres (GridSearchCV ou Optuna)
- [ ] Évaluation : AUC-ROC, F1-score, Précision, Rappel sur jeu de validation
- [ ] Sérialisation avec `joblib` → `models/scoring_model_v1.pkl`
- [ ] Documentation de la version du modèle (features utilisées, performances)

### 6.3 Intégration Pipeline
- [ ] `dag_scoring_quotidien.py` — charge le modèle sérialisé, prédit sur toutes les nouvelles transactions du jour
- [ ] INSERT `dw.scores_transactions` (id_collecte, score_risque, version_modele, scored_at)
- [ ] Log des métriques de dérive dans `dw.model_metrics`

### 6.4 Intégration Backend
- [ ] `GET /api/scoring/{collecte_id}` lit `dw.scores_transactions`
- [ ] Réponse : `{score: 0.82, niveau: "ELEVE", version: "v1.0", scored_at: "..."}`

### 6.5 Feedback Loop
- [ ] Script mensuel `retrain_model.py` — réentraîne sur les nouvelles données validées manuellement
- [ ] Comparaison performances v_nouvelle vs v_actuelle
- [ ] Déploiement automatique si AUC-ROC >= seuil configuré

---

## 7. Phase 6 — Intégration & Tests

### 7.1 Tests End-to-End

| Scénario | Étapes | Critère |
|---|---|---|
| **E2E-01** | CSV MTN → Airflow → PostgreSQL → API → Angular | Données visibles dans le dashboard web |
| **E2E-02** | Saisie Flutter (online) → API → PostgreSQL → Pipeline → Dashboard | Collecte visible après prochaine exécution DAG |
| **E2E-03** | Saisie Flutter (offline) → SQLite → retour réseau → sync → API | Collecte confirmée après sync |
| **E2E-04** | DAG alertes → `staging.alertes_impayes` → FCM → Flutter | Notification reçue en < 30 s |
| **E2E-05** | Connexion simultanée 50 utilisateurs → Angular → API | Temps de réponse P95 < 500 ms |

### 7.2 Tests de Performance (JMeter)
- [ ] Plan de charge : 100 utilisateurs simultanés, 10 minutes
- [ ] Endpoints cibles : `GET /api/kpis/par`, `GET /api/alertes`, `POST /api/collectes`
- [ ] Critères : P95 < 500 ms, taux erreur < 1 %
- [ ] Rapport JMeter exporté en HTML

### 7.3 Audit Sécurité (OWASP Top 10)
- [ ] A01 — Contrôle d'accès cassé : tester accès cross-rôle
- [ ] A02 — Échecs cryptographiques : vérifier stockage token, HTTPS forcé
- [ ] A03 — Injection : tests injection SQL sur tous les endpoints filtrés
- [ ] A07 — Échecs d'authentification : brute force, expiration JWT
- [ ] Scan dépendances : OWASP Dependency-Check (Maven + Dart pub)

---

## 8. Phase 7 — Déploiement

### 8.1 Infrastructure Production
- [ ] Serveur Pipeline : Ubuntu 22.04, 8 cœurs, 16 Go RAM, 200 Go SSD — IP `192.168.1.10`
- [ ] Serveur Application : Ubuntu 22.04, 8 cœurs, 16 Go RAM, 100 Go SSD — IP `192.168.1.11`
- [ ] Docker Engine installé sur les deux serveurs
- [ ] Firewall UFW : autoriser uniquement ports 22 (SSH), 80 (HTTP), 443 (HTTPS)
- [ ] Utilisateur `imf-deploy` (non-root) configuré avec accès Docker

### 8.2 Fichiers Docker Compose Production
- [ ] `docker-compose.pipeline.yml` — Airflow + PostgreSQL pipeline + Superset
- [ ] `docker-compose.app.yml` — Nginx + Spring Boot + PostgreSQL app + Redis
- [ ] Variables d'environnement injectées via `.env.prod` (non versionné)
- [ ] Restart policies (`unless-stopped`) configurées sur tous les services

### 8.3 Configuration Nginx
- [ ] Certificat TLS Let's Encrypt obtenu via `certbot`
- [ ] Redirection HTTP → HTTPS (301)
- [ ] Proxy inverse `/api/*` → Spring Boot `:8080`
- [ ] Service fichiers statiques Angular → `/var/www/angular-dist/`
- [ ] Headers de sécurité : `X-Frame-Options`, `X-Content-Type-Options`, `HSTS`

### 8.4 Migration Données
- [ ] Dump CBS initial importé via `pg_restore`
- [ ] Migrations Flyway appliquées dans l'ordre (`V1` → `Vn`)
- [ ] Vérification intégrité référentielle (contraintes FK)
- [ ] Backup initial avant migration archivé

### 8.5 Monitoring & Exploitation
- [ ] Netdata installé (métriques CPU, RAM, disque, réseau en temps réel)
- [ ] Uptime Kuma configuré (vérification `/api/health` toutes les 60 s)
- [ ] Alertes email si service down
- [ ] Cron `pg_dump` quotidien à 23h00 → `/backups/imf_$(date +%Y%m%d).sql.gz`
- [ ] Rotation des logs Docker (max-size: 50m, max-file: 5)

### 8.6 Livraison Finale
- [ ] Manuel utilisateur rédigé (Responsable Recouvrement + DSI)
- [ ] Runbook exploitation rédigé (démarrage, arrêt, restart, rollback)
- [ ] Formation session 1 : Responsable Recouvrement (web + alertes)
- [ ] Formation session 2 : DSI (pipeline, Airflow, monitoring)
- [ ] `v1.0.0` tagué sur Git
- [ ] `CHANGELOG.md` mis à jour
- [ ] Signature du PV de recette avec le maître de stage
