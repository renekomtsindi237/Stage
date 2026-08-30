# 06 — Justification des Choix Technologiques

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Critères de sélection

Les technologies ont été sélectionnées selon les critères suivants :
1. **Open source et sans licence** : contrainte budgétaire des EMF camerounaises.
2. **Maturité et production-ready** : fiabilité prouvée en production.
3. **Adapté au contexte** : fonctionnement en ressources contraintes, pas de cloud élastique.
4. **Ecosystème ML intégré** : support Python pour le scoring MCRS.
5. **Maintenabilité** : pile connue par les développeurs camerounais locaux.

---

## 2. Backend — Spring Boot 3.3 / Java 21

### Choix et alternatives considérées
| Technologie | Avantages | Inconvénients | Décision |
|---|---|---|---|
| **Spring Boot 3.3 / Java 21** | Écosystème mature, Spring Security robuste, JPA natif, LTS Java 21 (Virtual Threads) | Verbosité Java | **Retenu** |
| FastAPI (Python) | Rapidité de développement, même langage que le pipeline | Moins robuste pour auth multi-tenant complexe | Non retenu |
| Node.js / Express | Léger, rapide | Moins structuré pour les projets complexes, typage faible | Non retenu |
| Quarkus | GraalVM natif, startup rapide | Écosystème moins mature, courbe d'apprentissage | Non retenu |

### Justification
Spring Boot 3.3 avec Java 21 offre les Virtual Threads (Project Loom) pour une meilleure gestion de la concurrence SSE, Spring Security pour le multi-tenant JWT, et un écosystème JPA/Hibernate robuste pour la gestion du schéma `app.*`. C'est également la technologie la plus documentée pour les projets bancaires/fintech en Afrique francophone.

**Fonctionnalités clés utilisées :**
- Spring Security 6 (filterChain, JWT, RBAC).
- Spring Data JPA + Hibernate (repositories, entités).
- Flyway (migrations de schéma V1–V24).
- Spring SSE (SseEmitter ou WebFlux Flux).
- Spring Validation (records DTOs avec annotations Bean Validation).

---

## 3. Base de données — PostgreSQL 16

### Justification
PostgreSQL est le seul SGBD open source supportant nativement :
- Les **schémas multiples** dans une même instance (app.*, raw.*, dw.*, ml.*).
- Les **types de données avancés** : UUID, JSONB, TEXT[], NUMERIC précis pour les montants financiers.
- Les **fonctions de fenêtre** SQL (ROW_NUMBER, LAG, AVG OVER) utilisées dans les modèles dbt.
- Les **partitions de table** pour les tables de faits DW et snapshots.
- La conformité ACID pour les données financières sensibles.

**Version 16** apporte des améliorations de performance sur les requêtes analytiques (parallélisme amélioré).

**Redis** est utilisé comme couche Pub/Sub pour les événements SSE (pas de persistance de données).

---

## 4. Pipeline — Apache Airflow 2.9

### Justification
Airflow est l'orchestrateur de référence pour les pipelines ELT :
- DAGs Python natifs → intégration directe des tâches ML (MCRSModel).
- Scheduler robuste avec gestion des retries, SLAs et backfill.
- Interface web pour le monitoring des exécutions.
- Support des dépendances inter-tâches (TaskGroup) pour la parallélisation.
- Connecteurs natifs PostgreSQL et HTTP.

**Version 2.9** supporte les TaskFlow API simplifiées et les Dynamic Task Mapping utiles pour le scoring par batch.

**Alternatives écartées :** Prefect (payant pour certaines fonctions avancées), Luigi (moins actif), Dagster (excellent mais surcoût apprentissage).

---

## 5. Transformations — dbt Core

### Justification
dbt Core (open source) transforme les données SQL avec :
- **Versionnement des modèles** : chaque transformation est un fichier `.sql` versionné dans Git.
- **Tests intégrés** : `not_null`, `unique`, `accepted_values`, `relationships` sur chaque modèle.
- **Documentation automatique** : `dbt docs generate` produit un catalogue interactif.
- **Incremental models** : la clause `incremental` évite de retraiter tout l'historique à chaque exécution.
- **Lignage** : graphe de dépendances automatique entre tous les modèles.

**Avantage clé :** La séparation staging/intermediate/mart/ml correspond exactement aux besoins du pipeline ELT multi-couches.

---

## 6. Machine Learning — XGBoost + SHAP + scikit-learn

### XGBoost (composante RPS)
- État de l'art sur les données tabulaires (Kaggle benchmarks, Chen & Guestrin 2016).
- Support natif de `scale_pos_weight` pour le déséquilibre classes (défauts rares).
- TreeExplainer SHAP natif → explicabilité sans surcoût.
- Calibration Platt via `sklearn.calibration.CalibratedClassifierCV`.

### SHAP (explicabilité)
- TreeExplainer : O(T·L·D) pour les arbres → rapide sur un modèle XGBoost.
- Top 10 features par client stockées dans `ml.shap_explanations`.
- Rapport global d'entraînement : `shap.summary_plot` pour identifier les features dominantes.

