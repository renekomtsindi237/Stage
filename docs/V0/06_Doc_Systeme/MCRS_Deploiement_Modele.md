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
`pipeline/dags/dag_ml_training.py`).

**État au 2026-07-08 (fin de journée) : `dag_ml_scoring` tourne intégralement en succès contre la
vraie base**, vérifié par déclenchement réel (pas juste import) — les 17 tâches passent à
`success` (`declencher_retrain` correctement `skipped`, aucun drift détecté), y compris
`scorer_clients` qui a écrit 85 vrais scores (`ml.client_scores` : `n=85`,
`max(scored_at)` = l'heure du run, contre 60 lignes figées au 2026-06-23 avant ce travail).
Confirmé via l'API externe : `GET /external/scores/CLF001` renvoie désormais un score calculé à
l'instant (`model_version: "v1.2.0"`, lu depuis `ml.model_runs` — plus le défaut figé `"1.0.0"`).
`dag_ml_training` s'importe et s'enregistre correctement mais n'a pas été déclenché (walk-forward
sur `ml.features_client`, dépend en partie de `LABELS_QUERY` dans `feature_engineering.py`, non
auditée — cf. section 4 "Non résolus").

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

### Corrigés (2026-07-08, troisième intervention — mise en service réelle de dag_ml_scoring)

Déclencher réellement le DAG (plutôt que se fier à `airflow dags list`) a révélé une chaîne de
blocages plus profonde que prévu — chacun corrigé et reveérifié par un nouveau déclenchement :

- **`dbt-core` absent de l'image `imf-airflow`** : `Dockerfile.airflow` copiait `requirements.txt`
  (qui liste `dbt-core`/`dbt-postgres`) mais ne l'installait jamais — le `pip install` du Dockerfile
  est une liste manuelle qui n'avait jamais été tenue à jour avec ce fichier. Ajouté à la liste
  explicite (mêmes versions que `requirements.txt`, 1.8.2). Installé à chaud pour vérifier
  (`pip install` dans le conteneur) avant de committer le Dockerfile — aucun conflit de dépendances
  avec l'environnement Airflow 2.8.4 constaté.
- **`pipeline/` monté `:ro`** : `dbt deps`/`dbt run` doivent écrire `package-lock.yml`/
  `dbt_packages/`/`target/` dans le project-dir (aucune option dbt 1.8 ne permet de les reloger
  entièrement ailleurs) — `OSError: Read-only file system` systématique. Retiré `:ro` du montage
  `pipeline/` (scheduler + webserver). Une fois writable, permission refusée pour l'utilisateur
  `airflow` (uid 50000, gid 0) sur des fichiers appartenant à `root:root` sans bit d'écriture
  groupe — `chmod -R g+w` sur `pipeline/dbt_project/` côté hôte.
- **`sources.yml` incomplet** : le source `app` ne déclarait que 2 tables (`donnees_meteo`,
  `facteurs_macro`) sur les 9 réellement référencées par les modèles (`clients_informels`,
  `client_activites_produits`, `produits_generiques`, `agences`, `marches_locaux`,
  `evenements_exterieurs`, `imf`) — `Compilation Error` dès le premier `dbt run`, quel que soit le
  `--select`. Complété.
- **Schéma de sortie dbt doublé** (`staging_ml` au lieu de `ml`, etc.) : `profiles.yml` fixe un
  schéma cible `staging`, et sans macro `generate_schema_name` personnalisée, dbt préfixe
  systématiquement tout `+schema` custom par ce schéma cible — comportement par défaut documented
  par dbt lui-même, jamais neutralisé ici. Ajouté `macros/generate_schema_name.sql` (pattern
  standard dbt) pour que `+schema: ml` produise bien `ml`, pas `staging_ml`.
