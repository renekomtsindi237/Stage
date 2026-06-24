# MicroRecouv — Vue d'ensemble du projet

**Mémoire de fin de cycle**
**Auteur :** KOMTSINDI Réné Alban
**Institut Universitaire Saint Jean (IUSJ) — Yaoundé**
**Année académique : 2025-2026**

---

## 1. Problème résolu

Les établissements de microfinance (EMF) camerounais font face à deux défis opérationnels majeurs que les outils existants ne traitent pas de façon intégrée :

| Défi | Symptôme actuel | Conséquence |
|---|---|---|
| **Collectes terrain** | Agents sans connectivité, saisie papier, doublons | Données arrivant en retard, impossibles à agréger |
| **Recouvrement** | PAR calculé à la main, pas de priorisation | Provisions COBAC tardives, agents mobilisés sur de mauvais dossiers |

**MicroRecouv** est un pipeline de données complet qui automatise la collecte, la transformation, le scoring et la restitution de ces données — du téléphone de l'agent terrain jusqu'au tableau de bord du directeur.

---

## 2. Architecture en 4 couches

```
┌──────────────────────────────────────────────────────────────────────┐
│  COUCHE 1 — PRÉSENTATION                                             │
│  Angular 18 (web)                     Flutter 3.24 (mobile)         │
│  Dashboards, KPI, alertes             Collectes offline-first        │
└──────────────────────┬────────────────────────────┬─────────────────┘
                       │  HTTP/REST + SSE            │  HTTP/REST (sync batch)
┌──────────────────────▼────────────────────────────▼─────────────────┐
│  COUCHE 2 — API APPLICATIVE                                          │
│  Spring Boot 3.3 / Java 21                                           │
│  JWT multi-tenant · RBAC · Sync mobile · SSE · Scoring MCRS         │
└──────────────────────┬───────────────────────────────────────────────┘
                       │  JDBC / JPA
┌──────────────────────▼───────────────────────────────────────────────┐
│  COUCHE 3 — ENTREPÔT DE DONNÉES                                      │
│  PostgreSQL 16                                                        │
│  app.* opérationnel · raw.* ingestion · staging.* · dw.* (étoile)   │
│  ml.* feature store + scores + model_runs                            │
└──────────────────────┬───────────────────────────────────────────────┘
                       │  SQL / Python
┌──────────────────────▼───────────────────────────────────────────────┐
│  COUCHE 4 — PIPELINE DE DONNÉES                                      │
│  Apache Airflow 2.9 — orchestration des DAGs                         │
│  dbt Core — transformations SQL (staging → DW → feature store)       │
│  Python 3.11 — ML : XGBoost · SHAP · scikit-learn · FastAPI          │
│  Kafka + Flink — flux temps réel (alertes, KPI streaming)            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Flux de données de bout en bout

```
                    AGENT TERRAIN (offline)
                           │
                    Saisie dans l'app Flutter
                    UUID v4 généré localement
                           │
               ════════════╪════════════════════ (retour connexion)
                           │
                  POST /api/v1/sync/collectes
                           │
                    ┌──────▼──────┐
                    │   Backend   │  Déduplication par uuidMobile
                    │  Spring Boot│  Validation Bean Validation
                    └──────┬──────┘
                           │ INSERT app.collectes_terrain
                           │
              ┌────────────▼────────────────────┐
              │         PostgreSQL               │
              │  raw.* → staging.* → dw.* → ml.*│
              └────────────┬────────────────────┘
                           │
              ┌────────────▼────────────────────┐
              │  Apache Airflow (DAGs quotidiens)│
              │  dag_collectes                  │  → validation + agrégation
              │  dag_donnees_externes           │  → prix marchés + météo + macro
              │  dag_recouvrement               │  → PAR COBAC + dossiers
              │  dag_kpis_quotidien             │  → KPI dashboard
              │  dag_ml_scoring (07h30)         │  → MCRS score par client
              └────────────┬────────────────────┘
                           │
              ┌────────────▼────────────────────┐
              │  FastAPI ML (port 8090)          │
              │  MCRSScorer.score()              │
              │  → mcrs, crs, rps, csi, alertes │
              └────────────┬────────────────────┘
                           │
              ┌────────────▼────────────────────┐
              │  Backend Spring Boot             │
              │  SSE → Angular dashboard         │
              │  FCM → Flutter (notifications)   │
              └─────────────────────────────────┘
