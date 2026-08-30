# 02 — Objectifs et Périmètre

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Objectif général

Concevoir et implémenter un pipeline de données end-to-end permettant aux institutions de microfinance camerounaises de centraliser, transformer et analyser leurs données de collectes d'épargne terrain et de recouvrement de créances, afin de produire des indicateurs de pilotage conformes aux exigences COBAC et un scoring prédictif multi-critères des risques clients (MCRS).

## 2. Objectifs spécifiques

### 2.1 Pipeline de données

- **OBJ-1** : Concevoir une couche d'ingestion robuste capable de traiter les collectes terrain saisies offline (Flutter, UUID v4) et les exports CBS des créances, avec déduplication, idempotence et journal d'ingestion.
- **OBJ-2** : Implémenter des transformations dbt en couches (staging → intermediate → mart → ml) normalisant et enrichissant les données brutes pour le Data Warehouse.
- **OBJ-3** : Orchestrer le pipeline via Apache Airflow avec des DAGs différenciés par domaine fonctionnel (collectes, recouvrement, données externes, scoring ML).

### 2.2 Data Warehouse

- **OBJ-4** : Modéliser un schéma en étoile (`dw.*`) incluant les tables de faits collectes, créances, actions de recouvrement et prix de produits génériques, et les dimensions associées.
- **OBJ-5** : Implémenter un historique temporel des KPI via des snapshots journaliers (PAR COBAC, taux de réalisation des collectes, benchmarks inter-agences).

### 2.3 Scoring prédictif MCRS

- **OBJ-6** : Concevoir le modèle MCRS (Multi-Criteria Recovery Scoring) combinant trois composantes : CRS (Collection Reliability Score), RPS (Recovery Prediction Score via XGBoost), CSI (Client Solvency Index avec facteurs externes génériques).
- **OBJ-7** : Implémenter l'explicabilité du modèle via SHAP TreeExplainer (top 10 features par client) et la détection de dérive (PSI > 0.20 → retraining automatique).
- **OBJ-8** : Valider le modèle par validation croisée temporelle walk-forward (5 folds, 12 mois d'entraînement, 3 mois de test, 1 mois de gap).

### 2.4 Application web, bureau et mobile

- **OBJ-9** : Développer un dashboard DIRECTEUR temps réel présentant les KPI collecte, le PAR COBAC par agence, les scores MCRS et les benchmarks inter-agences.
- **OBJ-10** : Implémenter la synchronisation offline-first de l'application mobile Flutter avec déduplication UUID côté serveur.
- **OBJ-10b** : Fournir un client bureau Windows (Tauri) installable comme une application bureautique, encapsulant le frontend Angular et connecté à `https://imf.rene.it.com`.

### 2.5 Conformité et sécurité

- **OBJ-11** : Automatiser le calcul et l'archivage des indicateurs COBAC (PAR30/60/90/180, provisions classes A-E, taux de recouvrement).
- **OBJ-12** : Garantir l'isolation multi-tenant (`imf_id` sur toutes les entités) et la sécurité des accès via JWT httpOnly cookies.

## 3. Périmètre fonctionnel

### 3.1 Ce qui est inclus

| Module | Fonctionnalités |
|---|---|
| **Collectes d'épargne** | Saisie mobile offline, sync batch, validation agent/responsable, objectifs par cycle, KPI journaliers/hebdomadaires |
| **Recouvrement de créances** | Classification COBAC automatique, calcul PAR, dossiers de recouvrement, promesses de paiement, prioritisation |
| **Données externes** | Prix produits génériques (marché local), météo 10 zones Cameroun, indicateurs macro BEAC/INS/FMI |
| **Scoring MCRS** | CRS + RPS + CSI, SHAP, alertes prédictives, détection de dérive PSI |
| **Dashboards** | DIRECTEUR (KPI globaux + benchmarks), RESPONSABLE_RECOUVREMENT (dossiers + PAR), AGENT (objectifs + collectes) |
| **Pipeline** | Airflow DAGs, dbt transformations, feature store ML |
| **Mobile** | Saisie collecte offline, consultation KPI agent, push notifications |
| **Bureau** | Installeur Windows NSIS (Tauri), mêmes dashboards Angular, API `https://imf.rene.it.com` |

### 3.2 Ce qui est exclu

- Gestion du cycle de crédit (instruction, scoring d'octroi, déblocage, remboursement courant).
- Comptabilité générale et trésorerie.
- Gestion des ressources humaines et paie.
- Gestion de la relation client au-delà du profil informel.

## 4. Résultats attendus

| Livrable | Description |
|---|---|
| Pipeline opérationnel | 5 DAGs Airflow en production, 20+ modèles dbt, feature store ML |
| Data Warehouse | Schéma étoile `dw.*`, vues de reporting, snapshots historiques |
| Modèle MCRS | Score composite [0,1] avec AUC > 0.78 sur validation walk-forward |
| Application web | Dashboard DIRECTEUR temps réel avec mise à jour SSE |
| Application bureau | Installeur NSIS Windows, même interface Angular, API de production |
| Application mobile | Sync offline-first avec déduplication côté serveur |
| Documentation | Cahier des charges, conception, UML (8 diagrammes), mémoire de fin d'études |

## 5. Contraintes de temps

| Jalon | Échéance |
|---|---|
| Finalisation architecture et migrations DB | Avril 2026 |
| Pipeline Airflow + dbt opérationnel | Mai 2026 |
| Modèle MCRS entraîné et intégré | Mai 2026 |
| Dashboards web + mobile fonctionnels | Juin 2026 |
| Rédaction et soutenance mémoire | Juillet 2026 |
