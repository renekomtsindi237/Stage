# 03 — Conception du Pipeline de Données

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Architecture générale du pipeline

Le pipeline suit le paradigme **ELT** (Extract → Load → Transform) avec une couche d'orchestration Airflow et des transformations gérées par dbt Core :

```
[Sources] ─► raw.* ─► staging.* ─► intermediate.* ─► dw.* / ml.*
              (E)       (L+T)           (T)              (T)
```

### 1.1 Principes de conception

- **Idempotence** : chaque tâche peut être re-exécutée sans effets de bord.
- **Traçabilité** : `raw.journal_ingestions` enregistre chaque exécution (statut, lignes, durée).
- **Séparation des domaines** : chaque DAG couvre un domaine fonctionnel distinct.
- **Fail-fast** : un échec d'ingestion bloque les transformations aval ; les alertes sont déclenchées.

---

## 2. DAG 1 — Collectes d'Épargne (`dag_collecte_epargne`)

**Schedule :** `0 */2 * * *` (toutes les 2 heures)
**Objectif :** Intégrer les collectes synchronisées par les agents mobiles et mettre à jour les KPI.

### Tâches (dans l'ordre)
1. **`sync_collectes_app`** : lit les collectes avec statut `SYNCHRONISEE` dans `app.collectes_epargne` (insérées par le backend lors des appels sync) et les charge dans `raw.collectes_terrain`.
2. **`valider_format`** : vérifie les formats UUID, montants (> 0), dates, canaux valides ; rejette et log les invalides.
3. **`enrichir_gps`** : résout la zone géographique depuis lat/lon si absente.
4. **`dbt_staging`** : exécute `dbt run --select staging.stg_collectes_epargne` — nettoyage, typage, déduplication UUID, flags qualité.
5. **`dbt_intermediate`** : exécute `dbt run --select intermediate.int_collectes_par_agent` — agrégats hebdomadaires par agent.
6. **`calculer_kpis`** : calcule les KPI agence (montant jour, taux objectif) et insère dans `app.kpi_collecte_snapshots`.
7. **`verifier_objectifs`** : compare réalisé vs objectif par agent/cycle ; identifie les agents en deçà du seuil configuré.
8. **`generer_alertes_ops`** : insère des alertes dans `app.alertes_operationnelles` (type `OBJECTIF_NON_ATTEINT`, `AGENT_INACTIF`).
9. **`notif_agents`** : envoie des notifications push FCM aux agents concernés.
10. **`notif_responsables_sse`** : publie un événement SSE `kpi_collecte_updated` sur Redis Pub/Sub.
11. **`log_journal`** : écrit le bilan d'exécution dans `raw.journal_ingestions`.

---

## 3. DAG 2 — Recouvrement de Créances (`dag_recouvrement`)

**Schedule :** `0 6 * * *` (quotidien 06h00)
**Objectif :** Ingérer les exports CBS, calculer les PAR COBAC, mettre à jour les dossiers.

### Tâches
1. **`ingerer_cbs`** : détecte et charge les fichiers CBS déposés dans la zone de transfert → `raw.export_cbs` (avec hash fichier pour déduplication).
2. **`valider_cbs`** : contrôles de cohérence (montants positifs, dates valides, imf_code connu) ; rejette et alerte en cas d'erreur > 5%.
3. **`dbt_stg_creances`** : `dbt run --select staging.stg_creances` — calcul des jours de retard, classification COBAC A-E, provisions.
4. **`calculer_par`** : agrège PAR30/60/90/180 par agence ; valide les seuils réglementaires COBAC (alerte si PAR90 > 5%).
5. **`sync_creances_app`** : met à jour `app.creances` (classe COBAC, provision, jours_retard) depuis staging.
6. **`creer_dossiers`** : crée ou rouvre les dossiers de recouvrement pour toutes les créances en PAR30+.
7. **`prioriser_dossiers`** : trie les dossiers par score MCRS (dernière valeur disponible) + ancienneté COBAC.
8. **`traiter_promesses`** : met à jour le statut des promesses de paiement (RESPECTEE/ROMPUE) selon les paiements CBS.
9. **`calculer_kpis_recouvrement`** : insère snapshots dans `app.kpi_recouvrement_snapshots`.
10. **`calculer_benchmarks`** : calcule les z-scores inter-agences → `app.benchmarks_agences`.
11. **`generer_alertes_par`** : alerte si PAR90 de l'agence > seuil configuré → `app.alertes_operationnelles`.
12. **`notif_email`** : email récapitulatif aux responsables de recouvrement.
13. **`notif_sse`** : publie `recouvrement_updated` sur Redis.
14. **`log_journal`**.

---

## 4. DAG 3 — Données Externes (`dag_donnees_externes`)

**Schedule :** `0 4 * * *` (quotidien 04h00)
**Objectif :** Ingérer les prix produits génériques, météo et indicateurs macro.

### Tâches (parallèles en groupes)

**Groupe 1 — Prix produits (parallèle) :**
- **`prix_terrain`** : charge les relevés de prix saisis manuellement.
- **`prix_mincommerce`** : fetch l'API MINCOMMERCE (bulletin prix agricoles).

**Groupe 2 — Macro et météo (parallèle) :**
- **`indicateurs_beac`** : fetch le bulletin BEAC (taux directeur, réserves).
- **`indicateurs_ins`** : fetch les données INS (IPC, inflation par région).
- **`donnees_meteo`** : fetch Open-Meteo pour les 10 zones du Cameroun (coordonnées GPS prédéfinies).
- **`evenements`** : vérifie le calendrier des événements configurés (fêtes, marchés).

