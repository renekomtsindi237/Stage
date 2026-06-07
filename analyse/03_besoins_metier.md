# 03 — Besoins Métier

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Méthode d'identification des besoins

Les besoins métier ont été identifiés par :
- Analyse de la réglementation COBAC EMF 01/02 (CEMAC).
- Revue des pratiques opérationnelles décrites dans la littérature sur la microfinance camerounaise.
- Étude des processus AS-IS décrits dans `01_analyse_de_lexistant.md`.
- Benchmarking des solutions sectorielles (`02_benchmark.md`).

---

## 2. Besoins métier — Collectes d'Épargne

### BM-CE01 — Saisie terrain fiable sans connectivité
**Acteur :** Agent de collecte
**Besoin :** L'agent doit pouvoir enregistrer les collectes de ses clients en zone sans réseau, avec garantie de non-perte des données et de non-doublon lors de la synchronisation.
**Priorité :** CRITIQUE
**Solution retenue :** Offline-first Flutter + SQLite local + UUID v4 + sync batch idempotent.

### BM-CE02 — Visibilité en temps réel des objectifs
**Acteur :** Agent, Responsable agence
**Besoin :** L'agent doit consulter à tout moment son taux de réalisation de l'objectif du cycle en cours. Le responsable doit voir l'avancement de son équipe en temps réel.
**Priorité :** HAUTE
**Solution retenue :** DAG toutes les 2h + KPI snapshots + SSE dashboard.

