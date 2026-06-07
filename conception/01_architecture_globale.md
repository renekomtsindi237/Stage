# 01 — Architecture Globale du Système

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Vue d'ensemble

Le système est organisé en **quatre couches principales** interconnectées, chacune ayant une responsabilité distincte dans le flux de données :

```
┌─────────────────────────────────────────────────────────────────────┐
│                        COUCHE PRÉSENTATION                          │
│   Application Web (Angular 17)     Application Mobile (Flutter)     │
└─────────────────────────┬───────────────────────┬───────────────────┘
                          │ HTTP/REST + SSE        │ HTTP/REST (sync)
┌─────────────────────────▼───────────────────────▼───────────────────┐
│                        COUCHE APPLICATIVE                           │
│              Backend API REST (Spring Boot 3.3 / Java 21)           │
│    Spring Security (JWT) • JPA/Hibernate • Multi-tenant (imf_id)    │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ JDBC/JPA
┌─────────────────────────▼───────────────────────────────────────────┐
│                        COUCHE DONNÉES                               │
│                    PostgreSQL 16 (schémas)                          │
│   app.* (opérationnel) • raw.* (ingestion) • dw.* (entrepôt)       │
│   staging.* • intermediate.* • ml.* (feature store + scores)        │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────────┐
│                        COUCHE PIPELINE                              │
│    Apache Airflow 2.9 (orchestration) + dbt Core (transformations)  │
│    Python 3.11 (ML: XGBoost, SHAP, scikit-learn)                    │
│    Sources : Mobile sync • CBS exports • APIs externes              │
└─────────────────────────────────────────────────────────────────────┘
```

## 2. Couche Présentation

### 2.1 Application Web — Angular 17
- Module-based avec lazy loading par domaine fonctionnel.
- Reactive forms et gestion d'état locale (services RxJS).
- Internationalisation : `ngx-translate` (français/anglais).
- Thème sombre/clair via variables CSS.
- Mise à jour temps réel des dashboards via **Server-Sent Events (SSE)**.
- Cookies JWT httpOnly avec `withCredentials: true`.

**Modules principaux :**
- `dashboard/` : DashboardDirecteurComponent, DashboardRecouvrementComponent, DashboardAgentComponent.
- `collectes/` : Saisie, liste, validation des collectes d'épargne.
- `creances/` : Liste des créances, détail avec score MCRS, gestion dossiers.
- `admin/` : Gestion utilisateurs, IMF, paramètres.

### 2.2 Application Mobile — Flutter (offline-first)
- Stockage local SQLite pour les collectes en attente de synchronisation.
- Génération d'UUID v4 côté mobile pour chaque collecte (déduplication).
- Synchronisation batch vers `POST /api/collectes-epargne/sync` au retour en zone connectée.
- Notifications push via Firebase Cloud Messaging (FCM).

---

## 3. Couche Applicative — Backend Spring Boot

### 3.1 Architecture interne
```
Controllers (REST) → Services (interfaces IService) → Repositories (JPA)
                  ↓
        Spring Security (JWT Filter → TenantContext)
                  ↓
        PostgreSQL (schéma app.*)
```

### 3.2 Sécurité
- Spring Security avec filtre JWT extrayant `imf_id` et `role` du token.
- `TenantContext` (ThreadLocal) propagé à tous les appels JPA.
- RBAC via `@PreAuthorize` sur chaque endpoint.

### 3.3 Principaux controllers
| Controller | Endpoint base | Rôles |
|---|---|---|
| `AuthController` | `/api/auth` | PUBLIC |
| `CollecteEpargneController` | `/api/collectes-epargne` | AGENT, RESP_REC, DIRECTEUR |
| `CreanceController` | `/api/creances` | RESP_REC, DIRECTEUR, ANALYSTE |
| `KpiController` | `/api/kpi` | DIRECTEUR, ANALYSTE, RESP_REC |
| `AdminController` | `/api/admin` | SUPER_ADMIN, DSI |

### 3.4 Notifications SSE
- Endpoint `GET /api/sse/events` : flux SSE par IMF.
- Le pipeline publie des événements (`kpi_collecte_updated`, `recouvrement_updated`, `scoring_updated`) via Redis Pub/Sub → SSE controller.

---

## 4. Couche Données — PostgreSQL 16

### 4.1 Schémas
| Schéma | Rôle | Tables principales |
|---|---|---|
| `app.*` | Données opérationnelles applicatives | collectes_epargne, creances, cycles_collecte, clients_informels, produits_generiques, prix_produits, facteurs_macro, donnees_meteo, alertes_operationnelles |
| `raw.*` | Zone d'atterrissage des données brutes | collectes_terrain, export_cbs, prix_marche, donnees_meteo, indicateurs_macro, journal_ingestions |
| `staging.*` | Données nettoyées et typées (dbt) | stg_collectes_epargne, stg_creances, stg_prix_produits, stg_indicateurs_macro, stg_meteo |
| `intermediate.*` | Agrégats intermédiaires (dbt) | int_collectes_par_agent, int_profil_recouvrement_client |
| `dw.*` | Entrepôt de données — schéma en étoile | fact_collectes_epargne, fact_creances, dim_date, dim_client, dim_agent, dim_agence, dim_produit_generique |
| `ml.*` | Feature store et scores ML | features_client, client_scores, shap_explanations, model_runs, alertes_predictives |

