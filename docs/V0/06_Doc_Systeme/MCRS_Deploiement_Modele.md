# Déploiement et ré-entraînement du modèle MCRS — MicroRecouv V0

**Auteur :** KOMTSINDI Réné Alban
**Version :** V0 — Juillet 2026
**Structure :** Openxtech

---

## 1. Vue d'ensemble

Le modèle MCRS (Multi-Criteria Recovery Scoring, `pipeline/src/ml/mcrs_model.py`) est servi par
une API FastAPI dédiée (`pipeline/src/ml/api_service.py`, conteneur `imf-ml-api`, port 8090) et
consulté soit directement (interne, non authentifié), soit via la façade externe authentifiée
(`ExternalApiController`, Spring Boot, `/api/v1/external/scores/{clientId}`) — voir
[`api_docs/02_integration_blucash.md`](../../../api_docs/02_integration_blucash.md) pour le
contrat d'intégration côté consommateurs.

Ce document couvre le cycle de vie du modèle lui-même : entraînement, promotion, déploiement,
ré-entraînement — pas son exposition en API.

### 1.1 Convention champion / challenger / archive

```
/ml/models/mcrs/            (volume Docker monté :ro dans imf-ml-api)
├── champion/                ← modèle actif, servi par l'API
├── challenger/               ← candidat entraîné, pas encore promu
└── archive/<horodatage>/     ← anciens champions, conservés pour rollback
```

`api_service.py` charge **uniquement** `champion/` au démarrage (ou via `POST /model/reload`) —
`challenger/` et `archive/` n'ont aucun effet tant qu'ils ne sont pas explicitement promus. Cette
séparation existe pour qu'aucun modèle non validé ne devienne actif par accident.

---

## 2. Deux mécanismes d'entraînement

### 2.1 Mécanisme de production visé : DAG Airflow `dag_ml_training`

Entraînement walk-forward hebdomadaire (dimanche 02h00) sur `ml.features_client`, avec
comparaison champion/challenger et promotion automatique si le challenger est meilleur (cf.
`pipeline/dags/dag_ml_training.py`). **État actuel (2026-07-08) : le DAG s'importe et
s'enregistre correctement** dans le scheduler géré par CI/CD (`imf-airflow-scheduler`, projet
`imf-backend`) — vérifié via `airflow dags list` : `dag_ml_training | pipeline-imf | paused=False`,
de même pour `dag_ml_scoring`, et `airflow dags list-import-errors` ne remonte plus aucune erreur
(0/15 DAGs, contre ~10/15 le matin même). `dag_ml_scoring` a été déclenché réellement le
2026-07-08 — le scoring batch lui-même (le code corrigé en section 4) reste non vérifié de bout en
bout car les toutes premières tâches (`feat_comportemental`/`feat_externe`, préparation dbt)
échouent avant de l'atteindre : `dbt-core` n'est pas installé dans l'image `imf-airflow` — cf.
"Non résolus" en section 4. `dag_ml_training` n'a pas été déclenché (dépend des mêmes features).

### 2.2 Mécanisme manuel formalisé (utilisable dès maintenant)

Deux scripts, à la racine de `pipeline/`, respectant la même discipline champion/challenger que
le DAG :

```bash
# 1. Entraîner un challenger
python pipeline/train_mcrs_champion.py --donnees extrait_reel.csv --source "ml.features_client, extraction 2026-07-08"
# — ou, pour une démonstration uniquement (jamais pour une vraie décision de recouvrement) :
python pipeline/train_mcrs_champion.py --demo

# 2. Comparer au champion actuel et promouvoir si meilleur (opère uniquement en local, sur pipeline/models/)
python pipeline/promouvoir_modele.py
# --forcer pour promouvoir sans comparaison (premier déploiement, aucun champion existant)

# 3. Déployer sur le serveur cible (manuel, jamais automatisé sans revue humaine)
scp pipeline/models/champion/{mcrs_model.pkl,mcrs_meta.json,reference_scores.npy} \
    <serveur>:/ml/models/mcrs/champion/
ssh <serveur> 'docker restart imf-ml-api'   # pas juste /model/reload : ne recharge qu'UN seul des N workers uvicorn
```

