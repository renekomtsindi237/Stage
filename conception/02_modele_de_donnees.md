# 02 — Modèle de Données

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Principes de modélisation

- **Multi-tenant** : toutes les tables opérationnelles incluent `imf_id BIGINT NOT NULL`, référençant `app.imfs`.
- **Schémas séparés** : `app.*` (opérationnel), `raw.*` (ingestion), `dw.*` (entrepôt), `ml.*` (feature store).
- **Idempotence pipeline** : les tables `dw.*` utilisent des clés de substitution générées par dbt, les tables `raw.*` ont des clés naturelles composites pour la déduplication.
- **Historisation** : les snapshots KPI (collectes et recouvrement) sont archivés quotidiennement sans écrasement.

---

## 2. Schéma opérationnel — `app.*`

### 2.1 Entités centrales collectes

**`app.cycles_collecte`**
```sql
id, imf_id, agence_id, periodicite (HEBDOMADAIRE|BIHEBDOMADAIRE|MENSUEL),
date_debut, date_fin, statut (EN_COURS|TERMINE|ANNULE),
objectif_global_montant, created_at
```

**`app.collectes_epargne`** (V19)
```sql
id, imf_id, agence_id, agent_id, client_id, cycle_id,
uuid_mobile VARCHAR(36) UNIQUE,  -- UUID v4 déduplication offline
montant NUMERIC(15,2), date_collecte, canal (ESPECES|MOBILE_MONEY|VIREMENT),
statut (SOUMISE|VALIDEE|REJETEE|SYNCHRONISEE),
latitude, longitude, precision_gps,
source_sync (MOBILE|WEB|BATCH), date_sync, synchro_id,
motif_rejet, validated_by, validated_at,
created_at, updated_at
```

**`app.objectifs_collecte`**
```sql
id, imf_id, cycle_id, agent_id, agence_id,
montant_objectif, montant_realise, taux_realisation_montant NUMERIC GENERATED,
nb_clients_cible, nb_clients_realise,
created_at, updated_at
```

### 2.2 Entités clients informels

**`app.clients_informels`** (V20)
```sql
id, imf_id, client_id (FK app.clients),
secteur_activite, marche_principal, zone_geographique,
latitude_activite, longitude_activite, rayon_activite_km,
nb_dependants, niveau_education, anciennete_mois,
revenu_mensuel_estime, created_at
```

**`app.produits_generiques`**
```sql
id, imf_id, code_produit VARCHAR(20), nom_produit,
categorie (CEREALES|TUBERCULES|OLEAGINEUX|CULTURES_RENTE|ELEVAGE|ARTISANAT|COMMERCE),
unite_mesure, saisons_pic TEXT[],  -- ex: ['MARS','AVRIL','SEPTEMBRE']
zones_production TEXT[],
actif BOOLEAN DEFAULT TRUE,
created_at
```
*Seedé avec 15 produits : MAIS, MANIOC, PLANTAIN, ARACHIDE, CACAO, CAFE_ROBUSTA, COTON, IGNAME, PATATE_DOUCE, TOMATE, PIMENT, POULET_VILLAGE, POISSON_FUME, HUILE_PALME, SORGHO.*

**`app.client_activites_produits`**
```sql
id, imf_id, client_informel_id, produit_id,
est_activite_principale, volume_mensuel_estime, unite_volume,
revenu_mensuel_produit_estime, periode_haute_debut, periode_haute_fin,
created_at
```

### 2.3 Entités créances

**`app.creances`** (V22)
```sql
id, imf_id, agence_id, client_id, agent_id,
numero_contrat VARCHAR(50), type_credit,
montant_decaisse NUMERIC(15,2), date_decaissement,
duree_mois, taux_interet_annuel NUMERIC(5,2),
montant_encours NUMERIC(15,2), montant_echues NUMERIC(15,2),
date_derniere_echeance, date_dernier_paiement, date_dernier_paiement_cbs,
jours_retard INTEGER,
-- Classification COBAC (calculée par le pipeline)
classe_cobac CHAR(1),  -- A, B, C, D, E
taux_provision NUMERIC(5,2), montant_provision NUMERIC(15,2),
categorie_par VARCHAR(10),  -- PAR30, PAR60, PAR90, PAR180, SAIN
-- Scoring MCRS (mis à jour quotidiennement par dag_ml_scoring)
score_mcrs NUMERIC(4,3), score_crs NUMERIC(4,3),
score_rps NUMERIC(4,3), score_csi NUMERIC(4,3),
classe_risque_mcrs VARCHAR(10),  -- FAIBLE, MODERE, ELEVE, CRITIQUE
-- Garantie et dossier
type_garantie, valeur_garantie NUMERIC(15,2),
statut_recouvrement, gestionnaire_id,
created_at, updated_at
```

**`app.promesses_paiement`**
```sql
id, imf_id, creance_id, client_id,
montant_promesse NUMERIC(15,2), date_promesse,
statut (EN_ATTENTE|RESPECTEE|PARTIELLEMENT_RESPECTEE|ROMPUE),
montant_realise NUMERIC(15,2), date_realisation,
agent_suivi_id, notes, created_at
```

**`app.kpi_recouvrement_snapshots`**
```sql
id, imf_id, agence_id, date_snapshot,
encours_total, encours_sain,
encours_par30, taux_par30, encours_par60, taux_par60,
encours_par90, taux_par90, encours_par180, taux_par180,
nb_creances_b, montant_provisions_b,
nb_creances_c, montant_provisions_c,
nb_creances_d, montant_provisions_d,
nb_creances_e, montant_provisions_e,
total_provisions, taux_recouvrement_pct,
montant_recouvre_mois, created_at
```