**Transformation :**
- **`dbt_stg_prix`** : `dbt run --select staging.stg_prix_produits` — moyennes mobiles 7j/30j, variation.
- **`dbt_stg_macro`** : staging macro et météo.
- **`dbt_int_externe`** : `dbt run --select intermediate.int_profil_recouvrement_client` — enrichit les profils avec les features externes.

**Mise à jour applicative :**
- **`maj_app_prix`** : insère les nouveaux prix dans `app.prix_produits`.
- **`maj_app_macro`** : met à jour `app.facteurs_macro` et `app.donnees_meteo`.
- **`log_journal`**.

---

## 5. DAG 4 — Scoring MCRS (`dag_ml_scoring`)

**Schedule :** `30 7 * * *` (quotidien 07h30 — après dag_recouvrement et dag_donnees_externes)
**Objectif :** Calculer le score MCRS pour tous les clients actifs.

### Tâches
1. **Parallèle :**
   - **`feat_comportemental`** : agrège les features CRS et RPS depuis `stg_collectes_epargne`, `int_collectes_par_agent`, `stg_creances`.
   - **`feat_externe`** : agrège les features CSI depuis `stg_prix_produits`, `stg_meteo`, `stg_indicateurs_macro`.
2. **`assembler_features`** : joint les deux groupes → `ml.features_client` (43 features, upsert par client_id + date).
3. **`charger_modele`** : charge le modèle champion depuis le répertoire de stockage (`/models/champion/mcrs_model.pkl`).
4. **`scorer_clients`** : `MCRSModel.predict_batch()` par lots de 500 → `ml.client_scores`.
5. **`calculer_shap`** : `MCRSModel` génère SHAP values (top 10) → `ml.shap_explanations`.
6. **`generer_alertes_ml`** : insère alertes prédictives dans `ml.alertes_predictives` pour les clients CRITIQUE/ELEVE.
7. **`maj_scores_creances`** : met à jour `score_mcrs`, `classe_risque_mcrs` dans `app.creances`.
8. **`detecter_drift`** : calcule PSI entre distribution actuelle et distribution de référence (entraînement).
9. **`brancher_retrain`** : si PSI > 0.20 → déclenche `dag_ml_training` via Airflow API.
10. **`notif_sse`** : publie `scoring_updated`.
11. **`log_journal`**.

---

## 6. DAG 5 — Entraînement MCRS (`dag_ml_training`)

**Schedule :** `0 2 * * 0` (dimanche 02h00) + déclenchement conditionnel (PSI > 0.20)
**Objectif :** Réentraîner le modèle MCRS sur 2 ans de données avec walk-forward.

### Tâches
1. **`preparer_dataset`** : extrait 2 ans de `ml.features_client` + labels défaut.
2. **`split_walk_forward`** : génère 5 folds temporels (12 mois train, 3 mois test, 1 mois gap).
3. **`entrainer_xgboost`** : entraîne XGBoostClassifier avec hyperparamètres optimisés (max_depth=6, n_estimators=300, learning_rate=0.05, scale_pos_weight adapté au déséquilibre classe).
4. **`cross_validation`** : calcule AUC, précision, rappel, KS sur les 5 folds.
5. **`calibrer_platt`** : calibration isotonique (Platt scaling) pour des probabilités calibrées.
6. **`analyse_survie_cox`** : modèle de survie Cox sur les données de promesses de paiement.
7. **`shap_global`** : calcule l'importance globale des features SHAP pour le rapport d'entraînement.
8. **`comparer_champion_challenger`** : compare l'AUC challenger vs champion (seuil amélioration : 0.005 AUC).
9. **`brancher_promotion`** : si challenger meilleur → sauvegarde comme nouveau champion.
10. **`sauvegarder_modele`** : pickle + métadonnées JSON.
11. **`log_mlflow`** : insère toutes les métriques dans `ml.model_runs`.
12. **`log_journal`**.

---

## 7. Couches dbt

### 7.1 `staging.*` — Nettoyage et typage
| Modèle | Source | Transformation principale |
|---|---|---|
| `stg_collectes_epargne` | `raw.collectes_terrain` | UUID dedup, flags qualité, types castés |
| `stg_creances` | `raw.export_cbs` | PAR calculé, classe COBAC A-E, provisions |
| `stg_prix_produits` | `raw.prix_marche` | Moyennes mobiles 7j/30j, variation % |
| `stg_indicateurs_macro` | `raw.indicateurs_macro` | Normalisation source, pivotage par type |
| `stg_meteo` | `raw.donnees_meteo` | Agrégation par zone, indice sécheresse |

### 7.2 `intermediate.*` — Agrégats comportementaux
| Modèle | Description |
|---|---|
| `int_collectes_par_agent` | Agrégats hebdomadaires : montant, nb transactions, canal, rang agence, variation |
| `int_profil_recouvrement_client` | Joint créances + collectes 12m : régularité collecte, ratio collecte/crédit |

### 7.3 `mart/dw.*` — Schéma en étoile
Alimenté depuis staging et intermediate. Voir `02_modele_de_donnees.md` pour le schéma complet.

### 7.4 `ml.*` — Feature store
| Modèle | Description |
|---|---|
| `feat_client_externe` | Features CSI : prix stats (mean, stddev, pente 30j), météo, macro, événements |
| `features_client` | Join final 43 features, clé surrogate dbt_utils, features dérivées |