```

---

## 4. Les 5 domaines fonctionnels

### 4.1 Collectes d'épargne terrain
L'agent mobile collecte les dépôts en zone sans réseau. Chaque collecte est sauvegardée localement avec un UUID v4. À la reconnexion, le lot est envoyé en batch vers le backend. Le serveur déduplique par UUID et confirme ou rejette chaque entrée.

### 4.2 Recouvrement de créances
Le pipeline calcule chaque jour le PAR (Portfolio at Risk) à 30, 60, 90 et 180 jours, applique la classification COBAC (classes A à E avec taux de provision 0 % à 100 %), génère automatiquement les dossiers de recouvrement et met à jour leur priorité selon le score MCRS.

### 4.3 Scoring prédictif MCRS
Chaque client avec une créance active reçoit chaque matin un score **MCRS ∈ [0, 1]** combinant :
- son comportement de collecte (CRS)
- la probabilité de défaut à 90 jours prédite par XGBoost (RPS)
- sa résilience économique face aux facteurs externes (CSI)

### 4.4 Données externes génériques
Le pipeline ingère chaque jour les prix de produits génériques sur les marchés locaux (maïs, manioc, arachide, coton…), les précipitations par zone géographique (ANAMET), et les indicateurs macro (inflation BEAC, IPC INS). Ces données enrichissent le score CSI et rendent le modèle sensible aux cycles agricoles camerounais.

### 4.5 Tableaux de bord et alertes
- **Directeur :** PAR global, taux de collecte, évolution MCRS, benchmarks inter-agences
- **Responsable recouvrement :** dossiers priorisés par MCRS, taux de promesses tenues, alertes PAR90
- **Agent :** objectifs de collecte du jour, historique, synchronisation

---

## 5. Stack technologique résumée

| Composant | Technologie | Rôle |
|---|---|---|
| Mobile | Flutter 3.24 + SQLite | Collectes offline-first |
| Web | Angular 18 + signals | Dashboards temps réel |
| API | Spring Boot 3.3 / Java 21 | Logique métier, sécurité |
| Base de données | PostgreSQL 16 | Stockage opérationnel + DW |
| Orchestration | Apache Airflow 2.9 | Planification des pipelines |
| Transformations | dbt Core | Staging → DW → Feature store |
| ML | Python 3.11 + XGBoost | Scoring MCRS |
| Streaming | Apache Kafka + Flink | Alertes temps réel |
| Stockage fichiers | Cloudflare R2 | Avatars, documents KYC |
| Monitoring | Prometheus + Grafana | Santé infra + pipeline |
| Déploiement | Docker Compose + Nginx | Conteneurisation multi-env |
| CI/CD | GitHub Actions | Tests, build, déploiement auto |

---

## 6. Conformité réglementaire

Le système implémente deux cadres réglementaires :

**COBAC — Règlement EMF 01/02 CEMAC (créances)**

| Classe | Retard | Provision |
|---|---|---|
| A | < 30 j | 0 % |
| B | 30–89 j | 20 % |
| C | 90–179 j | 50 % |
| D | 180–359 j | 80 % |
| E | ≥ 360 j | 100 % |

**Loi 2024/017 (protection des données personnelles)**
Masquage des données PII (noms, téléphones) selon le rôle de l'utilisateur — les agents de recouvrement voient les données complètes, les analystes voient les données masquées (`A*** F*** A***`).

---

## 7. Multi-tenant

Chaque institution (IMF) est isolée par un `imf_id` propagé dans chaque requête JWT et dans chaque requête SQL. Un super-administrateur (SUPER_ADMIN) peut superviser toutes les IMF depuis un tableau de bord consolidé. Il est impossible pour un utilisateur d'une IMF d'accéder aux données d'une autre.