### 2.4 Facteurs externes

**`app.prix_produits`** (V21)
```sql
id, imf_id, produit_id, zone_id, date_prix,
prix_unitaire NUMERIC(10,2), unite_mesure,
source_type (TERRAIN|MINCOMMERCE|API|SCRAPING),
source_url, fiabilite_score INTEGER (1–5),
created_at
```

**`app.facteurs_macro`**
```sql
id, imf_id, date_indicateur, source,
type_indicateur (INFLATION|TAUX_DIRECTEUR_BEAC|COURS_EUR_XAF|CHOMAGE|IPC|CROISSANCE_PIB),
valeur NUMERIC(12,4), unite, periodicite,
created_at
```

**`app.donnees_meteo`**
```sql
id, zone_id, date_meteo, source,
precipitation_mm NUMERIC(6,1), temperature_max, temperature_min,
indice_secheresse NUMERIC(4,2),  -- Palmer DSI
humidite_relative NUMERIC(5,1),
created_at
```

**`app.evenements_exterieurs`**
```sql
id, imf_id, date_debut, date_fin, zone_ids TEXT[],
type_evenement (FETE_NATIONALE|MARCHE_LOCAL|ELECTION|CRISE_SECURITE|PENURIE|AUTRE),
description, impact_estime (POSITIF|NEUTRE|NEGATIF), created_at
```

---

## 3. Schéma entrepôt — `dw.*`

### 3.1 Tables de dimensions

| Table | Clé | Description |
|---|---|---|
| `dim_date` | `date_key` (int YYYYMMDD) | Calendrier 2020-2035, fêtes camerounaises, semaines COBAC |
| `dim_client` | `client_sk` | Client avec profil informel, secteur, zone |
| `dim_agent` | `agent_sk` | Agent avec agence, zone d'opération |
| `dim_agence` | `agence_sk` | Agence avec région, IMF |
| `dim_produit_generique` | `produit_sk` | Produit avec catégorie, saisons, zones |
| `dim_cycle` | `cycle_sk` | Cycle de collecte avec périodicité et objectifs |

### 3.2 Tables de faits

**`dw.fact_collectes_epargne`**
```
fact_collecte_sk, date_key, client_sk, agent_sk, agence_sk, cycle_sk, imf_id,
montant, canal, statut, est_validee, est_doublon,
est_geolocalisee, est_en_zone_target, heure_collecte
```

**`dw.fact_creances`** (snapshot journalier)
```
fact_creance_sk, date_key, client_sk, agence_sk, imf_id,
montant_encours, jours_retard, classe_cobac, categorie_par,
taux_provision, montant_provision,
score_mcrs, score_crs, score_rps, score_csi, classe_risque_mcrs
```

**`dw.fact_actions_recouvrement`**
```
fact_action_sk, date_key, creance_sk, agent_sk, agence_sk, imf_id,
type_action, resultat, montant_recouvre
```

**`dw.fact_prix_produits`**
```
fact_prix_sk, date_key, produit_sk, zone_id, imf_id,
prix_unitaire, source_type, fiabilite_score,
moy_mobile_7j, moy_mobile_30j, variation_7j_pct, variation_30j_pct
```

### 3.3 Vues de reporting
- `dw.v_par_par_agence` : PAR30/90 par agence et par date.
- `dw.v_collectes_agent_semaine` : collectes hebdomadaires par agent vs objectif.
- `dw.v_tendance_prix_produits` : prix + moyennes mobiles 30j/90j par produit et zone.

---

## 4. Schéma ML — `ml.*`

**`ml.features_client`** (43 features — mise à jour quotidienne)

*Groupe CRS (7 features) :* `regularite_collecte_pct`, `nb_collectes_30j`, `montant_moyen_collecte`, `tendance_collecte_30j`, `coefficient_variation_collecte`, `nb_semaines_sans_collecte`, `rang_collecte_agence`.

*Groupe RPS (6 features) :* `jours_retard_actuel`, `nb_incidents_paiement_12m`, `taux_remboursement_historique`, `ratio_creance_revenus`, `nb_reechelonnements`, `score_rps_precedent`.

*Groupe CSI (15 features) :* features prix produits (6 : prix moyen, stddev, pente 30j, volatilité, saisonnalité, tendance), features météo (4 : précipitations cumulées, indice sécheresse, température, humidité), features macro (4 : inflation, taux BEAC, IPC, chômage), 1 feature événements.

*Features dérivées (15) :* `indice_resilience`, `capacite_remboursement`, `ratio_collecte_credit`, `score_diversification_produits`, + 11 features d'interaction et de lag temporel.

**`ml.client_scores`**
```sql
id, client_id, imf_id, date_score,
score_crs, score_rps, score_csi, score_mcrs,
ic_bas_95, ic_haut_95,  -- intervalles de confiance
classe_risque, probabilite_defaut_90j,
action_recommandee, top_feature, top_shap_value,
version_modele, created_at
```

**`ml.shap_explanations`** : top 10 SHAP values par client et par date de scoring.

**`ml.model_runs`** : journal d'entraînement MLflow-like (AUC, précision, KS, métriques walk-forward, champion/challenger).

**`ml.alertes_predictives`** : alertes ML (RISQUE_DEFAUT_IMMINENT, BAISSE_COLLECTE_PERSISTANTE, DETERIORATION_RAPIDE, DRIFT_DETECTE).
