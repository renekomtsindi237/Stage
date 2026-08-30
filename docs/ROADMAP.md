# Pipeline IMF Cameroun — Feuille de route

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## Version actuelle (V1) — Pipeline de données complet

**Statut :** En développement (soutenance prévue juillet 2026)

**Fonctionnalités livrées :**

### 1. Couche données (PostgreSQL 16)
- Migrations Flyway V1–V24 couvrant l'intégralité du schéma
- `app.*` : collectes_epargne, cycles_collecte, objectifs_collecte, clients_informels, produits_generiques (15 produits seedés), prix_produits, facteurs_macro, donnees_meteo, creances, promesses_paiement, benchmarks_agences, alertes_operationnelles
- `raw.*` : zone d'atterrissage des données brutes (collectes, CBS, prix, météo, macro)
- `dw.*` : schéma en étoile (6 tables de faits + 6 dimensions, vues de reporting)
- `ml.*` : feature store (43 features), client_scores, shap_explanations, model_runs, alertes_predictives

### 2. Pipeline d'orchestration (Apache Airflow 2.9 + dbt Core 1.8)
- `dag_collecte_epargne` : synchronisation collectes terrain toutes les 2 heures
- `dag_recouvrement` : calcul PAR COBAC + dossiers + benchmarks quotidiens
- `dag_donnees_externes` : prix produits génériques + météo + macro quotidiens
- `dag_ml_scoring` : scoring MCRS quotidien (43 features, batch 500 clients)
- `dag_ml_training` : retraining hebdomadaire XGBoost walk-forward (5 folds)
- 15+ modèles dbt en couches (staging → intermediate → mart → ml)

### 3. Modèle ML — MCRS (Multi-Criteria Recovery Scoring)
- Score composite [0,1] = 0.35×CRS + 0.45×RPS + 0.20×CSI
- XGBoost calibré Platt (RPS) + walk-forward temporel (5 folds, 12m/3m/1m gap)
- SHAP TreeExplainer (top 10 features par client)
- Détection dérive PSI (seuil 0.20 → retraining automatique)
- Classification : FAIBLE / MODÉRÉ / ÉLEVÉ / CRITIQUE

### 4. Backend API REST (Spring Boot 3.3 / Java 21)
- Multi-tenant complet (`imf_id` + TenantContext)
- JWT httpOnly cookies (`imf_access` 900s, `imf_refresh` 7j)
- RBAC : SUPER_ADMIN, DIRECTEUR, RESPONSABLE_RECOUVREMENT, ANALYSTE, DSI, AGENT
- Collectes : POST, sync batch (déduplication UUID), validation, KPI
- Créances : liste, détail, score MCRS+SHAP, statut
- KPI : dashboard directeur, recouvrement, agent, PAR stats, tendances prix, benchmarks
- SSE temps réel (Redis Pub/Sub → EventSource)

### 5. Application Web (Angular 17)
- Dashboard DIRECTEUR : KPI collecte + PAR COBAC + MCRS + benchmarks inter-agences
- Dashboard RESPONSABLE_RECOUVREMENT : dossiers prioritaires + promesses de paiement
- Dashboard AGENT : objectifs + KPI journalier
- Internationalisation fr/en, thème dark/light
- Mise à jour temps réel via SSE

### 6. Client bureau (Tauri 2)
- Encapsule le frontend Angular dans une WebView Windows
- Installeur NSIS `MicroRecouv_1.0.0_x64-setup.exe` (menu Démarrer, raccourci Bureau, logo `MicroRecouv.png`)
- API de production : `https://imf.rene.it.com`
- JWT Bearer + CORS origines Tauri

### 7. Application Mobile (Flutter)
- Saisie collecte offline-first (SQLite local)
- UUID v4 génération locale (déduplication côté serveur)
- Synchronisation batch automatique au retour en zone connectée
- Notifications push Firebase FCM

### 8. Documentation
- 8 diagrammes UML PlantUML (`docs/uml/`)
- Cahier des charges complet (`cahier_des_charges/` — 6 documents)
- Conception complète (`conception/` — 6 documents)
- Analyse complète (`analyse/` — 5 documents)
- Mémoire de fin d'études (en cours de rédaction)

---

## Évolutions envisagées post-mémoire

### V2 — Déploiement pilote IMF
**Horizon :** 3-4 mois après soutenance

- Connexion à un CBS IMF camerounais réel (export automatique planifié).
- Validation du modèle MCRS sur données réelles (collecte de 12 mois minimum).
- CI/CD GitHub Actions (lint + tests + build Docker + déploiement staging).
- Monitoring Prometheus + Grafana (métriques pipeline, latence API).
- Tests end-to-end Angular (Playwright) sur les flux critiques.

### V3 — Expansion fonctionnelle
**Horizon :** 6-9 mois après V2

- Rapport COBAC automatisé (PDF signé, archivage réglementaire).
- Analyse géospatiale des zones de risque (heatmap Leaflet.js).
- Coach agent intégré dans l'app mobile : affichage score MCRS client lors de la visite terrain.
- Boucle de feedback ML : enregistrement des actions post-alerte pour améliorer le modèle.
- API publique OpenAPI 3.1 pour intégration CBS partenaires.

### V4 — Expansion CEMAC
**Horizon :** 12-18 mois après V3

- Adaptation multi-régulation (Tchad, RCA, Congo, Gabon — mêmes seuils COBAC).
- Multi-devises (CDF, USD, EUR) avec taux de change automatique.
- Moteur de classification PAR configurable par pays/régulateur.
- SDK Python/Java pour intégration simplifiée des CBS.

---

## Jalons actuels

| Jalon | Statut | Date |
|---|---|---|
| Architecture et migrations V1-V24 | ✅ Terminé | Avril 2026 |
| Pipeline Airflow 5 DAGs | ✅ Terminé | Mai 2026 |
| Modèle MCRS (MCRSModel Python) | ✅ Terminé | Mai 2026 |
| Diagrammes UML (8 diagrammes) | ✅ Terminé | Mai 2026 |
| Dashboard web (Angular) | ✅ Terminé | Mai 2026 |
| Client bureau Tauri (installeur NSIS) | ✅ Terminé | Août 2026 |
| Application mobile Flutter | 🔄 En cours | Juin 2026 |
| Tests d'intégration backend | 🔄 En cours | Juin 2026 |
| Rédaction mémoire | 🔄 En cours | Juillet 2026 |
| Soutenance | 📅 Planifiée | Juillet 2026 |
