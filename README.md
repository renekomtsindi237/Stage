# MicroRecouv — V0

> Plateforme de centralisation, d'analyse et d'aide au recouvrement pour les institutions de microfinance au Cameroun.

## Le projet en une phrase

MicroRecouv transforme les collectes d'épargne terrain, les créances du système bancaire et les données de contexte en informations fiables permettant aux équipes de microfinance de **suivre leur activité, respecter leurs obligations de reporting et prioriser leurs actions de recouvrement**.

**Auteur :** KOMTSINDI Réné Alban — Étudiant ingénieur ISI<br>
**Structure :** Openxtech — **Année :** 2025–2026<br>
**Nature :** Projet de fin d'études — **Version :** V0

## 1. Contexte et problème

Les institutions de microfinance (IMF) collectent l'épargne et accordent des crédits à des clients dont les activités sont souvent informelles, rurales et dépendantes des conditions économiques locales. Elles doivent donc disposer d'une information rapidement disponible pour suivre les agents, détecter les impayés et agir avant que les créances ne se dégradent.

Dans la pratique, cette information est souvent dispersée :

- les agents saisissent les collectes sur papier ou dans une application mobile peu fiable ;
- les opérations sont ressaisies dans le système central, ce qui crée des erreurs et des doublons ;
- les zones sans réseau empêchent une remontée immédiate des données ;
- les créances sont importées du CBS sous forme de fichiers, puis retraitées manuellement dans Excel ;
- les indicateurs PAR, les provisions et la classification COBAC sont produits tardivement ;
- les responsables choisissent les dossiers à relancer sans score objectif ni historique consolidé ;
- les facteurs externes comme la météo ou le prix des produits peuvent fragiliser un client sans être pris en compte.

Le problème à résoudre est donc le suivant : **comment donner aux IMF une vision centralisée, actualisée, traçable et exploitable de leurs collectes et de leurs créances, malgré des sources hétérogènes et une connectivité parfois limitée ?**

## 2. But et objectifs

### But général

Concevoir et mettre en œuvre une solution end-to-end qui centralise les données de collecte et de recouvrement afin d'améliorer le pilotage opérationnel, le suivi réglementaire COBAC et la priorisation des actions auprès des clients.

### Objectifs spécifiques

1. Permettre à un agent de saisir une collecte sans connexion Internet.
2. Synchroniser les opérations dès que le réseau revient, sans perte ni doublon.
3. Intégrer les exports du CBS et les données externes utiles à l'analyse.
4. Automatiser le calcul des PAR30, PAR60, PAR90 et PAR180, des classes COBAC et des provisions.
5. Conserver des snapshots historiques pour analyser l'évolution des KPI.
6. Classer les dossiers de recouvrement selon un score de risque explicable.
7. Alerter les responsables en cas d'anomalie, de promesse non tenue ou de risque élevé.
8. Donner à chaque rôle une vue adaptée à ses responsabilités.

## 3. Réponse proposée

MicroRecouv relie six éléments :

| Élément | Rôle dans la solution |
|---|---|
| Application mobile Flutter | Saisie offline des collectes par l'agent terrain et synchronisation par lots |
| API Spring Boot | Authentification, contrôle, déduplication, gestion métier et accès aux données |
| Pipeline Airflow/dbt | Ingestion, nettoyage, transformation, historisation et orchestration des traitements |
| PostgreSQL | Stockage opérationnel, zone brute, entrepôt décisionnel et données ML |
| Application web Angular | Dashboards, suivi des KPI, alertes et gestion des dossiers |
| Application bureau Tauri | Même interface Angular, empaquetée pour Windows, API `https://imf.rene.it.com` |

Le fonctionnement est le suivant :

```text
1. L'agent saisit une collecte, même hors connexion.
2. L'application la conserve localement avec un UUID unique.
3. Le retour du réseau déclenche une synchronisation avec l'API.
4. L'API valide la collecte et élimine les doublons.
5. Airflow et dbt consolident les collectes, les exports CBS et les données externes.
6. Le système calcule les KPI, la classification COBAC, les alertes et les scores.
7. Les agents, responsables et directeurs consultent les résultats dans le web, le bureau ou le mobile.
```