- **`app.clients` n'a jamais existé** : `feat_client_externe.sql`/`features_client.sql`
  interrogeaient une table `raw.export_cbs` → `stg_clients` → `app.clients` comme pilote — chaîne
  jamais alimentée (aucune ingestion CBS réelle configurée). Recablé sur `app.clients_informels`
  (table réelle, peuplée) + jointure `app.imf` pour `imf_code` (`clients_informels` ne porte que
  `imf_id`). Même remède appliqué à `pipeline/src/ml/feature_engineering.py`
  (`CRS_QUERY`/`RPS_QUERY`/`CSI_QUERY`, utilisées par `scorer_clients_batch` — un chemin de code
  entièrement différent des modèles dbt, avec les mêmes bugs structurels en parallèle).
- **`QUALIFY`** (syntaxe Snowflake/BigQuery, absente de Postgres) dans `features_client.sql` —
  remplacé par une sous-requête + `ROW_NUMBER()`/`WHERE`.
- **`app.agences` n'a pas de latitude/longitude** — `distance_agence_km` ne peut structurellement
  pas être calculée avec le schéma actuel ; mise à `NULL` plutôt que référencer des colonnes
  inexistantes (`distance_marche_km`, via `app.marches_locaux`, fonctionne réellement).
- **`app.donnees_meteo`/`app.facteurs_macro`** interrogées avec des noms de colonnes et un format
  imaginaires (pivot narrow `variable`/`valeur` pour la météo, indicateurs minuscules pour la
  macro) sans rapport avec le schéma réel (déjà wide, indicateurs déjà en majuscules — cf. V21).
  Corrigé dans `stg_meteo.sql`/`stg_indicateurs_macro.sql` et dans `feature_engineering.py`.
  `indice_secheresse` est un VARCHAR enum réel, pas numérique — encodé en ordinal type Palmer DSI
  (0 = normal, négatif = sécheresse croissante) pour matcher ce qu'attend `mcrs_model.py`.
- **`raw.prix_marche` n'existe pas** (aucune ingestion de prix marché configurée, même limite que
  MTN/Orange/CRB) : les features de prix produit restent `NULL`/`0` plutôt que de bloquer tout le
  feature store sur une source absente — `mcrs_model.py` les impute déjà à ses médianes
  sectorielles (`FEATURE_DEFAULTS`), aucune régression de comportement au scoring.
- **`readonly_session()` (`pipeline/src/database.py`) laissait fuiter un état `READ ONLY` au
  niveau de la session Postgres physique** vers le pooler Supabase (port 6543, mode transaction) —
  `conn.close()` ferme le socket client, pas la connexion backend que le pooler recycle telle
  quelle. Une tâche `db_session()` ultérieure pouvait hériter d'une connexion encore en lecture
  seule et échouer sur son premier `UPDATE`/`INSERT` (`maj_priorites_dossiers`/`detecter_drift` en
  `up_for_retry` aléatoire). Corrigé : `readonly_session()` réinitialise explicitement la session en
  lecture-écriture avant de fermer la connexion.
- **Comparaison de dates tz-aware/tz-naive** dans `detecter_drift_psi_segmente` (`scored_at` est
  `TIMESTAMPTZ`, `pd.Timestamp.now()` sans fuseau) — `TypeError` Pandas. Corrigé (`utc=True`
  explicite des deux côtés).