### scikit-learn
- `TimeSeriesSplit` pour la validation walk-forward temporelle.
- `CalibratedClassifierCV` pour la calibration Platt.
- `PipelinePipeline` pour les préprocesseurs (StandardScaler, encodeurs).

### Python 3.11
- Améliorations de performance vs 3.10 (Faster CPython, 10-60% plus rapide sur code Python pur).
- Compatible avec toutes les bibliothèques ML requises.

---

## 7. Frontend Web — Angular 17

### Justification
- **Lazy loading par module** : performances sur les connexions lentes camerounaises.
- **Reactive Forms** : formulaires complexes (cycles, objectifs) avec validation côté client.
- **ngx-translate** : internationalisation français/anglais sans rechargement.
- **Angular Material** : composants UI cohérents (tables, cartes, graphiques via Chart.js).
- **RxJS SSE** : `EventSource` natif encapsulé dans un service Observable pour les mises à jour temps réel.

**Angular 17** apporte les Signals et le nouveau control flow (`@if`, `@for`) pour de meilleures performances de rendu.

---

## 8. Client bureau — Tauri 2

### Choix et alternatives considérées
| Technologie | Avantages | Inconvénients | Décision |
|---|---|---|---|
| **Tauri 2** | Binaire léger (~6 Mo), WebView système, installeur NSIS, réutilise Angular | Dépend de WebView2 sous Windows | **Retenu** |
| Electron | Écosystème mature | Chromium embarqué, 150–200 Mo, RAM élevée sur PC d’agence | Non retenu |
| Flutter Desktop | Une base avec le mobile | Ne reprend pas les dashboards Angular | Non retenu |
| PWA seule | Aucun installeur à maintenir | Pas de Setup.exe ni d’entrée « Applications » native | Complément, pas suffisant |

### Justification
Les postes d’agence (directeur, recouvrement, DSI) ont besoin d’une application installable comme les outils bureautiques, sans réécrire l’interface. Tauri charge le `dist` Angular et parle à l’API déjà déployée (`https://imf.rene.it.com`). L’installeur NSIS crée le menu Démarrer, propose un raccourci Bureau et s’enregistre pour la désinstallation Windows.

---

## 9. Application Mobile — Flutter

### Justification
- **Cross-platform** : une seule base de code pour Android (cible principale : Android 8+).
- **Offline-first natif** : `sqflite` (SQLite local) + `flutter_secure_storage` + connectivity_plus.
- **UUID v4** : `uuid` package pour la génération locale d'identifiants de collecte.
- **Push notifications** : Firebase Cloud Messaging (FCM) pour les alertes agent.
- **Performance** : rendu Skia natif, proche des performances natives.

**Alternative React Native écartée** : performances inférieures sur appareils Android d'entrée de gamme (dominants en zone rurale camerounaise).

---

## 10. Infrastructure — Docker Compose

### Architecture de déploiement

```yaml
services:
  postgres:    # PostgreSQL 16, ports internes uniquement
  redis:       # Redis 7, cache SSE + sessions temporaires
  backend:     # Spring Boot 3.3, port 8080 interne
  nginx:       # Reverse proxy, TLS, port 443 public
  airflow-web: # Interface Airflow
  airflow-scheduler: # Scheduler DAGs
  airflow-worker:    # Exécution tâches (Celery ou LocalExecutor)
  angular:     # Build statique servi par Nginx
```

### Justification Docker Compose vs Kubernetes
- **Ressources contraintes** : un serveur dédié 8 CPU / 16 Go RAM — pas besoin d'un orchestrateur distribué.
- **Simplicité opérationnelle** : une IMF avec un DSI non spécialisé en cloud peut opérer la plateforme.
- **Migration future** : les images Docker sont identiques si une migration vers Kubernetes est envisagée.

---

## 11. Récapitulatif de la pile technologique

| Couche | Technologie | Version | Licence |
|---|---|---|---|
| Backend API | Spring Boot / Java | 3.3 / 21 | Apache 2.0 |
| Base de données | PostgreSQL | 16 | PostgreSQL License |
| Cache / SSE | Redis | 7 | BSD |
| Orchestration | Apache Airflow | 2.9 | Apache 2.0 |
| Transformations | dbt Core | 1.8 | Apache 2.0 |
| ML — Boosting | XGBoost | 2.0 | Apache 2.0 |
| ML — Explicabilité | SHAP | 0.45 | MIT |
| ML — Utilitaires | scikit-learn | 1.4 | BSD |
| Frontend Web | Angular | 17 | MIT |
| Client bureau | Tauri | 2 | MIT / Apache 2.0 |
| Mobile | Flutter / Dart | 3.19 | BSD |
| Migrations DB | Flyway | 9 | Apache 2.0 |
| Infrastructure | Docker Compose | v2 | Apache 2.0 |
| Reverse proxy | Nginx | 1.25 | BSD |