## 4. Le score de risque MCRS

Le **MCRS** (*Multi-Criteria Recovery Scoring*) aide le responsable à traiter en priorité les dossiers présentant le plus de risque. Il combine :

```text
MCRS = 0.35 x CRS + 0.45 x RPS + 0.20 x CSI
```

- **CRS** (*Collection Reliability Score*) : régularité et fiabilité des collectes ;
- **RPS** (*Recovery Prediction Score*) : probabilité de défaut à 90 jours, estimée par XGBoost ;
- **CSI** (*Client Solvency Index*) : influence du contexte économique, des prix, de la météo et des facteurs macroéconomiques.

Le résultat n'est pas une simple note : le système fournit des explications SHAP pour indiquer les principaux facteurs du risque. La dérive du modèle est suivie avec le PSI afin de déclencher un réentraînement lorsque les données changent fortement.

## 5. Utilisateurs et résultats attendus

| Utilisateur | Résultat attendu |
|---|---|
| Agent de collecte | Saisir hors ligne et suivre ses objectifs |
| Responsable d'agence | Contrôler les collectes, les anomalies et la performance de l'équipe |
| Responsable recouvrement | Disposer d'une liste de dossiers priorisés et suivre les promesses de paiement |
| Directeur | Voir les KPI consolidés et comparer les agences |
| Analyste | Comprendre les tendances et les facteurs qui expliquent le risque |
| DSI / administrateur | Gérer les utilisateurs, les paramètres et l'isolation des IMF |

À terme, la solution doit réduire la ressaisie, accélérer la disponibilité des indicateurs, améliorer la traçabilité et permettre une action de recouvrement plus précoce et mieux justifiée.

## 6. Périmètre de la V0

### Fonctionnalités incluses

- saisie mobile offline et synchronisation batch idempotente ;
- import des créances depuis des exports CBS ;
- calcul des PAR et de la classification COBAC A à E ;
- suivi des collectes, cycles, objectifs et validations ;
- gestion des dossiers et des promesses de paiement ;
- données de prix, météo et facteurs macroéconomiques ;
- score MCRS, explications SHAP, alertes et détection de dérive ;
- dashboards pour les agents, responsables, directeurs et analystes ;
- client bureau Windows (installeur NSIS Tauri) branché sur `https://imf.rene.it.com` ;
- sécurité JWT/RBAC et isolation multi-tenant par `imf_id`.

### Fonctionnalités exclues

La V0 ne gère pas l'instruction ou le déblocage des crédits, la comptabilité générale, la trésorerie, la paie ni un CRM complet.

## 7. Architecture et flux de données

```text
Application mobile Flutter (offline) ─┐
                                     ├─ REST/JWT ─> API Spring Boot ─> PostgreSQL
Application web Angular ─────────────┤                  │                  │
Application bureau Tauri ────────────┘                  │                  │
                                                       │                  ├─ app.* : métier
                                                       │                  ├─ raw/staging/dw.* : données
                                                       │                  └─ ml.* : features et scores
                                                       └─ SSE <─ Redis

Exports CBS + prix + météo + macroéconomie
                    └─> Airflow ─> dbt ─> KPI, alertes et MCRS
```

Les données traversent les niveaux suivants :

```text
raw.* → staging.* → intermediate.* → dw.*
                                  └→ ml.*
```

| Composant | Technologie | Port local |
|---|---|---:|
| API REST | Spring Boot 3.2, Java 17 | 8080 |
| Application web | Angular 17, Angular Material | 4200 |
| Application bureau | Tauri 2 (WebView + Angular) | — |
| Application mobile | Flutter / Dart | — |
| Pipeline | Airflow 2.8.4, Python 3.11, dbt 1.8.6 | 8089 |
| Service de scoring | FastAPI | 8090 |
| Données et cache | PostgreSQL 15, Redis 7 | 5432 / 6379 |
| Interface base de données | Adminer | 8888 |

## 8. Démarrage local

