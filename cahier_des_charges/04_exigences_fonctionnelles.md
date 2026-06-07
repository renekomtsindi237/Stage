# 04 — Exigences Fonctionnelles

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Module Collectes d'Épargne

### EF-C01 — Saisie mobile offline-first
Le système doit permettre à un agent de saisir des collectes d'épargne sur son smartphone sans connexion internet. Chaque collecte est identifiée par un UUID v4 généré côté mobile et stockée localement (SQLite) jusqu'à la synchronisation.

**Critères d'acceptation :**
- Une collecte saisie offline est stockée localement avec un statut `PENDING`.
- L'UUID v4 est généré en local et garantit l'unicité sur l'ensemble du système.
- L'application peut stocker au minimum 500 collectes en attente sans perte de données.

### EF-C02 — Synchronisation batch
Le système doit permettre la synchronisation en batch des collectes offline via l'endpoint `POST /api/collectes-epargne/sync`. La réponse indique le nombre de collectes acceptées, rejetées (validation) et les doublons détectés.

**Critères d'acceptation :**
- Les doublons (même UUID) sont détectés et rejetés avec un code explicite.
- La synchronisation est idempotente : re-soumettre le même batch ne crée pas de doublons.
- La réponse retourne pour chaque collecte son statut de traitement.

### EF-C03 — Validation des collectes
Le responsable d'agence doit pouvoir valider ou rejeter les collectes soumises par les agents avant leur intégration dans les KPI officiels.

### EF-C04 — Gestion des cycles de collecte
Le système doit supporter des cycles de collecte configurables (HEBDOMADAIRE, BIHEBDOMADAIRE, MENSUEL) avec des objectifs de montant définis par cycle, agence et agent.

### EF-C05 — KPI collecte journaliers
Le système doit calculer et exposer, pour chaque agent et chaque agence, les KPI journaliers suivants :
- Montant total collecté du jour, de la semaine, du mois.
- Nombre de transactions par canal (ESPECES, MOBILE_MONEY, VIREMENT).
- Taux de réalisation de l'objectif du cycle en cours (%).
- Variation par rapport à la semaine précédente (%).

### EF-C06 — Alertes objectifs non atteints
Le pipeline doit générer automatiquement des alertes opérationnelles lorsqu'un agent ou une agence dépasse le seuil configuré (ex. < 70% de l'objectif à J-3 de la fin de cycle).

---

## 2. Module Recouvrement de Créances

### EF-R01 — Ingestion des données CBS
Le système doit ingérer les exports CBS (fichiers CSV/Excel déposés en zone de transfert) contenant les données de créances, avec validation de format, journal d'ingestion et détection des anomalies.

### EF-R02 — Classification COBAC automatique
Pour chaque créance, le système doit calculer automatiquement :
- Le nombre de jours de retard.
- La classe COBAC (A: courant, B: 30-89j, C: 90-179j, D: 180-359j, E: 360j+).
- Le montant de provision réglementaire (B: 20%, C: 50%, D: 80%, E: 100%).

**Critères d'acceptation :**
- Le calcul est effectué à chaque ingestion CBS.
- Un snapshot journalier est archivé dans `app.kpi_recouvrement_snapshots`.

### EF-R03 — Calcul du PAR
Le système doit calculer le Portfolio at Risk (PAR) aux seuils réglementaires : PAR30, PAR60, PAR90, PAR180, exprimé en montant et en pourcentage de l'encours total.

### EF-R04 — Gestion des dossiers de recouvrement
Pour chaque créance en PAR30+, le système doit créer ou mettre à jour automatiquement un dossier de recouvrement incluant : statut, actions effectuées, promesses de paiement, gestionnaire assigné.