### BM-CE03 — Détection automatique des anomalies
**Acteur :** Responsable agence, DSI
**Besoin :** Détecter automatiquement les collectes anormales (montant aberrant, doublon d'UUID, collecte hors zone géographique habituelle).
**Priorité :** HAUTE
**Solution retenue :** Flags qualité dans `stg_collectes_epargne` (est_doublon, est_montant_aberrant, est_geolocalisee).

### BM-CE04 — Suivi des cycles et périodicités
**Acteur :** Directeur, Responsable agence
**Besoin :** Configurer des cycles de collecte avec des périodicités variables selon l'agence (hebdomadaire en zone rurale, bihebdomadaire en zone urbaine), avec des objectifs différenciés par agent.
**Priorité :** HAUTE
**Solution retenue :** Table `app.cycles_collecte` configurable + `app.objectifs_collecte`.

### BM-CE05 — Benchmarking inter-agences des collectes
**Acteur :** Directeur général
**Besoin :** Comparer les performances de collecte entre agences sur une base standardisée (z-scores) pour identifier les meilleures pratiques et les agences en difficulté.
**Priorité :** MOYENNE
**Solution retenue :** `app.benchmarks_agences` avec z-scores calculés par `dag_recouvrement`.

---

## 3. Besoins métier — Recouvrement de Créances

### BM-RC01 — Calcul automatisé PAR COBAC
**Acteur :** Responsable recouvrement, Analyste, Directeur
**Besoin :** Calculer automatiquement chaque jour le Portfolio at Risk aux seuils réglementaires (PAR30, PAR60, PAR90, PAR180), avec archivage historique pour tendances et reporting COBAC.
**Priorité :** CRITIQUE
**Réglementation :** COBAC EMF 01/02 — classification A à E obligatoire.
**Solution retenue :** DAG quotidien 06h00, `stg_creances` (SQL PAR), `app.kpi_recouvrement_snapshots`.

### BM-RC02 — Priorisation des dossiers par risque
**Acteur :** Responsable recouvrement
**Besoin :** Obtenir une liste des dossiers à traiter en priorité, classés selon un score de risque objectif combinant l'ancienneté du retard, le comportement de collecte passé et les facteurs contextuels.
**Priorité :** HAUTE
**Solution retenue :** Score MCRS (CRS+RPS+CSI) → tri des dossiers par `classe_risque_mcrs` DESC.

### BM-RC03 — Suivi des promesses de paiement
**Acteur :** Responsable recouvrement, Agent
**Besoin :** Enregistrer et suivre les engagements de paiement pris par les clients, avec alerte automatique si une promesse n'est pas respectée à la date prévue.
**Priorité :** HAUTE
**Solution retenue :** Table `app.promesses_paiement` + vérification quotidienne dans `dag_recouvrement`.

### BM-RC04 — Rapport COBAC automatisé
**Acteur :** Analyste, Directeur
**Besoin :** Générer automatiquement les données nécessaires aux rapports COBAC périodiques (PAR, provisions par classe, taux de couverture) sans ressaisie manuelle.
**Priorité :** CRITIQUE
**Solution retenue :** Snapshots quotidiens `app.kpi_recouvrement_snapshots` couvrant toutes les métriques COBAC.

### BM-RC05 — Intégration des facteurs externes dans l'analyse de risque
**Acteur :** Analyste, Responsable recouvrement
**Besoin :** Comprendre pourquoi un client en bonne santé financière se retrouve soudainement en retard, en corrélant son comportement avec les prix de ses produits sur les marchés locaux et les conditions météo de sa zone.
**Priorité :** HAUTE (contribution différenciante)
**Solution retenue :** CSI (Client Solvency Index) dans le modèle MCRS — prix_produit_x + météo + macro → feature store ML.

---

## 4. Besoins métier — Management et Pilotage

### BM-PI01 — Dashboard de pilotage unifié
**Acteur :** Directeur
**Besoin :** Disposer d'un tableau de bord unique présentant simultanément les KPI collectes et les indicateurs de recouvrement, avec comparaison inter-agences, sans avoir à naviguer entre plusieurs outils.
**Priorité :** HAUTE
**Solution retenue :** `DashboardDirecteurComponent` Angular avec SSE et KPI unifiés.

### BM-PI02 — Alertes proactives
**Acteur :** Directeur, Responsable recouvrement, Agent
**Besoin :** Recevoir des alertes automatiques sans avoir à consulter activement le dashboard : PAR dépassant un seuil, client passant en risque CRITIQUE, agent n'ayant aucune collecte depuis 48h.
**Priorité :** HAUTE
**Solution retenue :** `app.alertes_operationnelles` + `ml.alertes_predictives` + notifications push FCM + SSE.

### BM-PI03 — Historisation des indicateurs
**Acteur :** Analyste, Directeur
**Besoin :** Consulter l'évolution des KPI dans le temps (tendances sur 30/90 jours) pour détecter des dégradations progressives et mesurer l'impact des actions correctives.
**Priorité :** MOYENNE
**Solution retenue :** Snapshots historiques dans `dw.fact_collectes_epargne`, `dw.fact_creances`, `app.kpi_*_snapshots`.

---

## 5. Besoins métier — Technique et Administration

### BM-TA01 — Architecture multi-IMF (SaaS)
**Acteur :** SUPER_ADMIN
**Besoin :** La plateforme doit pouvoir héberger plusieurs IMF clientes sur la même infrastructure, avec isolation totale des données entre IMF.
**Priorité :** HAUTE
**Solution retenue :** Multi-tenant `imf_id` sur toutes les entités + TenantContext Spring Security.

### BM-TA02 — Configuration sans redéploiement
**Acteur :** DSI
**Besoin :** Modifier les paramètres opérationnels (objectifs de collecte, seuils d'alerte, périodicité des cycles) sans modifier le code source ni redéployer l'application.
**Priorité :** MOYENNE
**Solution retenue :** Tables de configuration dans `app.*` + interface admin Angular.

### BM-TA03 — Catalogue de produits génériques extensible
**Acteur :** DSI, SUPER_ADMIN
**Besoin :** Ajouter de nouveaux produits agricoles ou commerciaux au catalogue sans modifier le code du pipeline ML. Le modèle MCRS doit automatiquement intégrer les features des nouveaux produits.
**Priorité :** HAUTE (généricité du modèle)
**Solution retenue :** `app.produits_generiques` configurable + feat_client_externe dbt récupérant dynamiquement tous les produits actifs.

---

## 6. Cartographie des besoins vs fonctionnalités

| Besoin Métier | DAG concerné | Table(s) clé(s) | Endpoint API |
|---|---|---|---|
| BM-CE01 | dag_collecte_epargne | collectes_epargne, raw.collectes_terrain | POST /api/collectes-epargne/sync |
| BM-CE02 | dag_collecte_epargne | kpi_collecte_snapshots | GET /api/kpi/dashboard-agent |
| BM-RC01 | dag_recouvrement | stg_creances, kpi_recouvrement_snapshots | GET /api/kpi/par-stats |
| BM-RC02 | dag_ml_scoring | ml.client_scores, app.creances | GET /api/creances (trié par score) |
| BM-RC05 | dag_donnees_externes + dag_ml_scoring | prix_produits, features_client, ml.client_scores | GET /api/creances/{id}/score-mcrs |
| BM-PI01 | tous les DAGs | kpi_*_snapshots, benchmarks_agences | GET /api/kpi/dashboard-directeur |
| BM-PI02 | tous les DAGs | alertes_operationnelles, ml.alertes_predictives | GET /api/sse/events |