- **`ml_alertes_utils.py`** (même catégorie de bugs que `ml_scoring_utils.py`, jamais corrigée
  jusqu'ici) : `imf_code`/`date_score`/`shap_top_features` inexistants sur `ml.client_scores`,
  colonnes `message`/`date_detection` inexistantes sur `ml.alertes_predictives` (les vraies sont
  `titre`/`description`, `titre` NOT NULL). Corrigé pour `_alertes_risque_critique` (fonctionnelle
  avec les vraies données — 20 alertes générées au premier run réel). `_alertes_deterioration_rapide`
  rendue explicitement non-fonctionnelle avec un log clair plutôt que silencieuse : `ml.client_scores`
  ne conserve plus d'historique par client depuis l'upsert V29, aucune comparaison à "il y a 7 jours"
  n'est possible sans table dédiée. `_alertes_baisse_collecte`/`_alertes_cobac_aggravee` déjà
  protégées par `try/except` (dégradation existante conservée) — dépendent de `raw.export_cbs`/d'un
  historique quotidien de classe COBAC, tous deux absents du schéma réel.
- **Sélecteurs dbt de `dag_ml_scoring.py` vers des modèles fantômes** : `feat_comportemental`/
  `feat_externe` sélectionnaient 7 noms de modèles granulaires
  (`feat_collecte_regularite`, `feat_prix_produit_principal`, ...) qui n'ont jamais existé dans le
  projet dbt réel — la logique correspondante vit dans 2 modèles seulement
  (`int_profil_recouvrement_client`, `feat_client_externe`). Corrigé.

**Vérification finale** : trois déclenchements réels complets de `dag_ml_scoring` le 2026-07-08,
le dernier avec les 17 tâches à `success` (`declencher_retrain` `skipped`, comportement correct
sans drift) — `scorer_clients` a écrit 85 scores réels (`ml.client_scores`), `generer_alertes_ml` a
produit 20 alertes réelles, `GET /external/scores/CLF001` confirme un score et un `model_version`
frais.

### Non résolus

- **`LABELS_QUERY` (`pipeline/src/ml/feature_engineering.py`), utilisée par `dag_ml_training`
  uniquement** : mêmes symptômes probables que les requêtes corrigées ci-dessus (`staging.stg_clients`
  comme pilote, `app.kpi_recouvrement_snapshots.client_id` qui n'existe pas — cette table est un
  agrégat de portefeuille, pas par client). Non auditée ni corrigée — `dag_ml_training` n'a pas été
  déclenché aujourd'hui, hors périmètre de cette vérification centrée sur le scoring.
- **`_alertes_baisse_collecte`/`_alertes_cobac_aggravee`** (`ml_alertes_utils.py`) restent
  non-fonctionnelles (dégradation déjà en place, cf. ci-dessus) : dépendent de `raw.export_cbs`
  (aucune ingestion CBS réelle) et d'un historique quotidien de classification COBAC qui n'existe
  nulle part dans le schéma actuel.
- **Ingestion `raw.*` jamais implémentée** (`export_cbs`, `prix_marche`, `transactions_mtn`,
  `transactions_orange`) : construit pour une architecture `raw → staging` qui n'a jamais été
  câblée — les données réelles arrivent directement dans `app.*` via l'API Spring Boot. Les
  fonctionnalités qui en dépendent structurellement (prix marché, alertes CBS, historique de
  classification) resteront indisponibles tant qu'aucune ingestion réelle (CBS SFTP, MTN/Orange
  Mobile Money, scraping/API de prix) n'est branchée — limite déjà connue, pas nouvelle.
- **`ml-api` sans authentification serveur-à-serveur, exposé sur l'IP publique** — corrigé (cf.
  section précédente, header `X-Internal-Key`). Restructuration réseau (isoler `ml-api` du réseau
  public) explicitement écartée avec l'utilisateur — `backend` et `ml-api` partagent
  `network_mode: host` à cause de Redis en localhost.
- **`http://ml-api:8090` ne résout pas depuis `imf-backend`** — `backend` et `ml-api` tournent tous
  deux en `network_mode: host` (pas de réseau Docker bridge, donc pas de DNS `ml-api` interne).
  `MlScoringClient`/`RealtimeScoringService` (scoring temps réel à l'ouverture d'un dossier,
  utilisé côté Spring Boot) échouent donc silencieusement depuis le début — capturés par le mode
  dégradé (`Optional.empty()`), sans jamais remonter d'erreur visible. Correctif probable :
  `ML_API_URL=http://localhost:8090` explicite pour le service `backend`. Non corrigé — hors
  périmètre validé pour cette intervention.