### Prérequis

- Docker et Docker Compose ;
- GNU Make ;
- Java 17 et Maven pour le développement du backend ;
- Node.js et npm pour le développement du frontend ;
- Flutter pour exécuter l'application mobile ;
- Rust (rustup) et les outils de build C++ Windows pour le client bureau Tauri.

### Démarrer tous les services avec Docker

Depuis la racine du projet :

```bash
make build ENV=dev
make up ENV=dev
make ps
```

Le Makefile prépare `.env` à partir de `.env.dev` si le fichier n'existe pas.

| Interface | Adresse |
|---|---|
| Application web | http://localhost:4200 |
| API / Swagger | http://localhost:8080/swagger-ui.html |
| Airflow | http://localhost:8089 |
| Service ML | http://localhost:8090 |
| Adminer | http://localhost:8888 |

### Développer séparément le backend et le frontend

```bash
make infra
make backend-local
cd frontend && npm ci && npm start
```

Les deux dernières commandes sont à lancer dans des terminaux séparés.

### Client bureau (Tauri)

Le frontend Angular est empaqueté dans une fenêtre native. L'API utilisée est `https://imf.rene.it.com`.

```bash
cd desktop && npm ci
npm run dev      # développement (ng serve + fenêtre Tauri)
npm run build    # produit MicroRecouv_1.0.0_x64-setup.exe
```

L'installeur se trouve dans `desktop/dist/MicroRecouv_1.0.0_x64-setup.exe`. Double-cliquer l'installe comme Word ou Excel : menu Démarrer, raccourci Bureau (proposé à la fin), désinstallation depuis Paramètres Windows. L'application se connecte à `https://imf.rene.it.com`. L'icône (Setup, `.exe`, Bureau) est le logo `MicroRecouv.png` ; la régénérer avec `cd desktop && npm run icons` puis reconstruire. Guide : [docs/desktop.md](docs/desktop.md).

Le backend doit autoriser les origines Tauri (`http://tauri.localhost`, `https://tauri.localhost`). C'est déjà prévu dans `app.cors.allowed-origins` : après modification, redéployer l'API.

Pour arrêter les services :

```bash
make down ENV=dev
```

## 9. Commandes utiles

```bash
make logs ENV=dev       # afficher les logs Docker
make test-backend       # tester le backend avec Maven
make test-web           # tester Angular
make pipeline-test      # tester le pipeline Python
make pipeline-lint      # lancer ruff et mypy
make dbt-run ENV=dev    # exécuter les modèles dbt
make migrate            # appliquer les migrations Flyway
make desktop-dev        # lancer le client bureau Tauri
make desktop-build      # construire l'installeur Windows
```

## 10. Organisation du dépôt

```text
backend/             API Spring Boot, sécurité et migrations Flyway
frontend/            Application web Angular
desktop/             Client bureau Tauri (encapsule le frontend Angular)
mobile/              Application Flutter offline-first
pipeline/            DAGs Airflow, ETL Python, dbt et modèles ML
schemas/             Schémas Avro et modèles générés
analyse/             Analyse de l'existant, benchmark et besoins métier
cahier_des_charges/ Objectifs, périmètre et exigences
conception/          Architecture, données, API et sécurité
docs/                Documentation V0, UML et rapport
deploy/              Déploiement et monitoring
docker-compose.*.yml Environnements Docker
Makefile             Orchestration des services, tests et traitements
```

## 11. Documentation complémentaire

Ce README est autonome. Les documents suivants approfondissent certains sujets :

- [Analyse de l'existant](analyse/01_analyse_de_lexistant.md) ;
- [Besoins métier](analyse/03_besoins_metier.md) ;
- [Cas d'utilisation](analyse/04_cas_utilisation.md) ;
- [Architecture globale](conception/01_architecture_globale.md) ;
- [Conception de l'API](conception/04_conception_api.md) ;
- [Client bureau Tauri](docs/desktop.md) ;
- [Index de la documentation](docs/README.md).

*Projet de fin d'études — Institut Universitaire Saint Jean, Yaoundé — 2026*
