# RAPPORT DE STAGE DE FIN D'ÉTUDES

## Conception et Implémentation d'un Système Intégré de Pipeline de Données, d'API REST et d'Applications Multiplateforme pour le Suivi du Recouvrement de Créances dans les Institutions de Microfinance au Cameroun

---

| Champ | Valeur |
|---|---|
| **Document** | Rapport de Stage — **Version V0** |
| **Date de version** | 2026-04-05 |
| **Statut** | Brouillon — En cours de rédaction |
| **Établissement** | Institut Universitaire Saint Jean — Yaoundé |
| **Filière** | Ingénierie des Systèmes d'Information (ISI) — Niveau 4 |
| **Option** | Génie Logiciel - Data Engineering & Intelligence Artificielle (aspiration) |
| **Auteur** | KOMTSINDI Réné Alban |
| **Maître de stage** | [Encadrant professionnel] |
| **Encadrant académique** | [Enseignant responsable] |
| **Période de stage** | Mai — Août 2026 |
| **Structure d'accueil** | Openxtech |
| **Année académique** | 2025 — 2026 |

### Historique des versions

| Version | Date | Auteur | Description |
|---|---|---|---|
| V0 | 2026-04-05 | KOMTSINDI Réné Alban | Version initiale — structure complète, sections en cours de finalisation |

---

## DÉDICACE

*À ma famille, pour leur soutien indéfectible tout au long de ce parcours.*

*À tous les Camerounais qui, faute d'accès aux outils financiers adaptés, restent en marge du développement économique. Ce travail est une modeste contribution à cette grande cause.*

---

## REMERCIEMENTS

Je tiens à exprimer ma profonde gratitude à toutes les personnes qui ont contribué à la réalisation de ce travail.

À mon encadrant professionnel, pour la confiance accordée, la disponibilité constante et les précieux conseils qui ont guidé chaque étape de ce projet. Sa vision pratique du métier a considérablement enrichi ma compréhension des enjeux réels du secteur.

À mon encadrant académique, pour la rigueur méthodologique transmise et pour avoir su orienter mes réflexions vers une approche à la fois scientifique et pragmatique.

À l'ensemble de l'équipe technique de la structure d'accueil, pour l'intégration chaleureuse et les échanges enrichissants qui m'ont permis de comprendre les réalités opérationnelles d'une institution de microfinance camerounaise.

À mes camarades de promotion, pour les discussions, les débats techniques et la solidarité qui ont rendu ces quatre années d'études à la fois riches et mémorables.

---

## RÉSUMÉ

Le secteur de la microfinance au Cameroun joue un rôle déterminant dans l'inclusion financière des populations rurales et péri-urbaines, souvent exclues des circuits bancaires classiques. Cependant, les institutions qui le composent font face à un défi opérationnel majeur : le recouvrement des créances repose encore largement sur des processus manuels, fragmentés et peu traçables, entraînant des taux d'impayés élevés et une capacité d'analyse limitée.

Ce rapport présente la conception et l'implémentation de **MicroRecouv**, un système intégré à quatre couches visant à moderniser la gestion du recouvrement dans une institution de microfinance (IMF) camerounaise. Le projet articule : (1) un **pipeline de données** automatisé basé sur Apache Airflow et dbt pour l'extraction, la transformation et le chargement des données de prêts et de collectes ; (2) une **API REST** sécurisée développée avec Spring Boot exposant les indicateurs clés de performance (KPI) et les alertes d'impayés ; (3) une **application web** Angular offrant aux gestionnaires un tableau de bord analytique temps réel ; et (4) une **application mobile** Flutter permettant aux agents de terrain de consulter leurs portefeuilles et de recevoir des alertes instantanées.

Sur le plan technique, le projet mobilise un ensemble cohérent de technologies modernes — PostgreSQL (schémas `app`, `staging`, `dw`), Java 21, TypeScript, Dart — organisées en monorepo avec une infrastructure Docker conteneurisée, déclinée en trois environnements (dev, staging, prod). La sécurité est assurée par JWT avec rotation de tokens, le contrôle d'accès par rôles (RBAC) et le chiffrement SSL.

**Mots-clés :** microfinance, recouvrement, pipeline de données, ETL, dbt, Apache Airflow, Spring Boot, Angular, Flutter, Docker, PostgreSQL, inclusion financière, Cameroun.

---

## ABSTRACT

The microfinance sector in Cameroon plays a central role in the financial inclusion of rural and peri-urban populations often excluded from conventional banking systems. However, institutions in this sector face a major operational challenge: debt recovery still relies largely on manual, fragmented, and poorly traceable processes, leading to high non-performing loan rates and limited analytical capacity.

This report presents the design and implementation of **MicroRecouv**, an integrated four-layer system aimed at modernising debt recovery management in a Cameroonian microfinance institution (MFI). The project comprises: (1) an automated **data pipeline** based on Apache Airflow and dbt for extracting, transforming and loading loan and collection data; (2) a secure **REST API** developed with Spring Boot exposing key performance indicators (KPIs) and non-payment alerts; (3) an **Angular web application** providing managers with a real-time analytical dashboard; and (4) a **Flutter mobile application** enabling field agents to consult their portfolios and receive instant alerts.