### EF-R05 — Score MCRS par client
Le système doit calculer quotidiennement un score MCRS composite [0,1] pour chaque client avec des créances actives :
- **CRS** (35%) : score de régularité des collectes d'épargne.
- **RPS** (45%) : probabilité de défaut à 90 jours (XGBoost calibré Platt).
- **CSI** (20%) : indice de solvabilité client basé sur les facteurs externes génériques.
- Classification du risque : FAIBLE (<0.30), MODÉRÉ (<0.55), ÉLEVÉ (<0.75), CRITIQUE (≥0.75).

### EF-R06 — Explicabilité SHAP
Pour chaque score MCRS, le système doit stocker les 10 features SHAP les plus contributives, avec leur valeur et leur impact sur le score, exposables via l'API.

### EF-R07 — Alertes prédictives
Le pipeline ML doit générer des alertes prédictives pour les clients dont le score MCRS dépasse les seuils configurés : RISQUE_DEFAUT_IMMINENT, BAISSE_COLLECTE_PERSISTANTE, DETERIORATION_RAPIDE.

---

## 3. Module Données Externes

### EF-E01 — Prix des produits génériques
Le système doit ingérer et stocker les prix de produits génériques (catalogue configurable : maïs, manioc, plantain, arachide, cacao, etc.) par zone géographique et par date, depuis des sources multiples (MINCOMMERCE, relevés terrain, APIs).

**Contrainte :** Les produits sont paramétrables dans `app.produits_generiques` ; aucun produit ne doit être hardcodé dans la logique métier.

### EF-E02 — Données météorologiques
Le système doit ingérer les données météo (précipitations, températures, indice de sécheresse) pour les 10 zones géographiques du Cameroun via Open-Meteo ou MétéoCam.

### EF-E03 — Indicateurs macroéconomiques
Le système doit ingérer les indicateurs macro-économiques BEAC (taux directeur), INS (inflation, IPC) et FMI (chômage, cours EUR/XAF) avec une fréquence mensuelle ou à chaque mise à jour disponible.

### EF-E04 — Événements exterieurs
Le système doit permettre l'enregistrement d'événements perturbateurs (fêtes nationales, périodes de marchés locaux, crises, pénuries) impactant potentiellement les collectes ou les remboursements.

---

## 4. Module Dashboard et Reporting

### EF-D01 — Dashboard DIRECTEUR
Interface présentant en temps réel (mise à jour SSE) :
- KPI collectes : montant jour, taux objectif, clients à risque, alertes ML.
- KPI recouvrement : PAR30, PAR90, taux de recouvrement, provisions totales.
- Tendances prix produits génériques (top 6 produits, variation 30 jours).
- Classement des agences (benchmark z-scores collecte et recouvrement).

### EF-D02 — Dashboard RESPONSABLE_RECOUVREMENT
Interface présentant : liste des dossiers prioritaires (MCRS élevé/critique), PAR de l'agence, actions de recouvrement à planifier, clients avec promesses de paiement en retard.

### EF-D03 — Dashboard AGENT
Interface mobile et web : KPI journalier et hebdomadaire de l'agent, taux de réalisation de l'objectif, liste de ses collectes en attente de synchronisation.

### EF-D04 — Benchmarks inter-agences
Le pipeline doit calculer des scores de benchmark (z-scores) pour chaque agence en matière de collecte et de recouvrement, avec classement et historique de la position dans le temps.

---

## 5. Module Administration

### EF-A01 — Gestion multi-IMF
Le SUPER_ADMIN doit pouvoir créer, configurer et suspendre des IMF clientes, avec isolation complète des données entre IMF.

### EF-A02 — Configuration des paramètres pipeline
Le DSI doit pouvoir configurer via l'interface web : objectifs de collecte par cycle/agence/agent, seuils d'alerte PAR, seuils PSI de retraining, périodicité des cycles de collecte.

### EF-A03 — Journal d'ingestion
Le système doit maintenir un journal d'ingestion (`raw.journal_ingestions`) enregistrant pour chaque exécution de DAG : source, nombre de lignes traitées, nombre d'erreurs, durée, statut.