### 4.2 Migrations Flyway (V1–V24)
Les migrations sont appliquées séquentiellement au démarrage du backend :
- V1–V18 : schéma initial, multi-tenant, recouvrement COBAC de base, GPS.
- **V19** : collectes_epargne, cycles_collecte, objectifs_collecte.
- **V20** : clients_informels, produits_generiques (seed 15 produits), client_activites_produits.
- **V21** : prix_produits, facteurs_macro, donnees_meteo, evenements_exterieurs, marches_locaux.
- **V22** : creances (enrichi), promesses_paiement, kpi_recouvrement_snapshots.
- **V23** : schéma ml.* complet (features, scores, SHAP, model_runs, alertes).
- **V24** : kpi_collecte_snapshots, benchmarks_agences, alertes_operationnelles.

---

## 5. Couche Pipeline — Airflow + dbt + Python ML

### 5.1 DAGs Airflow
| DAG | Schedule | Fonction principale |
|---|---|---|
| `dag_collecte_epargne` | `0 */2 * * *` (toutes les 2h) | Sync collectes → validation → dbt → KPI → alertes opérationnelles |
| `dag_recouvrement` | `0 6 * * *` (06h00) | CBS → PAR COBAC → dossiers → benchmarks → alertes → email |
| `dag_donnees_externes` | `0 4 * * *` (04h00) | Prix produits + météo + macro + événements → dbt → maj app |
| `dag_ml_scoring` | `30 7 * * *` (07h30) | Features → MCRS → SHAP → alertes ML → drift PSI |
| `dag_ml_training` | `0 2 * * 0` (dimanche 02h00) | Walk-forward → XGBoost → Platt → champion/challenger |

### 5.2 Couches dbt
```
raw.* ──► staging.* ──► intermediate.* ──► dw.* (mart)
                                      └──► ml.* (feature store)
```

### 5.3 Modèle ML — MCRS
Score composite [0,1] :
```
MCRS = 0.35 × CRS + 0.45 × RPS + 0.20 × CSI
```
- **CRS** : régularité et tendance des collectes d'épargne de l'agent/client.
- **RPS** : XGBoost calibré (Platt scaling) → P(défaut 90 jours).
- **CSI** : indice de résilience économique — diversification produits, volatilité prix génériques, météo, indicateurs macro.
- Walk-forward temporel : 5 folds, 12 mois entraînement, 3 mois test, 1 mois gap.
- SHAP TreeExplainer : top 10 features par client.
- Détection dérive PSI : seuil 0.20 → retraining automatique.

---

## 6. Flux de données bout en bout

### 6.1 Flux collectes d'épargne
```
Agent mobile (offline) → SQLite local
  → sync batch → POST /api/collectes-epargne/sync
  → Spring Boot : déduplication UUID → app.collectes_epargne (SOUMISE)
  → Airflow dag_collecte_epargne (toutes les 2h) :
      raw.collectes_terrain → stg_collectes_epargne → int_collectes_par_agent
      → dw.fact_collectes_epargne → app.kpi_collecte_snapshots
      → alertes opérationnelles → SSE → Dashboard DIRECTEUR
```

### 6.2 Flux recouvrement
```
CBS (export CSV) → dépôt zone transfert
  → Airflow dag_recouvrement (06h00) :
      raw.export_cbs → stg_creances (PAR + COBAC calculé) → int_profil_recouvrement_client
      → dw.fact_creances → app.creances (mise à jour)
      → app.kpi_recouvrement_snapshots → app.benchmarks_agences
      → alertes PAR → email responsables → SSE Dashboard
```

### 6.3 Flux scoring MCRS
```
Airflow dag_ml_scoring (07h30) :
  features_comportementales (stg_collectes + int_profil)
  + features_externes (prix_produits + meteo + macro) [parallèle]
  → ml.features_client (feature store)
  → MCRSModel.predict_batch() → ml.client_scores + ml.shap_explanations
  → ml.alertes_predictives → app.creances (maj score_mcrs)
  → PSI si > 0.20 → trigger dag_ml_training
```

---

## 7. Diagrammes UML associés

Cf. `docs/uml/` :
- `01_use_case.puml` — Acteurs et cas d'utilisation.
- `02_sequence_collecte.puml` — Flux collecte offline-first.
- `03_sequence_scoring_mcrs.puml` — Pipeline MCRS journalier.
- `04_classes_domaine.puml` — Modèle de domaine complet.
- `05_composants.puml` — Vue composants système.
- `06_deploiement.puml` — Docker Compose cible.
- `07_activite_recouvrement.puml` — Workflow recouvrement COBAC.
- `08_sequence_auth.puml` — Authentification JWT multi-tenant.