Technically, the project leverages a coherent set of modern technologies — PostgreSQL (schemas `app`, `staging`, `dw`), Java 21, TypeScript, Dart — organised in a monorepo with containerised Docker infrastructure, available in three environments (dev, staging, prod). Security is ensured by JWT with token rotation, role-based access control (RBAC) and SSL encryption.

**Keywords:** microfinance, debt recovery, data pipeline, ETL, dbt, Apache Airflow, Spring Boot, Angular, Flutter, Docker, PostgreSQL, financial inclusion, Cameroon.

---

## TABLE DES MATIÈRES

1. [Introduction générale](#1-introduction-générale)
2. [Contexte et problématique](#2-contexte-et-problématique)
3. [État de l'art](#3-état-de-lart)
4. [Analyse et spécification des besoins](#4-analyse-et-spécification-des-besoins)
5. [Conception du système](#5-conception-du-système)
6. [Réalisation et implémentation](#6-réalisation-et-implémentation)
7. [Tests et validation](#7-tests-et-validation)
8. [Déploiement et infrastructure](#8-déploiement-et-infrastructure)
9. [Bilan et perspectives](#9-bilan-et-perspectives)
10. [Conclusion générale](#10-conclusion-générale)
11. [Bibliographie](#11-bibliographie)
12. [Annexes](#12-annexes)

---

## 1. INTRODUCTION GÉNÉRALE

### 1.1 Contexte du stage

Ce rapport présente le travail réalisé dans le cadre d'un stage de fin d'études effectué au sein d'une institution de microfinance camerounaise. À l'issue de quatre années de formation en Ingénierie des Systèmes d'Information à l'Institut Universitaire Saint Jean de Yaoundé, ce stage représente l'aboutissement pratique d'un cursus alliant fondements théoriques de l'informatique, conception de systèmes d'information et, plus particulièrement, ingénierie des données.

La microfinance constitue, à mes yeux, l'un des domaines où la technologie peut avoir l'impact social le plus concret et le plus immédiat dans le contexte africain. Avoir eu l'opportunité d'y contribuer à travers un projet technique de cette envergure est une expérience à la fois formatrice et profondément motivante.

### 1.2 Motivation personnelle

Mon intérêt pour le *data engineering* — la discipline qui consiste à concevoir et opérer les infrastructures permettant de collecter, transformer et valoriser des données à grande échelle — s'est affirmé progressivement au fil de ma formation. Les cours de bases de données, de systèmes distribués et de modélisation m'ont fourni les bases ; les lectures personnelles sur Apache Kafka, dbt, Apache Spark et les architectures *data lakehouse* ont alimenté une curiosité croissante.

Ce projet m'a permis de mettre en pratique, pour la première fois dans un contexte réel, une partie de ces concepts : conception d'un entrepôt de données multi-couches, orchestration de pipelines ETL, modélisation dimensionnelle avec dbt. C'est précisément ce type de problème — transformer des données brutes et disparates en informations décisionnelles — qui définit le quotidien d'un data engineer, et que je me propose de poursuivre comme spécialisation professionnelle.

### 1.3 Objectifs du rapport

Ce document poursuit trois objectifs :

1. **Documenter** la démarche de conception et d'implémentation de MicroRecouv de manière rigoureuse et reproductible.
2. **Analyser** les choix techniques effectués et les compromis acceptés, en les inscrivant dans le contexte spécifique d'une IMF camerounaise.
3. **Partager** les apprentissages — techniques mais aussi humains — que ce stage a permis d'acquérir.

---

## 2. CONTEXTE ET PROBLÉMATIQUE

### 2.1 Le secteur de la microfinance au Cameroun

La microfinance camerounaise est régie par le **Règlement COBAC EMF/2002/01** et ses révisions subséquentes, sous la supervision de la Commission Bancaire de l'Afrique Centrale (COBAC). On distingue trois catégories d'établissements de microfinance (EMF) : les EMF de catégorie 1 (collecte d'épargne et octroi de crédit aux membres uniquement), de catégorie 2 (opérations avec le public) et de catégorie 3 (crédit sans collecte d'épargne).

Selon les données de la COBAC, le secteur camerounais comptait en 2023 plus de **400 EMF agréés**, pour un encours de crédit dépassant **500 milliards de FCFA**. Ces chiffres, bien qu'en progression, masquent une réalité opérationnelle souvent difficile : des taux de portefeuille à risque (PAR) élevés, une gestion des impayés réactive plutôt que préventive, et des outils informatiques souvent inadaptés.

### 2.2 Le mobile money comme catalyseur de transformation

L'un des faits marquants de la dernière décennie au Cameroun est l'essor fulgurant du **mobile money**. **MTN Mobile Money** et **Orange Money** totalisent ensemble plusieurs dizaines de millions de transactions mensuelles. Pour les IMF, cela représente une opportunité majeure : les clients peuvent désormais effectuer leurs remboursements à distance, sans se déplacer en agence.

Cependant, cette transformation crée aussi une complexité nouvelle : les données de paiement sont fragmentées entre plusieurs canaux (espèces, MTN MoMo, Orange Money, virement), et leur réconciliation avec les créances enregistrées dans les systèmes de gestion n'est pas automatisée dans la plupart des structures.

### 2.3 Problématique identifiée

À l'issue de la phase d'analyse menée en début de stage, les problèmes suivants ont été identifiés :

**P1 — Fragmentation des données de collecte.** Les données de remboursement proviennent de sources hétérogènes (caissiers, agents terrain, opérateurs mobile money) et sont centralisées manuellement dans des tableaux Excel, avec des risques élevés d'erreurs et de doublons.

**P2 — Absence d'indicateurs de risque en temps réel.** Le calcul du PAR30 et du PAR90 — indicateurs standards de la COBAC mesurant respectivement les encours en retard de plus de 30 et 90 jours — est effectué mensuellement, ce qui empêche toute réaction préventive.

**P3 — Inefficacité du recouvrement terrain.** Les agents de terrain n'ont pas accès à leurs portefeuilles d'impayés en mobilité. La liste des clients à relancer est transmise par téléphone ou sur papier, avec toute l'imprévisibilité que cela implique.

**P4 — Absence de traçabilité des actions.** Aucun système ne permet d'enregistrer les tentatives de recouvrement, les promesses de paiement ou les motifs de défaillance déclarés par les clients.

### 2.4 Objectifs du projet MicroRecouv

Face à ces constats, l'institution a formulé le besoin d'un système capable de :

- **Centraliser et automatiser** la collecte de données multi-sources ;
- **Calculer en continu** les indicateurs de risque réglementaires (PAR30, PAR90) ;
- **Alerter automatiquement** les responsables dès qu'un seuil critique est franchi ;
- **Équiper les agents terrain** d'une application mobile leur permettant de consulter leurs portefeuilles en temps réel ;
- **Fournir aux décideurs** un tableau de bord analytique pour le pilotage stratégique.

---

## 3. ÉTAT DE L'ART

### 3.1 Architectures de pipelines de données modernes

La gestion de flux de données hétérogènes fait l'objet de nombreuses approches dans la littérature et l'industrie. On distingue principalement :

**L'architecture Lambda** (Nathan Marz, 2011) décompose le traitement en deux couches : une *batch layer* pour les calculs historiques précis, et une *speed layer* pour le traitement en temps quasi-réel. Si cette architecture est robuste, elle souffre d'une complexité de maintenance due à la dualité des traitements.

**L'architecture Kappa** (Jay Kreps, LinkedIn, 2014) simplifie ce modèle en unifiant les deux couches sous un unique stream processing. Elle convient aux cas où les données peuvent être régénérées par rejeu des événements.

**Le paradigme ELT vs ETL.** La tendance actuelle dans le data engineering favorise l'**ELT** (Extract-Load-Transform) sur l'ETL traditionnel. Les outils comme **dbt** (data build tool) ont popularisé cette approche : on charge d'abord les données brutes dans un entrepôt, puis on les transforme *in situ* en SQL. Cela rend les transformations plus transparentes, versionnables et testables.

Pour ce projet, compte tenu de la volumétrie modérée des données et de la priorité donnée à la simplicité de maintenance, une **architecture ELT simplifiée** a été retenue : extraction Python → staging PostgreSQL → transformation dbt → data warehouse PostgreSQL.

### 3.2 Outils d'orchestration

**Apache Airflow** (Airbnb, 2014) est devenu le standard de facto pour l'orchestration de pipelines de données. Son modèle de DAG (*Directed Acyclic Graph*) offre une expressivité suffisante pour modéliser des dépendances complexes entre tâches, avec une interface de monitoring intuitive.

Des alternatives comme **Prefect** et **Dagster** proposent des modèles plus modernes (orientés flux plutôt que DAG statiques), mais leur courbe d'adoption est plus élevée. Pour un projet en contexte étudiant-professionnel, Airflow représente le meilleur compromis entre popularité, documentation et fonctionnalités.

### 3.3 Frameworks backend et frontend

**Spring Boot** (Pivotal, 2014) s'est imposé comme le standard du développement backend Java en entreprise. Son écosystème mature — Spring Security, Spring Data JPA, Flyway — permet de construire des API REST sécurisées avec un niveau d'abstraction élevé, réduisant le code *boilerplate*.

**Angular** (Google, 2016) offre une structure opinionée adaptée aux applications d'entreprise. Son système de modules, services injectables et composants, combiné à TypeScript, garantit une base de code maintenable sur le long terme.

**Flutter** (Google, 2017) est le framework qui a le plus retenu mon attention dans ce projet. Sa promesse de codebase unique pour iOS et Android, avec des performances proches du natif grâce au moteur Skia/Impeller, le rend particulièrement pertinent dans le contexte africain où les utilisateurs sont sur une grande diversité d'appareils Android.

### 3.4 Solutions concurrentes dans la microfinance

Il existe des solutions spécialisées pour la microfinance, dont **Mambu**, **Temenos Microfinance** ou **Musoni**. Ces plateformes sont cependant onéreuses (modèle SaaS en devises) et peu adaptables au contexte camerounais spécifique (mobile money local, règlementation COBAC). C'est cette inadéquation qui justifie le développement d'une solution sur mesure.

---

## 4. ANALYSE ET SPÉCIFICATION DES BESOINS

### 4.1 Acteurs du système

| Acteur | Description | Accès |
|---|---|---|
| **Agent de terrain** | Collecte les remboursements, effectue les relances | Application mobile |
| **Responsable de recouvrement** | Supervise les agents, valide les alertes | Web + Mobile |
| **Analyste** | Produit les rapports, analyse les tendances | Web |
| **Directeur** | Vue d'ensemble, indicateurs stratégiques | Web |
| **DSI** | Gestion des utilisateurs, configuration | Web (admin) |
| **Pipeline (système)** | Calcul automatique des KPI, génération des alertes | API interne |

### 4.2 Besoins fonctionnels principaux

**BF01 — Authentification et gestion des rôles**
Le système doit permettre l'authentification des utilisateurs par identifiant/mot de passe avec émission d'un token JWT. Les droits d'accès sont déterminés par le rôle de l'utilisateur (AGENT, RESPONSABLE_RECOUVREMENT, ANALYSTE, DIRECTEUR, DSI).

**BF02 — Consultation du portefeuille de prêts**
Chaque utilisateur doit pouvoir consulter la liste des prêts, filtrés par statut (ACTIF, EN_RETARD, SOLDE) avec pagination. Le détail d'un prêt inclut l'échéancier complet.

**BF03 — Gestion des alertes d'impayés**
Le système génère automatiquement des alertes lorsqu'un prêt dépasse 30 ou 90 jours de retard. Les alertes sont consultables, filtrables par statut et peuvent être mises à jour (TRAITEE, ESCALADEE, CLOTUREE).

**BF04 — Tableau de bord KPI**
Les indicateurs suivants doivent être calculés et affichés en temps réel : Total des collectes (période configurable), Nombre de collectes, Encours PAR30, Encours PAR90, Nombre d'alertes actives.

**BF05 — Reporting et exports**
Le système doit permettre l'export des données de recouvrement en format CSV et PDF, par agent, par zone ou pour l'ensemble du portefeuille.

**BF06 — Synchronisation pipeline**
Le pipeline ETL doit s'exécuter selon un calendrier configurable (quotidien) et mettre à jour les données de l'entrepôt. Un endpoint sécurisé permet au pipeline de soumettre des données de collecte et de déclencher le recalcul des alertes.

### 4.3 Besoins non fonctionnels

**BNF01 — Performance.** Les endpoints API doivent répondre en moins de 500ms pour 95% des requêtes sous charge normale (< 100 utilisateurs simultanés).

**BNF02 — Disponibilité.** Le système doit viser une disponibilité de 99,5% en heures ouvrables (7h-20h, heure de Yaoundé, UTC+1).

**BNF03 — Sécurité.** Toutes les communications doivent être chiffrées (TLS 1.2+). Les mots de passe sont hashés avec BCrypt (coût ≥ 12). Les tokens JWT ont une durée de vie de 24h avec rotation via refresh token.

**BNF04 — Conformité COBAC.** Les indicateurs PAR30 et PAR90 sont calculés selon les définitions du Règlement COBAC EMF/2002/01. Le journal d'audit conserve l'historique de toutes les modifications sensibles.

**BNF05 — Accessibilité mobile.** L'application Flutter doit fonctionner correctement sur des appareils Android avec 2 Go de RAM minimum et des connexions 3G intermittentes (gestion des états de chargement, retry automatique).

---

## 5. CONCEPTION DU SYSTÈME

### 5.1 Architecture globale

Le système MicroRecouv adopte une **architecture en couches découplées**, organisée en quatre composants principaux communicant via des interfaces bien définies :

```
┌─────────────────────────────────────────────────────────────────┐
│                        COUCHE PRÉSENTATION                      │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐ │
│  │     Application Web      │  │    Application Mobile        │ │
│  │  Angular 17 + Material   │  │   Flutter 3.19 + Dart 3.3   │ │
│  │  Chart.js + ngx-translate│  │   Provider + GoRouter        │ │
│  └──────────┬───────────────┘  └──────────────┬───────────────┘ │
└─────────────┼───────────────────────────────────┼───────────────┘
              │ HTTP/REST (JWT)                    │ HTTP/REST (JWT)
              ▼                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                         COUCHE API                              │
│         Spring Boot 3.2 + Spring Security 6 + Flyway           │
│    JWT Auth · RBAC · SSE · Multilingue (FR/EN) · Swagger       │
│    17 Controllers · 15 Services · 7 Entités JPA               │
└─────────────────────────┬───────────────────────────────────────┘
                          │ JDBC/Hikari Connection Pool
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                     COUCHE DONNÉES                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ Schéma `app`   │  │ Schéma `staging│  │  Schéma `dw`     │  │
│  │ (opérationnel) │  │ (ETL landing)  │  │ (analytique)     │  │
│  │ prêts, clients │  │ données brutes │  │ agrégats, faits  │  │
│  │ alertes, users │  │ non validées   │  │ dimensions       │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
│                   PostgreSQL 15 (Supabase)                      │
└─────────────────────────▲───────────────────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────────────────┐
│                      COUCHE PIPELINE                            │
│  ┌──────────────────────┐  ┌────────────────┐  ┌─────────────┐  │
│  │   Apache Airflow 2.x │  │   dbt Core 1.7 │  │  Python 3.11│  │
│  │   Orchestration DAGs │  │   Transformations│  │  Extracteurs│  │
│  └──────────────────────┘  └────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Modèle de données

Le schéma de données est organisé selon le principe des trois zones d'une architecture Data Warehouse moderne :

**Zone `staging`** — Zone d'atterrissage brute. Les données y sont chargées telles qu'elles arrivent des sources, sans transformation. Table principale : `staging.collectes_raw` avec colonnes source, montant, date, statut de validation.

**Zone `app`** — Zone opérationnelle. Contient les données validées et enrichies utilisées par l'API REST. Tables principales :

```sql
-- Prêts (vue consolidée des créances)
app.prets_consolides (id_pret, reference, id_client, montant_initial,
                      montant_restant, taux_interet, statut, date_debut,
                      date_fin, nombre_echeances, echeances_payees)

-- Alertes d'impayés (générées automatiquement par le pipeline)
app.alertes_impayees (id, id_pret, type_alerte, jours_retard,
                      montant_en_retard, statut, date_generation, date_cloture)

-- Collectes terrain (remboursements effectués)
app.collectes_terrain (id, id_pret, montant, canal_paiement,
                        agent_id, date_collecte, statut_validation)

-- Échéances applicatives
app.echeances_app (id, id_pret, numero, date_echeance, montant_du,
                   montant_paye, statut, date_paiement)
```

**Zone `dw`** — Zone analytique. Modèle dimensionnel pour les agrégats KPI :

```sql
-- Table de faits collectes (grain : 1 collecte)
dw.fact_collectes (id, date_key, agent_key, client_key,
                   canal_key, montant, statut)

-- Dimension temps
dw.dim_date (date_key, jour, semaine, mois, trimestre, annee, est_jour_ouvre)

-- Agrégats PAR (mis à jour quotidiennement par dbt)
dw.agg_par_daily (date, agence_id, par30_montant, par30_nb_prets,
                  par90_montant, par90_nb_prets)
```

### 5.3 Architecture de sécurité

La sécurité est implémentée sur plusieurs niveaux complémentaires :

**Niveau transport** : TLS 1.2+ obligatoire sur tous les endpoints publics. Les certificats sont gérés via Let's Encrypt (environnements de production).

**Niveau authentification** : JWT avec algorithme HS256. Access token (durée : 24h) + Refresh token (durée : 7 jours, stocké en base pour invalidation possible). BCrypt avec un facteur de coût de 12 pour le hashage des mots de passe.

**Niveau autorisation** : RBAC avec 5 rôles hiérarchiques. Chaque endpoint est annoté avec les rôles autorisés via `@PreAuthorize`. La matrice de droits est documentée dans `SecurityConfig.java`.

**Niveau données** : Séparation des schémas PostgreSQL avec des utilisateurs DB distincts par couche. Le compte API n'a accès qu'au schéma `app`. Le pipeline utilise un compte avec accès en écriture sur `staging` et lecture sur `app`.

### 5.4 Pipeline de données — Architecture détaillée

```
Sources de données
      │
      ├── Fichiers CSV (exports système core banking)
      ├── API REST Spring Boot (collectes validées)
      └── Tables staging existantes
             │
             ▼
  ┌──────────────────────┐
  │  Airflow DAG         │  Planifié : quotidien 02h00 (Yaoundé)
  │  extract_load_dag    │
  └──────────┬───────────┘
             │
             ▼
  Python Extractors ──▶ staging.collectes_raw (INSERT brut)
                 │
                 ▼
  Python Loaders ──▶ Validation + enrichissement
                 │
                 ▼
  ┌──────────────────────┐
  │  Airflow DAG         │
  │  transform_dbt_dag   │
  └──────────┬───────────┘
             │
             ▼
  dbt models :
    staging/  ──▶ Nettoyage, déduplication
    marts/    ──▶ Agrégats PAR, KPI mensuels
    reports/  ──▶ Vues reporting COBAC
             │
             ▼
  POST /internal/alertes ──▶ API Spring Boot
  (déclenche recalcul alertes et mise à jour statuts)
```

---

## 6. RÉALISATION ET IMPLÉMENTATION

### 6.1 Organisation du monorepo

Le projet est organisé en monorepo à la racine `k:/Stage/`, avec un dossier par couche :

```
Stage/
├── backend/          # Spring Boot (Java 21, Maven)
├── web/              # Angular 17 (TypeScript)
├── mobile/           # Flutter 3.19 (Dart 3.3)
├── pipeline/         # Python 3.11 + Airflow + dbt
│   ├── dags/         # DAGs Airflow
│   ├── src/          # Code Python ETL
│   └── dbt_project/  # Modèles dbt
├── docker/           # Configurations Docker spécifiques
├── docs/             # Documentation (ce document)
├── docker-compose.dev.yml
├── docker-compose.staging.yml
├── docker-compose.prod.yml
├── Makefile          # Orchestration des commandes
└── .gitignore
```

Ce choix de monorepo facilite la cohérence des versions entre couches et simplifie la gestion du CI/CD, au prix d'un dépôt Git plus volumineux.

### 6.2 Backend Spring Boot — points techniques notables

**Gestion des migrations Flyway**

Les migrations de base de données sont versionnées avec Flyway, garantissant la reproductibilité des environnements. Un soin particulier a été apporté à la numérotation pour éviter les conflits :

| Version | Description |
|---|---|
| V1 | Initialisation des schémas app/staging/dw |
| V2 | Seed utilisateur administrateur (BCrypt) |
| V3 | Table sync_logs pour l'audit pipeline |
| V4 | Journal d'audit des échéances |

**Architecture des services**

Chaque domaine métier suit le pattern Interface + Implémentation, favorisant la testabilité via injection de dépendances. Exemple pour le service d'alertes :

```java
// Interface — contrat fonctionnel
public interface IAlertService {
    Page<AlerteResponse> getAlertes(String statut, Pageable pageable);
    AlerteResponse updateStatut(Long id, AlerteUpdateRequest request);
    void genererAlertes(); // appelé par le pipeline
}

// Implémentation — logique métier
@Service
@RequiredArgsConstructor
public class AlerteServiceImpl implements IAlertService {
    private final AlerteImpayeeRepository alerteRepository;
    private final ApplicationEventPublisher eventPublisher; // SSE
    // ...
}
```

**SSE (Server-Sent Events) pour les alertes temps réel**

Le endpoint `/api/sse/stream` utilise un `SseEmitter` Spring pour pousser les notifications en temps réel vers les clients connectés. Ce mécanisme est léger côté serveur (pas de WebSocket nécessaire) et bien supporté par les navigateurs modernes et les applications Flutter via `http` package.

**Internationalisation (i18n)**

L'API supporte le français et l'anglais via l'en-tête `Accept-Language`. Les messages d'erreur, labels et notifications sont externalisés dans des fichiers `messages_fr.properties` et `messages_en.properties`, permettant une adaptation ultérieure aux langues locales camerounaises (Ewondo, Bassa, Fulfulde).

### 6.3 Application Web Angular — choix de conception

**Architecture modulaire lazy-loaded**

L'application est structurée en modules fonctionnels chargés à la demande (*lazy loading*), réduisant le bundle initial et améliorant les performances au démarrage. C'est particulièrement important dans le contexte camerounais où la connexion peut être limitée.

**Système de thème**

Un système de tokens CSS (`--brand-navy`, `--brand-gold`, `--brand-teal`) permet le basculement instantané entre mode clair et sombre sans rechargement de page, via l'attribut `data-theme` sur l'élément `<html>`.

**Page d'accueil**

Une page d'accueil animée présente l'application avant l'authentification, avec un système de particules Canvas et un carrousel de fonctionnalités. Ce choix de design vise à donner une impression de modernité et de sérieux à l'outil.

### 6.4 Application Mobile Flutter — points d'attention

**Gestion de la connectivité intermittente**

Dans le contexte africain, les réseaux 3G peuvent être instables. L'application gère cela via un `ConnectivityService` qui surveille l'état de la connexion et affiche des indicateurs visuels appropriés. Les appels API utilisent des timeouts raisonnables (30s) avec retry automatique via les intercepteurs Dio.

**Sécurité du stockage local**

Les tokens JWT sont stockés dans `flutter_secure_storage`, qui utilise le Keychain iOS et les EncryptedSharedPreferences Android. Contrairement au `SharedPreferences` simple, ces données sont chiffrées au niveau du système d'exploitation.

**Navigation déclarative**

GoRouter offre une navigation déclarative avec protection des routes. Si un utilisateur non authentifié tente d'accéder au dashboard, il est automatiquement redirigé vers la page de login, et le callback de redirection post-login est géré proprement.

---

## 7. TESTS ET VALIDATION

### 7.1 Stratégie de tests

La stratégie de tests adoptée pour ce projet s'appuie sur la pyramide de tests :

- **Tests unitaires** (base) : Tests des services avec Mockito pour isoler les dépendances. 20+ classes de tests Spring Boot couvrent les controllers, services et filtres.
- **Tests d'intégration** (milieu) : Tests avec `@SpringBootTest` et base de données H2 in-memory pour valider les interactions JPA-PostgreSQL.
- **Tests end-to-end** (sommet) : Validation manuelle des flux complets via Swagger UI et l'interface Angular.

### 7.2 Tests backend — exemples

```java
// Exemple : test du controller d'alertes
@WebMvcTest(AlerteController.class)
class AlerteControllerTest {

    @MockBean private IAlertService alerteService;

    @Test
    @WithMockUser(roles = "RESPONSABLE_RECOUVREMENT")
    void getAlertes_shouldReturn200WithPagedResults() throws Exception {
        // Given
        var page = new PageImpl<>(List.of(AlerteResponse.builder()
            .id(1L).statut("ACTIVE").joursRetard(45).build()));
        when(alerteService.getAlertes(any(), any())).thenReturn(page);

        // When + Then
        mockMvc.perform(get("/api/alertes?statut=ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].statut").value("ACTIVE"))
            .andExpect(jsonPath("$.content[0].joursRetard").value(45));
    }
}
```

### 7.3 Résultats des tests

| Catégorie | Nombre de tests | Taux de succès |
|---|---|---|
| Controllers REST | 16 classes, ~80 tests | 100% |
| Services | 4 classes, ~25 tests | 100% |
| Filtres & Handlers | 3 classes, ~15 tests | 100% |
| Build Angular | Compilation TypeScript | ✅ Sans erreur |
| Build Flutter | Analyse Dart | ✅ Sans avertissement critique |

---

## 8. DÉPLOIEMENT ET INFRASTRUCTURE

### 8.1 Stratégie de conteneurisation

L'ensemble du système est conteneurisé avec Docker, avec trois environnements distincts :

**Développement (`docker-compose.dev.yml`)** : Services locaux avec hot-reload, logs verbeux, Adminer pour l'accès à la base de données.

**Staging (`docker-compose.staging.yml`)** : Configuration proche de la production, avec des données anonymisées, pour les tests d'intégration et de validation.

**Production (`docker-compose.prod.yml`)** : Séparation en profils (`pipeline`, `app`) permettant de déployer les couches sur des serveurs distincts si nécessaire. Backend avec 2 réplicas pour la haute disponibilité.

### 8.2 Base de données — Supabase

Pour la persistence des données en production, **Supabase** est retenu comme fournisseur de PostgreSQL managé. Ce choix est motivé par :

- **Haute disponibilité** : Standby automatique avec failover < 30 secondes
- **Performance** : Connection pooling Supavisor, 8 Go de RAM dédiée sur le plan Pro
- **Sécurité** : SSL obligatoire, Row Level Security PostgreSQL, backups automatiques quotidiens
- **Coût** : ~25$/mois (plan Pro), adapté à une structure de microfinance de taille moyenne
- **Compatibilité** : PostgreSQL 15 natif, 100% compatible avec Flyway et Spring Data JPA

La configuration Spring Boot se réduit à un changement d'URL JDBC :

```properties
spring.datasource.url=jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres?sslmode=require
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=30000
```

### 8.3 Orchestration avec le Makefile

Un `Makefile` centralisé orchestre toutes les opérations courantes :

```bash
make up ENV=dev           # Démarrer l'environnement dev
make up ENV=staging       # Environnement staging
make up-app ENV=prod      # Profil app en production
make up-pipeline ENV=prod # Profil pipeline en production
make test-backend         # Lancer les tests Spring Boot
make build-web            # Construire l'image Docker web
```

---

## 9. BILAN ET PERSPECTIVES

### 9.1 Objectifs atteints

Au terme de ce stage, les objectifs initiaux ont été atteints dans leur grande majorité :

✅ Pipeline de données fonctionnel avec Airflow, Python et dbt
✅ API REST complète avec 17 controllers, sécurité JWT, RBAC, i18n
✅ Application web Angular avec tableau de bord, alertes, prêts, clients, reporting, admin
✅ Application mobile Flutter avec toutes les fonctionnalités principales
✅ Infrastructure Docker conteneurisée pour trois environnements
✅ Documentation technique complète

### 9.2 Difficultés rencontrées et solutions

**Difficulté 1 — Complexité de la modélisation multi-schémas PostgreSQL.**
La gestion de trois schémas distincts (`app`, `staging`, `dw`) avec des utilisateurs PostgreSQL aux droits différenciés a nécessité une compréhension approfondie de la gestion des permissions PostgreSQL. La solution a été de centraliser ces permissions dans les scripts Flyway et de documenter la matrice de droits.

**Difficulté 2 — Gestion de la connectivité intermittente pour le mobile.**
Le contexte camerounais impose de concevoir les applications mobiles pour des réseaux instables. L'implémentation d'un `ConnectivityService` avec états visuels et retry automatique a résolu ce problème de manière élégante.

**Difficulté 3 — Internationalisation cohérente sur quatre couches.**
Maintenir la cohérence des traductions (FR/EN) entre le backend (properties), l'Angular (JSON i18n), le Flutter (ARB) et la base de données a nécessité une discipline organisationnelle stricte.

### 9.3 Perspectives d'évolution

**Court terme (6 mois)**

- Intégration d'un SDK mobile money (MTN MoMo API, Orange Money API) pour la réconciliation automatique des paiements mobiles
- Ajout d'un module de **géolocalisation** des collectes terrain (coordonnées GPS à la soumission)
- Authentification **biométrique** sur l'application mobile (empreinte digitale) pour les agents terrain

**Moyen terme (1-2 ans)**

- Intégration d'un modèle de **scoring de crédit** alimenté par l'entrepôt de données : prédiction de la probabilité de défaut basée sur l'historique comportemental du client (régularité des paiements, utilisation du mobile money, etc.)
- Extension de la **couche analytique** vers un vrai lakehouse avec Apache Spark pour le traitement de volumes plus importants
- API ouverte pour l'intégration avec d'autres IMF de la zone CEMAC

**Long terme — Impact social visé**

À l'échelle de la vision, MicroRecouv pourrait évoluer vers une plateforme SaaS mutualisée accessible aux IMF de petite et moyenne taille, leur offrant des capacités analytiques avancées sans investissement informatique lourd. Dans un secteur où l'accès aux outils technologiques est souvent corrélé à la taille de la structure, une telle démocratisation des outils data pourrait contribuer significativement à l'amélioration du taux d'inclusion financière au Cameroun — actuellement estimé à 35% selon la Banque Mondiale.

### 9.4 Apport personnel du stage

Ce stage a représenté, pour moi, une transition décisive entre la théorie universitaire et la pratique professionnelle. Plusieurs dimensions ont particulièrement enrichi ma formation :

**Dimension technique :** La conception et l'implémentation complète d'un système multi-couches m'a confronté à des problèmes réels que les exercices académiques ne peuvent simuler — migrations de base de données en production, gestion des sessions JWT avec rotation, optimisation des requêtes sur des volumes significatifs. Le travail avec dbt a notamment confirmé mon intérêt pour le data engineering : transformer des données brutes en informations actionnables, de manière fiable et reproductible, est un exercice intellectuellement stimulant.

**Dimension métier :** Comprendre les contraintes spécifiques d'une IMF camerounaise — la réglementation COBAC, le rôle du mobile money, les réalités terrain des agents de recouvrement — a enrichi ma vision de ce que signifie concevoir un système d'information *pour* des utilisateurs réels, dans un contexte social défini.

**Dimension humaine :** Travailler dans une équipe professionnelle m'a appris la valeur de la communication claire, de la documentation et du respect des engagements. La technique seule ne suffit pas : un système bien conçu mais mal documenté ou mal présenté a peu de chances d'être adopté.

---

## 10. CONCLUSION GÉNÉRALE

Ce projet de fin d'études m'a permis de concevoir et d'implémenter de A à Z un système d'information complet au service d'une problématique sociale réelle : améliorer l'efficacité du recouvrement de créances dans une institution de microfinance camerounaise, et par là contribuer, modestement, à la pérennité d'un acteur de l'inclusion financière.

Sur le plan technique, MicroRecouv illustre comment des technologies modernes et open source — Apache Airflow, dbt, Spring Boot, Angular, Flutter, PostgreSQL, Docker — peuvent être combinées de manière cohérente pour répondre à des besoins métier complexes, avec un niveau de qualité comparable aux solutions propriétaires du marché.

Sur le plan personnel, ce stage a cristallisé ma vocation pour le *data engineering* : la construction d'infrastructures de données robustes, scalables et fiables, au service de l'intelligence décisionnelle. Le Cameroun, comme le reste de l'Afrique subsaharienne, génère des volumes de données croissants dont la valorisation reste largement inexploitée. C'est dans cet espace — entre la rigueur technique du génie logiciel et la vision stratégique de la data science — que je me propose de construire ma carrière.

*"Les données sont le pétrole du 21e siècle, mais comme le pétrole brut, elles n'ont de valeur que raffinées."*
— Clive Humby

---

## 11. BIBLIOGRAPHIE

### Ouvrages et articles académiques

[1] KLEPPMANN, M. *Designing Data-Intensive Applications*. O'Reilly Media, 2017. ISBN 978-1-4493-7332-0.

[2] MARZ, N., WARREN, J. *Big Data: Principles and Best Practices of Scalable Realtime Data Systems*. Manning Publications, 2015.

[3] FOWLER, M. *Patterns of Enterprise Application Architecture*. Addison-Wesley, 2002.

[4] KIMBALL, R., ROSS, M. *The Data Warehouse Toolkit: The Definitive Guide to Dimensional Modeling* (3e éd.). Wiley, 2013.

[5] COMMISSION BANCAIRE DE L'AFRIQUE CENTRALE. *Règlement COBAC EMF/2002/01 relatif aux conditions d'exercice et de contrôle de l'activité de microfinance dans la CEMAC*. COBAC, 2002.

### Documentation technique

[6] APACHE SOFTWARE FOUNDATION. *Apache Airflow Documentation* (v2.8). https://airflow.apache.org/docs/

[7] DBT LABS. *dbt Core Documentation* (v1.7). https://docs.getdbt.com/

[8] SPRING. *Spring Boot Reference Documentation* (v3.2). https://docs.spring.io/spring-boot/docs/

[9] ANGULAR TEAM. *Angular Documentation* (v17). https://angular.io/docs

[10] FLUTTER TEAM. *Flutter Documentation* (v3.19). https://docs.flutter.dev/

[11] SUPABASE. *Supabase Documentation*. https://supabase.com/docs

### Rapports et études

[12] BANQUE MONDIALE. *Financial Inclusion Overview — Cameroon*. World Bank Group, 2023.

[13] GSMA. *State of the Industry Report on Mobile Money 2023*. GSMA Mobile for Development, 2024.

[14] COBAC. *Rapport annuel sur la situation des Établissements de Microfinance en zone CEMAC 2022*. BEAC/COBAC, 2023.

---

## 12. ANNEXES

### Annexe A — Matrice des droits d'accès

| Endpoint | AGENT | RR | ANALYSTE | DIRECTEUR | DSI |
|---|:---:|:---:|:---:|:---:|:---:|
| GET /api/dashboard/summary | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/prets | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/alertes | ❌ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/alertes/:id/statut | ❌ | ✅ | ❌ | ✅ | ✅ |
| GET /api/reporting | ❌ | ✅ | ✅ | ✅ | ✅ |
| GET /api/admin/users | ❌ | ❌ | ❌ | ❌ | ✅ |
| POST /api/admin/users | ❌ | ❌ | ❌ | ❌ | ✅ |
| POST /internal/alertes | Pipeline uniquement (clé API) |

*RR = Responsable Recouvrement*

### Annexe B — Variables d'environnement

Voir fichier `.env.example` à la racine du projet pour la liste complète des variables d'environnement par couche.

### Annexe C — Guide de démarrage rapide

```bash
# 1. Cloner le projet
git clone <repo-url> && cd Stage

# 2. Créer le fichier d'environnement
cp .env.dev .env    # ou .env.staging / .env.prod

# 3. Démarrer l'environnement de développement
make up ENV=dev

# 4. Vérifier les services (attendre ~60s)
make ps

# 5. Accéder aux interfaces
# API Swagger  : http://localhost:8080/swagger-ui.html
# App Web      : http://localhost:4200
# Airflow      : http://localhost:8090
# Adminer DB   : http://localhost:8080

# 6. Identifiants par défaut
# API Admin    : admin / Admin2026!
# Airflow      : airflow / airflow
```

---

*Document rédigé dans le cadre d'un stage de fin d'études en Ingénierie des Systèmes d'Information.*
*Institut Universitaire Saint Jean — Yaoundé, Cameroun — 2026*