`--donnees` attend un CSV avec les colonnes `ALL_FEATURES` (cf. `mcrs_model.py`) +
`label_defaut_90j` + `client_id_externe` + `imf_code`, une ligne par observation datée
(`anciennete_jours` ou une colonne de date explicite). Le script refuse de démarrer avec moins de
2 cas de défaut (validation croisée impossible sinon) — utiliser `--demo` explicitement si c'est
voulu.

### 2.3 Vérification post-déploiement (obligatoire)

```bash
curl -s http://<serveur>:8090/model/health
# {"status":"ok","model_loaded":true,"auc_roc":...,"version":"2.0.0"}

curl -s -X POST http://<serveur>:8090/score/single -H "Content-Type: application/json" -d '{...}'
```

Vérifier que `model_loaded: true` **sur plusieurs appels successifs** (l'API tourne avec
`--workers 2` — un redémarrage de conteneur recharge les deux, contrairement à `/model/reload` qui
n'en recharge qu'un).

---

## 3. Modèle actuellement déployé (staging)

Entraîné via `train_mcrs_champion.py --demo` : les 25 clients réels de FINTECH SARL
(`pipeline/models/fintech/features_fintech.csv`, un seul cas de défaut — insuffisant seul pour
toute validation croisée) complétés par ~175 clients synthétiques générés à partir d'une variable
latente de solvabilité continue bruitée par feature (chevauchement délibéré entre profils sains et
à risque, pour un AUC-ROC walk-forward crédible ≈ 0.85-0.90 plutôt qu'une séparation triviale à
0.99 qui trahirait une fuite d'information plutôt qu'un vrai signal). Explicitement étiqueté
`_provenance`/`_avertissement` dans `mcrs_meta.json`, exposé via `GET /model/info` — **ce n'est pas
un modèle de production**, uniquement adapté à une démonstration.

**Pour passer en production** : constituer un extrait réel de `ml.features_client` avec un nombre
de défauts suffisant (des dizaines au minimum, idéalement des centaines pour une validation
walk-forward significative sur 5 plis), puis `train_mcrs_champion.py --donnees <extrait>.csv`.

---

## 4. Historique des diagnostics (pour qui reprend ce travail)

### Corrigés (2026-07-08)

- **Création de clé API impossible (`POST /support/api-clients` → 500)** : la contrainte SQL
  `utilisateurs_role_check` (ajoutée en V50) ne listait pas `'API_CLIENT'` parmi les rôles
  autorisés, alors qu'`ApiClientService.create()` insère justement un utilisateur système avec ce
  rôle — toute tentative de provisionner une clé pour un intégrateur externe (BluCash, CBS)
  échouait. Corrigé par `V59__fix_role_check_add_api_client.sql` (migration idempotente, déjà
  appliquée en staging).
