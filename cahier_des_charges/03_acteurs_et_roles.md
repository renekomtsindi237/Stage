# 03 — Acteurs et Rôles

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Acteurs humains

### 1.1 AGENT (Agent de collecte terrain)

**Profil :** Agent de terrain rattaché à une agence, responsable de la collecte physique des épargnes et du suivi de base des remboursements de ses clients.

**Responsabilités :**
- Saisir les collectes d'épargne via l'application mobile Flutter (offline-first).
- Consulter ses objectifs de collecte par cycle (semaine, mois) et son taux de réalisation.
- Visualiser la liste de ses clients en retard de paiement.
- Recevoir des alertes push (objectif non atteint, client à risque élevé détecté par MCRS).

**Contraintes :** Opère souvent en zone à faible connectivité ; doit pouvoir enregistrer des collectes sans connexion internet avec synchronisation différée.

---

### 1.2 RESPONSABLE_RECOUVREMENT

**Profil :** Gestionnaire de portefeuille créances au niveau de l'agence ou du siège, en charge du suivi des impayés et de l'animation de l'équipe de recouvrement.

**Responsabilités :**
- Consulter le tableau de bord de recouvrement : PAR30/60/90, dossiers ouverts, promesses de paiement.
- Prioriser les dossiers de recouvrement selon le score MCRS et la classification COBAC.
- Enregistrer et suivre les promesses de paiement.
- Déclencher les actions de recouvrement (relance amiable, mise en demeure, contentieux).
- Valider les collectes d'épargne soumises par les agents.
- Recevoir les alertes PAR dépassant les seuils réglementaires.

---

### 1.3 DIRECTEUR

**Profil :** Directeur d'agence ou directeur général de l'IMF. Décideur stratégique disposant d'une vision transversale sur l'ensemble des activités.

**Responsabilités :**
- Consulter le dashboard de pilotage global : KPI collectes + PAR COBAC + scores MCRS + benchmarks inter-agences.
- Comparer les performances de son agence (ou de l'ensemble des agences) sur une période donnée.
- Suivre les tendances des prix des produits génériques des marchés locaux (facteurs de risque external CSI).
- Prendre des décisions stratégiques (ajustement objectifs de collecte, politique de provisionnement, déclenchement audit).
- Recevoir les alertes opérationnelles critiques.

---

### 1.4 ANALYSTE

**Profil :** Data analyst ou risk analyst rattaché au siège, spécialisé dans l'analyse des données et la production de rapports réglementaires.

**Responsabilités :**
- Explorer les données du Data Warehouse via des vues analytiques.
- Produire les rapports COBAC périodiques (PAR, provisions, taux de couverture).
- Analyser les explications SHAP du modèle MCRS pour identifier les facteurs de risque dominants.
- Configurer les seuils d'alerte opérationnelle.
- Analyser les tendances des benchmarks inter-agences.

---

### 1.5 DSI (Directeur des Systèmes d'Information)

**Profil :** Responsable technique de l'IMF, administrateur de la plateforme.

**Responsabilités :**
- Configurer les paramètres système (périodicité des DAGs, seuils PSI, seuils d'alerte).
- Superviser les logs d'ingestion et d'exécution du pipeline.
- Gérer les utilisateurs et leurs droits d'accès.
- Surveiller la santé du pipeline (Airflow, dbt, PostgreSQL).

---

### 1.6 SUPER_ADMIN

**Profil :** Administrateur de la plateforme SaaS multi-tenant.

**Responsabilités :**
- Gérer les IMF clientes (création, suspension, configuration).
- Administrer les ressources communes (catalogue de produits génériques, zones géographiques).
- Superviser les exécutions de pipeline cross-tenant.
- Gérer les incidents et la maintenance de la plateforme.

---

## 2. Acteurs systèmes

### 2.1 Application Mobile Flutter

Acteur système représentant les appareils mobiles des agents. Génère des collectes avec UUID v4 en mode offline, les stocke localement (SQLite) et les synchronise en batch avec l'API lors du retour en zone connectée.

### 2.2 Pipeline Airflow

Acteur système qui orchestre l'ensemble des traitements de données automatiques : ingestion, transformation dbt, calcul des KPI, scoring MCRS, détection de dérive, génération d'alertes.

### 2.3 Système Core Banking (CBS)

Acteur système externe représentant le logiciel de gestion des prêts de l'IMF. Produit des exports périodiques (fichiers) contenant les données de créances (encours, statuts, retards de paiement). Non contrôlé par ce projet.

### 2.4 Sources de données externes

- **Open-Meteo / MétéoCam** : données météorologiques par zone géographique (précipitations, sécheresse).
- **MINCOMMERCE / relevés terrain** : prix des produits génériques sur les marchés locaux camerounais.
- **BEAC / INS / FMI** : indicateurs macroéconomiques (taux directeur, inflation, cours EUR/XAF).

---

## 3. Matrice des droits d'accès

| Fonctionnalité | AGENT | RESP_REC | DIRECTEUR | ANALYSTE | DSI | SUPER_ADMIN |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Saisie collecte mobile | ✓ | — | — | — | — | — |
| Voir ses propres KPI | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Valider collectes | — | ✓ | — | — | — | — |
| Dashboard recouvrement | — | ✓ | ✓ | ✓ | — | — |
| Dashboard directeur | — | — | ✓ | ✓ | — | — |
| Score MCRS client | — | ✓ | ✓ | ✓ | — | — |
| Gestion dossiers recouvrement | — | ✓ | — | — | — | — |
| Rapports COBAC | — | — | ✓ | ✓ | — | — |
| Configuration pipeline | — | — | — | — | ✓ | ✓ |
| Gestion IMF | — | — | — | — | — | ✓ |

---

## 4. Isolation multi-tenant

Chaque utilisateur est rattaché à une IMF (`imf_id`) enregistrée dans le JWT. Toutes les requêtes sont automatiquement filtrées par `imf_id` via le `TenantContext` Spring Security. Un DIRECTEUR ou ANALYSTE d'une IMF ne peut jamais accéder aux données d'une autre IMF, sauf pour les benchmarks agrégés et anonymisés inter-agences produits par le pipeline.