- **`GET /external/ping` → 500 (`LazyInitializationException` sur `Imf`)** : `systemUser` (injecté
  par `ApiKeyAuthenticationFilter` dans le `SecurityContext`) porte une relation `imf` chargée dans
  la session Hibernate du filtre, déjà refermée au moment où le contrôleur y accède —
  `@Transactional` sur la méthode ne suffit pas (une nouvelle transaction ne réattache pas une
  instance déjà détachée d'une autre session). Corrigé dans `ExternalApiController.ping()` en
  rechargeant l'IMF via `ImfRepository.findById()` plutôt qu'en touchant le proxy détaché — même
  remède que documenté dans `UserRepository` pour un cas similaire au login. Recompilé et redéployé
  en staging (`imf-backend`, jar remplacé directement dans le conteneur).
- **Vérification bout en bout réussie post-correctifs** : clé API réelle provisionnée pour un
  tenant (`FINANCE`, imf id 347), `GET /external/ping` → 200 avec la bonne IMF, `GET
  /external/scores/CLF001` → 200 avec un score réellement déjà calculé (0.8797, FAIBLE, classe A,
  calculé le 2026-06-23 — pas une donnée fabriquée pour l'occasion), `GET /external/scores/at-risk`
  → 200 avec 31 clients réels. Aucune clé / `401` sans authentification confirmés.
- **DAGs Airflow en échec d'import (`ModuleNotFoundError: No module named 'pipeline'`)**, dont
  `dag_ml_training` et `dag_ml_scoring` — ~10 des 15 DAGs sur 15. **Diagnostic initial erroné dans
  une version antérieure de ce document** : ce qui semblait être le problème
  (`imf_staging_airflow_init` en crash-loop, erreur socket Postgres local) était en fait un
  conteneur **orphelin d'un tout autre projet Docker Compose** (`imf-pipeline`, image
  `apache/airflow:2.9.1` nue), sans rapport avec le vrai Airflow géré par CI/CD
  (`imf-airflow-scheduler`/`imf-airflow-webserver`, projet `imf-backend`, image
  `ghcr.io/.../imf-airflow:staging`, en service continu). Le vrai problème : `dags/scripts/*.py`
  fait des imports en paquet (`from pipeline.src... import ...`), mais
  `docker-compose.backend-pipeline.yml` ne montait que `pipeline/dags/` dans le conteneur, jamais
  `pipeline/src/`. Corrigé en ajoutant un second volume (`pipeline/:/opt/airflow/pipeline:ro`) et
  une variable `PYTHONPATH` à double entrée (`/opt/airflow:/opt/airflow/pipeline/src` — la seconde
  entrée est nécessaire car `pipeline/src/database.py` fait lui-même des imports plats
  `from config import settings`). Après correctif : import-errors passés de ~10 DAGs à 1 (bug
  résiduel sans rapport, cf. section suivante), `dag_ml_training` et `dag_ml_scoring` confirmés
  `paused=False` via `airflow dags list`. **Non testé : un déclenchement réel du DAG** (le run
  complet nécessiterait un extrait `ml.features_client` avec un volume de défauts suffisant, cf.
  section 3) — seuls l'import et l'enregistrement dans le scheduler sont vérifiés.

### Corrigés (2026-07-08, suite — même journée, deuxième intervention)

- **`dag_pipeline_init.py` en échec d'import** (`ValueError: 'skipped' is not a valid
  DagRunState`) : `"skipped"` est un état de *tâche* (TaskInstanceState), jamais un état de
  *DagRun* — confirmé en inspectant `TriggerDagRunOperator.__init__` dans le conteneur
  (`DagRunState(s) for s in allowed_states` lève l'exception). Corrigé en retirant `"skipped"` des
  deux `allowed_states=[...]` du DAG. Vérifié : `airflow dags list-import-errors` → 0 erreur sur
  les 15 DAGs (contre 1 avant ce correctif).
- **`ml-api` sans authentification serveur-à-serveur** : ajout d'une dépendance FastAPI vérifiant
  un header `X-Internal-Key` (comparaison en temps constant) sur `/score/single` et `/score/batch`
  uniquement — `/model/health`/`/model/info` restent ouverts (pas de donnée client). Clé partagée
  `MCRS_INTERNAL_API_KEY`, propagée au backend Spring Boot (`MlClientConfig`) et à Blucash (à faire
  côté `Workflow_de_gestion` — cf. `api_docs/02_integration_blucash.md`). Vérifié en staging :
  `POST /score/single` sans clé → 401 ; `GET /model/health` toujours 200. La restructuration réseau
  (isoler `ml-api` du réseau public) a été explicitement écartée avec l'utilisateur — `backend` et
  `ml-api` partagent `network_mode: host` à cause de Redis en localhost, la clé partagée est le
  compromis retenu.
- **`GET /external/scores/{clientId}` ne renvoie pas la version du modèle** : `model_version`
  ajouté au `SELECT` de `getScore()`/`getAtRiskScores()` — la colonne existait déjà depuis V29
  (défaut `'1.0.0'`), seule la lecture manquait côté contrôleur. Vérifié : le champ apparaît
  désormais dans la réponse (`"model_version":"1.0.0"` sur le score CLF001 existant, pas encore
  réécrit par le pipeline batch — cf. point suivant pour que cette valeur devienne réellement
  significative).
- **Deux IMF "FINANCE SARL"** : `imfCode` ajouté à `ApiClientCreatedResponse`/`ApiClientResponse`
  (additif, sans renommage de données — décision explicite avec l'utilisateur).
- **`ml_scoring_utils.py` (le script réel derrière `dag_ml_scoring`) était écrit contre un schéma
  de base entièrement différent de celui migré** : `imf_code`/`date_score` sur `ml.client_scores`
  (colonnes inexistantes — les vraies sont `imf_id`/`scored_at`, upsert par client depuis V29),
  `classe_risque` au lieu de `niveau_risque`, une forme de `ml.shap_explanations` composite au lieu
  du vrai `score_id` FK, une table `app.clients` qui n'a jamais existé (le vrai client informel est
  `app.clients_informels`), des colonnes de score sur `app.creances`
  (`score_mcrs`/`classe_risque_mcrs`/…) qui n'avaient jamais été migrées, et une valeur
  `'DRIFT_DETECTE'` hors de la contrainte CHECK de `ml.alertes_predictives`. Concrètement : **le
  scoring batch n'avait jamais pu réussir contre la vraie base** — le seul score réel existant
  (CLF001, 2026-06-23) vient d'un calcul manuel ponctuel, pas du pipeline. Réécriture complète des
  6 fonctions d'écriture de `ml_scoring_utils.py` pour correspondre au schéma réel, `model_version`
  et `model_run_id` désormais réellement peuplés depuis `ml.model_runs` (plutôt que le défaut figé),
  SHAP inséré au moment de l'insertion du score (`RETURNING id`) plutôt que relu depuis une colonne
  qui n'existait pas. Nouvelle migration `V60__ajout_scores_mcrs_creances.sql` (colonnes
  nullables, additives) pour que le miroir de score sur `app.creances` ait enfin une cible réelle.
  Les alertes de drift (portefeuille/segment, pas par client) ne sont plus écrites en base — le
  schéma `ml.alertes_predictives` est conçu pour des alertes par client (`imf_id`/`client_id_externe`
  NOT NULL), pas pour un événement de portefeuille ; seul le log Airflow fait foi pour l'instant.

### Non résolus

- **`dbt-core` n'est pas installé dans l'image `imf-airflow`** — découvert en déclenchant
  réellement `dag_ml_scoring` : les toutes premières tâches (`feat_comportemental`/`feat_externe`,
  `dbt run` via `pipeline/dags/scripts/dbt_utils.py`) échouent avant même d'atteindre le code
  corrigé ci-dessus (`FileNotFoundError` sur un chemin en plus faux — `DBT_PROJECT_DIR` par défaut
  vaut `/app/pipeline/dbt_project`, un chemin qui n'existe que dans le conteneur `imf-ml-api`, pas
  dans `imf-airflow` où le vrai chemin est `/opt/airflow/pipeline/dbt_project` depuis le montage de
  volume du 2026-07-08 matin — mais même en corrigeant le chemin, `dbt` lui-même est absent :
  `pip show dbt-core` → *not found*). **Conséquence directe pour ce document** : la réécriture de
  `ml_scoring_utils.py` ci-dessus est correcte et déployée, mais reste **non vérifiée de bout en
  bout** — le déclenchement réel du 2026-07-08 s'est terminé en `upstream_failed` en cascade dès la
  première tâche, jamais jusqu'à `scorer_clients`. Il faut ajouter `dbt-core` + l'adaptateur
  Postgres au `Dockerfile` de l'image `imf-airflow` (et corriger `DBT_PROJECT_DIR`/
  `DBT_PROFILES_DIR`), ce qui nécessite un rebuild d'image via le pipeline CI/CD — pas un correctif
  à chaud. Non fait à ce stade : hors du périmètre validé pour cette intervention, à traiter comme
  un chantier séparé.
- **`http://ml-api:8090` ne résout pas depuis `imf-backend`** — `backend` et `ml-api` tournent tous
  deux en `network_mode: host` (pas de réseau Docker bridge, donc pas de DNS `ml-api` interne).
  `MlScoringClient`/`RealtimeScoringService` (scoring temps réel à l'ouverture d'un dossier,
  utilisé côté Spring Boot) échouent donc silencieusement depuis le début — capturés par le mode
  dégradé (`Optional.empty()`), sans jamais remonter d'erreur visible. Correctif probable :
  `ML_API_URL=http://localhost:8090` explicite pour le service `backend`. Découvert en vérifiant
  que la clé `X-Internal-Key` (ci-dessus) atteignait bien `ml-api` depuis le backend — pas corrigé,
  hors périmètre validé pour cette intervention.
