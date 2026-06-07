# DOCUMENTATION ALGORITHMES & COMPLEXITÉ
## Pipeline de Données — Collectes Digitales & Recouvrement de Créances — IMF Cameroun

---

| Champ | Valeur |
|---|---|
| **Document** | Algorithmes & Complexité (ALGO) |
| **Version** | 1.0 |
| **Auteur** | Étudiant Ingénieur 4 — Institut Universitaire Saint Jean |
| **Date** | 2026-03-31 |
| **Statut** | Draft |

---

## TABLE DES MATIÈRES

1. [Algorithmes de calcul des KPIs métier](#1-algorithmes-de-calcul-des-kpis-métier)
2. [Algorithmes ETL du pipeline](#2-algorithmes-etl-du-pipeline)
3. [Tableau de synthèse des complexités](#3-tableau-de-synthèse-des-complexités)
4. [Optimisations appliquées](#4-optimisations-appliquées)
5. [Algorithme de scoring de risque client](#5-algorithme-de-scoring-de-risque-client)

---

## 1. Algorithmes de calcul des KPIs métier

### 1.1 Algorithme de calcul du PAR (Portfolio at Risk)

#### Définition CGAP/COBAC

```
PAR_N = Encours des prêts avec au moins un remboursement en retard de plus de N jours
        ─────────────────────────────────────────────────────────────────────────────
                          Encours total brut des prêts actifs
```

**Note** : L'encours d'un prêt = capital restant dû (pas les intérêts, pas les pénalités).
Un seul remboursement en retard contamine l'encours total du prêt (pas seulement l'échéance en retard).

#### Pseudo-code

```
ALGORITHME calculer_PAR(prêts[], echeances[], remboursements[], date_ref, N)

ENTRÉES :
  prêts[]         : liste des prêts actifs {id, capital_restant_du}
  echeances[]     : liste des échéances {id_pret, date_echeance, montant_du}
  remboursements[]: liste des remboursements {id_echeance, montant_paye}
  date_ref        : date de calcul
  N               : seuil en jours (30 ou 90)

SORTIES :
  taux_par        : float [0, 1]
  liste_prets_a_risque : liste des prêts concernés

DÉBUT
  encours_total ← 0
  encours_a_risque ← 0
  liste_prets_a_risque ← []

  // Étape 1 : Calculer le montant remboursé par échéance
  remb_par_echeance ← MAP vide (id_echeance → montant_total_paye)
  POUR CHAQUE remboursement r DANS remboursements[] FAIRE
    remb_par_echeance[r.id_echeance] += r.montant_paye
  FIN POUR

  // Étape 2 : Identifier les prêts avec retard > N jours
  prets_en_retard ← ENSEMBLE vide
  POUR CHAQUE echéance e DANS echeances[] FAIRE
    montant_restant ← e.montant_du - remb_par_echeance.get(e.id_echeance, 0)
    SI montant_restant > 0 ET (date_ref - e.date_echeance) > N JOURS ALORS
      prets_en_retard.AJOUTER(e.id_pret)
    FIN SI
  FIN POUR

  // Étape 3 : Calculer les encours
  POUR CHAQUE prêt p DANS prêts[] FAIRE
    encours_total += p.capital_restant_du
    SI p.id DANS prets_en_retard ALORS
      encours_a_risque += p.capital_restant_du
      liste_prets_a_risque.AJOUTER(p)
    FIN SI
  FIN POUR

  // Étape 4 : Calculer le ratio
  SI encours_total = 0 ALORS
    taux_par ← 0
  SINON
    taux_par ← encours_a_risque / encours_total
  FIN SI

  RETOURNER (taux_par, liste_prets_a_risque)
FIN
```

#### Implémentation SQL (dbt model `fact_par_quotidien.sql`)

```sql
-- Étape 1 : Capital restant dû par prêt
WITH encours_prets AS (
    SELECT
        p.id_pret,
        p.id_client,
        p.id_agent,
        p.id_produit,
        z.id_zone,
        p.montant_octroye - COALESCE(SUM(r.montant_paye), 0) AS capital_restant_du
    FROM staging.prets p
    JOIN staging.echeances e ON p.id_pret = e.id_pret
    LEFT JOIN staging.remboursements r ON e.id_echeance = r.id_echeance
    JOIN staging.clients c ON p.id_client = c.id_client
    JOIN staging.zones z ON c.id_zone = z.id_zone
    WHERE p.statut IN ('ACTIF', 'EN_RETARD')
    GROUP BY p.id_pret, p.id_client, p.id_agent, p.id_produit, z.id_zone, p.montant_octroye
),

-- Étape 2 : Jours de retard maximum par prêt
retard_par_pret AS (
    SELECT
        e.id_pret,
        MAX(
            CASE
                WHEN e.montant_du - COALESCE(SUM(r.montant_paye), 0) > 0
                THEN CURRENT_DATE - e.date_echeance
                ELSE 0
            END
        ) AS jours_retard_max
    FROM staging.echeances e
    LEFT JOIN staging.remboursements r ON e.id_echeance = r.id_echeance
    WHERE e.date_echeance <= CURRENT_DATE
    GROUP BY e.id_pret
),

-- Étape 3 : Jointure et calcul PAR par zone et produit
par_calcule AS (
    SELECT
        TO_CHAR(CURRENT_DATE, 'YYYYMMDD')::INTEGER AS date_id,
        ep.id_zone AS zone_id,
        ep.id_produit AS produit_id,
        SUM(ep.capital_restant_du) AS encours_total,
        SUM(CASE WHEN rp.jours_retard_max > 30 THEN ep.capital_restant_du ELSE 0 END) AS encours_par30,
        SUM(CASE WHEN rp.jours_retard_max > 90 THEN ep.capital_restant_du ELSE 0 END) AS encours_par90,
        COUNT(ep.id_pret) AS nb_prets_actifs,
        COUNT(CASE WHEN rp.jours_retard_max > 0 THEN 1 END) AS nb_prets_en_retard
    FROM encours_prets ep
    LEFT JOIN retard_par_pret rp ON ep.id_pret = rp.id_pret
    GROUP BY ep.id_zone, ep.id_produit
)

SELECT
    date_id,
    zone_id,
    produit_id,
    encours_total,
    encours_par30,
    encours_par90,
    CASE WHEN encours_total > 0 THEN ROUND(encours_par30::NUMERIC / encours_total, 4) ELSE 0 END AS taux_par30,
    CASE WHEN encours_total > 0 THEN ROUND(encours_par90::NUMERIC / encours_total, 4) ELSE 0 END AS taux_par90,
    nb_prets_actifs,
    nb_prets_en_retard
FROM par_calcule
```

#### Analyse de complexité

| Étape | Opération | Complexité temporelle | Complexité spatiale |
|---|---|---|---|
| Agrégation remboursements par échéance | Hash aggregation | O(R) | O(E) |
| Détection retards par prêt | Scan + agrégation | O(E) | O(P) |
| Jointure encours × retards | Hash join | O(P + E) | O(P) |
| Agrégation par zone/produit | Hash aggregation | O(P) | O(Z × PR) |
| **Total** | | **O(R + E + P)** | **O(P + E)** |

Avec R = nb remboursements, E = nb échéances, P = nb prêts actifs, Z = nb zones, PR = nb produits.

> **Conclusion** : Complexité linéaire O(n) par rapport au nombre total d'enregistrements. Optimal pour un calcul batch quotidien.

---

### 1.2 Algorithme de calcul du taux de recouvrement

```
Taux de recouvrement (période T) = Montants effectivement recouvrés pendant T
                                   ─────────────────────────────────────────
                                      Montants échus pendant T

Variante agent : Taux_agent_i = SUM(remboursements reçus par agent i sur T)
                                ──────────────────────────────────────────────
                                SUM(montants dus des prêts gérés par agent i sur T)
```

#### Pseudo-code

```
ALGORITHME calculer_taux_recouvrement(echeances[], remboursements[], date_debut, date_fin, groupe_par)

ENTRÉES :
  echeances[]     : échéances dont la date_echeance ∈ [date_debut, date_fin]
  remboursements[]: remboursements dont la date_paiement ∈ [date_debut, date_fin]
  groupe_par      : 'agent' | 'zone' | 'produit' | 'global'

SORTIE :
  dict            : {groupe → taux_recouvrement}

DÉBUT
  dus_par_groupe    ← MAP vide
  recouvrés_par_groupe ← MAP vide

  POUR CHAQUE échéance e DANS echeances[] FAIRE
    clé ← extraire_clé(e, groupe_par)
    dus_par_groupe[clé] += e.montant_du
  FIN POUR

  POUR CHAQUE remboursement r DANS remboursements[] FAIRE
    clé ← extraire_clé(r, groupe_par)
    recouvrés_par_groupe[clé] += r.montant_paye
  FIN POUR

  résultat ← MAP vide
  POUR CHAQUE clé DANS dus_par_groupe FAIRE
    SI dus_par_groupe[clé] > 0 ALORS
      résultat[clé] ← recouvrés_par_groupe.get(clé, 0) / dus_par_groupe[clé]
    SINON
      résultat[clé] ← NULL
    FIN SI
  FIN POUR

  RETOURNER résultat
FIN
```

**Complexité** : O(E + R) temporelle, O(G) spatiale avec G = nombre de groupes distincts.

---

## 2. Algorithmes ETL du pipeline

### 2.1 Algorithme de déduplication par hashing

#### Problème

Les relevés mobile money peuvent contenir des doublons :
- Même transaction envoyée deux fois dans le fichier CSV
- Fichier CSV re-envoyé intégralement lors d'une correction

#### Solution : Hash SHA-256 sur la clé naturelle

```
hash_dedup = SHA-256( reference_externe || date_transaction || montant || operateur )
```

La colonne `hash_dedup` possède une contrainte `UNIQUE` dans PostgreSQL. L'insertion utilise `ON CONFLICT DO NOTHING`.

#### Pseudo-code

```
ALGORITHME dedupliquer_transactions(df: DataFrame) → DataFrame

ENTRÉES :
  df : DataFrame avec colonnes (reference_externe, date_transaction, montant, operateur, ...)

SORTIE :
  df_dedup : DataFrame sans doublons

DÉBUT
  POUR CHAQUE ligne ligne DANS df FAIRE
    clé_naturelle ← ligne.reference_externe
                   + STR(ligne.date_transaction)
                   + STR(ligne.montant)
                   + ligne.operateur
    ligne.hash_dedup ← SHA256(clé_naturelle.encode('utf-8')).hexdigest()
  FIN POUR

  // Déduplication intra-fichier (avant insertion)
  df_dedup ← df.DROP_DUPLICATES(subset=['hash_dedup'])

  // La déduplication inter-fichiers est gérée par PostgreSQL
  // INSERT INTO ... ON CONFLICT (hash_dedup) DO NOTHING

  RETOURNER df_dedup
FIN
```

#### Implémentation Python

```python
import hashlib
import pandas as pd

def compute_dedup_hash(row: pd.Series) -> str:
    """
    Calcule un hash SHA-256 unique pour déduplication de transaction.
    Complexité : O(1) par ligne — O(n) pour n lignes
    """
    key = (
        str(row.get('reference_externe', ''))
        + str(row.get('date_transaction', ''))
        + str(row.get('montant', ''))
        + str(row.get('operateur', ''))
    )
    return hashlib.sha256(key.encode('utf-8')).hexdigest()


def deduplicate_dataframe(df: pd.DataFrame) -> tuple[pd.DataFrame, int]:
    """
    Applique la déduplication intra-fichier.
    Retourne le DataFrame dédupliqué et le nombre de doublons supprimés.
    Complexité temporelle : O(n) — hash O(1) par ligne + groupby O(n)
    Complexité spatiale : O(n)
    """
    df = df.copy()
    df['hash_dedup'] = df.apply(compute_dedup_hash, axis=1)
    before = len(df)
    df = df.drop_duplicates(subset=['hash_dedup'], keep='first')
    duplicates_removed = before - len(df)
    return df, duplicates_removed
```

#### Analyse de complexité

| Opération | Complexité temporelle | Complexité spatiale | Remarque |
|---|---|---|---|
| Calcul hash par ligne | O(L) où L = longueur clé | O(1) | L est borné (~100 caractères) → O(1) pratique |
| Calcul hash pour n lignes | O(n) | O(n) | |
| `drop_duplicates` (Pandas) | O(n) | O(n) | Table de hachage interne |
| INSERT ON CONFLICT (PostgreSQL) | O(1) par ligne avec index hash | O(n) | Index B-tree sur hash_dedup |
| **Total pipeline** | **O(n)** | **O(n)** | |

---

### 2.2 Algorithme de chargement incrémental (delta load)

#### Problème

Le rechargement quotidien intégral (full load) du CBS serait inefficace si l'historique est large. Le delta load ne charge que les enregistrements nouveaux ou modifiés depuis le dernier chargement.

#### Stratégie : Watermark sur `updated_at`

```
ALGORITHME delta_load(source_table, target_table, watermark_column, last_watermark)

ENTRÉES :
  source_table    : table ou fichier source
  target_table    : table cible (raw ou staging)
  watermark_column: colonne de date de modification (ex: updated_at)
  last_watermark  : dernier timestamp de chargement (lu dans une table de contrôle)

DÉBUT
  // Lecture uniquement des enregistrements modifiés
  new_records ← SELECT * FROM source_table
                WHERE watermark_column > last_watermark

  SI new_records EST VIDE ALORS
    LOG "Aucun nouvel enregistrement"
    RETOURNER (0, last_watermark)
  FIN SI

  // Insertion/mise à jour dans la cible
  POUR CHAQUE record DANS new_records FAIRE
    UPSERT INTO target_table VALUES (record)
    // UPSERT = INSERT ... ON CONFLICT (id) DO UPDATE SET ...
  FIN POUR

  // Mise à jour du watermark
  new_watermark ← MAX(new_records.watermark_column)
  UPDATE control_table SET last_watermark = new_watermark
                       WHERE table_name = source_table

  RETOURNER (COUNT(new_records), new_watermark)
FIN
```

#### Comparaison Full Load vs Delta Load

| Critère | Full Load | Delta Load |
|---|---|---|
| Complexité temporelle | O(N_total) | O(N_delta) où N_delta << N_total |
| Complexité spatiale | O(N_total) | O(N_delta) |
| Risque de manquer des enregistrements | Faible | Présent si watermark mal géré |
| Simplicité d'implémentation | Élevée | Modérée |
| Adapté au projet IMF | Pour les 6 premiers mois | Après > 100 000 enregistrements |

> **Décision** : Full load pendant la phase initiale (volume faible). Delta load activé si la table CBS dépasse 50 000 lignes.

---

### 2.3 Algorithme de jointure et réconciliation transactions / prêts

#### Problème

Relier une transaction mobile money à un prêt/échéance spécifique est complexe car :
- Le numéro de téléphone de l'expéditeur peut ne pas correspondre exactement au numéro enregistré dans le CBS
- La description de la transaction peut contenir une référence de prêt, mais dans des formats variés

#### Algorithme de rapprochement en 3 passes

```
ALGORITHME rapprocher_transactions(transactions[], remboursements_cbs[], clients[])

PASSE 1 — Correspondance directe par référence explicite
  POUR CHAQUE transaction t DANS transactions[] FAIRE
    SI t.description CONTIENT référence_pret (regex: r'\bPRET\d+\b' ou r'\bCRD\d+\b') ALORS
      id_pret ← extraire_reference(t.description)
      MAPPER t → id_pret
    FIN SI
  FIN POUR
  transactions_non_mappées ← transactions SANS correspondance

PASSE 2 — Correspondance par numéro de téléphone + montant + date
  POUR CHAQUE transaction t DANS transactions_non_mappées FAIRE
    candidats ← CHERCHER clients où telephone ≈ t.numero_expediteur
    POUR CHAQUE client c DANS candidats FAIRE
      echeances_dues ← CHERCHER échéances de c proches de t.date_transaction
      POUR CHAQUE echeance e DANS echeances_dues FAIRE
        SI ABS(t.montant - e.montant_du) < TOLERANCE (5%) ALORS
          MAPPER t → e.id_pret (correspondance probable)
          BREAK
        FIN SI
      FIN POUR
    FIN POUR
  FIN POUR
  transactions_non_mappées ← transactions encore SANS correspondance

PASSE 3 — Transactions non réconciliées → table suspense
  POUR CHAQUE transaction t DANS transactions_non_mappées FAIRE
    INSÉRER t DANS raw.transactions_suspense
    LOGGER "Transaction non réconciliée: " + t.reference_externe
  FIN POUR
```

**Complexité** :
- Passe 1 : O(T × L) avec L = longueur description (regex) → O(T) pratique
- Passe 2 : O(T × C × E) dans le pire cas → O(T × E/C_avg) avec index téléphone
- **Total** : O(T × E) avec index → optimisé à O(T log E) avec index B-tree sur téléphone + date

---

## 3. Tableau de synthèse des complexités

| # | Algorithme | Complexité Temporelle | Complexité Spatiale | Justification du choix |
|---|---|---|---|---|
| A01 | Calcul PAR30/PAR90 (SQL) | O(R + E + P) | O(P + Z × PR) | Scans linéaires + hash join en SQL |
| A02 | Calcul taux de recouvrement | O(E + R) | O(G) | Agrégation linéaire par groupe |
| A03 | Déduplication par SHA-256 | O(n) | O(n) | Hash O(1) par ligne, table de hachage |
| A04 | Delta load (watermark) | O(N_delta) | O(N_delta) | Filtrage par index sur updated_at |
| A05 | Rapprochement transactions/prêts (passe 1) | O(T) | O(T) | Regex sur description |
| A06 | Rapprochement transactions/prêts (passe 2) | O(T log E) | O(T) | Index B-tree téléphone + date |
| A07 | Calcul score de risque (règles) | O(P × K) | O(P) | K règles appliquées par prêt |
| A08 | Tri agrégats temporels (SQL) | O(n log n) | O(n) | ORDER BY en SQL → quicksort/mergesort |
| A09 | Génération alertes impayés | O(P) | O(A) | Scan prêts actifs |
| A10 | Chargement initial données de référence (seeds dbt) | O(S) | O(S) | S = taille des seeds (petit) |

**Légende** : T = nb transactions, E = nb échéances, P = nb prêts, R = nb remboursements, G = nb groupes, Z = nb zones, PR = nb produits, A = nb alertes, K = nb règles scoring

---

## 4. Optimisations appliquées

### 4.1 Index PostgreSQL

```sql
-- Index pour accélération des calculs PAR
-- Permet de filtrer les échéances en retard sans full scan
CREATE INDEX idx_echeances_date_statut
    ON staging.echeances(date_echeance, statut)
    WHERE statut IN ('EN_RETARD', 'A_VENIR');

-- Index partiel sur les prêts actifs seulement (réduit la taille de l'index)
CREATE INDEX idx_prets_actifs
    ON staging.prets(id_client, id_agent, id_produit)
    WHERE statut IN ('ACTIF', 'EN_RETARD');

-- Index pour la déduplication (lookup O(log n) au lieu de O(n))
CREATE UNIQUE INDEX idx_tmm_hash
    ON staging.transactions_mobile_money(hash_dedup);

-- Index pour le rapprochement par téléphone
CREATE INDEX idx_clients_telephone_hash
    ON staging.clients USING hash(telephone);

-- Index composite pour les requêtes de tableau de bord (date + zone)
CREATE INDEX idx_fact_collectes_date_zone
    ON dw.fact_collectes(date_id, zone_id);

CREATE INDEX idx_fact_par_date_zone
    ON dw.fact_par_quotidien(date_id, zone_id);
```

### 4.2 Vues matérialisées pour Superset

Les dashboards Superset n'interrogent jamais directement les tables de faits brutes. Ils utilisent des vues matérialisées pré-agrégées dans le schéma `reporting` :

```sql
-- Vue matérialisée : KPIs collectes (rafraîchie quotidiennement par dbt)
CREATE MATERIALIZED VIEW reporting.v_kpi_collectes AS
SELECT
    t.date_complete,
    t.mois,
    t.annee,
    z.nom_zone,
    z.region,
    c.nom_canal,
    a.nom_agent,
    COUNT(fc.collecte_id)               AS nb_collectes,
    SUM(fc.montant_collecte)            AS montant_total,
    AVG(fc.montant_collecte)            AS montant_moyen,
    SUM(fc.est_mobile_money::INT)       AS nb_mobile_money
FROM dw.fact_collectes fc
JOIN dw.dim_temps t ON fc.date_id = t.date_id
JOIN dw.dim_zone z ON fc.zone_id = z.zone_id
JOIN dw.dim_canal c ON fc.canal_id = c.canal_id
JOIN dw.dim_agent a ON fc.agent_id = a.agent_id
GROUP BY t.date_complete, t.mois, t.annee, z.nom_zone, z.region, c.nom_canal, a.nom_agent;

CREATE UNIQUE INDEX ON reporting.v_kpi_collectes(date_complete, nom_zone, nom_canal, nom_agent);

-- Vue matérialisée : KPIs recouvrement
CREATE MATERIALIZED VIEW reporting.v_kpi_recouvrement AS
SELECT
    t.date_complete,
    t.mois,
    t.annee,
    z.nom_zone,
    p.nom_produit,
    fp.encours_total,
    fp.encours_par30,
    fp.encours_par90,
    fp.taux_par30,
    fp.taux_par90,
    fp.nb_prets_actifs,
    fp.nb_prets_en_retard
FROM dw.fact_par_quotidien fp
JOIN dw.dim_temps t ON fp.date_id = t.date_id
JOIN dw.dim_zone z ON fp.zone_id = z.zone_id
JOIN dw.dim_produit p ON fp.produit_id = p.produit_id;
```

**Gain de performance** : Avec des vues matérialisées, les requêtes Superset passent de O(millions de lignes brutes) à O(quelques milliers de lignes agrégées). Gain typique : facteur 100x sur les temps de réponse.

### 4.3 Partitionnement par date (si volume > 500 000 lignes)

```sql
-- Partitionnement de fact_remboursements par année
CREATE TABLE dw.fact_remboursements_2024
    PARTITION OF dw.fact_remboursements
    FOR VALUES FROM (20240101) TO (20250101);

CREATE TABLE dw.fact_remboursements_2025
    PARTITION OF dw.fact_remboursements
    FOR VALUES FROM (20250101) TO (20260101);

CREATE TABLE dw.fact_remboursements_2026
    PARTITION OF dw.fact_remboursements
    FOR VALUES FROM (20260101) TO (20270101);
```

**Impact** : Les requêtes filtrées par année ne scannent qu'une partition → division du temps de réponse par le nombre d'années de données.

---

## 5. Algorithme de scoring de risque client

### 5.1 Définition du score

Le score de risque est calculé sur une échelle de 0 à 100 :
- **0–30** : Risque faible (client fiable, remboursements réguliers)
- **31–60** : Risque modéré (quelques retards occasionnels)
- **61–80** : Risque élevé (retards fréquents ou longs)
- **81–100** : Risque très élevé (PAR90 ou défaut récent)

### 5.2 Règles de scoring (système à points)

```
ALGORITHME calculer_score_risque(client_id, historique_prets[])

SCORE_BASE ← 0

// Règle R01 : Jours de retard maximum sur prêt actuel
SI max_jours_retard > 90       → SCORE += 40
SINON SI max_jours_retard > 30 → SCORE += 25
SINON SI max_jours_retard > 7  → SCORE += 10
SINON                           → SCORE += 0

// Règle R02 : Fréquence des retards historiques (sur tous les prêts passés)
taux_echeances_en_retard ← nb_echeances_en_retard / nb_echeances_total
SI taux_echeances_en_retard > 0.3   → SCORE += 25
SINON SI taux_echeances_en_retard > 0.1 → SCORE += 15
SINON SI taux_echeances_en_retard > 0   → SCORE += 5
SINON                                    → SCORE += 0

// Règle R03 : Présence d'un write-off dans l'historique
SI nb_write_off > 0 → SCORE += 30

// Règle R04 : Ancienneté client (stabilité)
SI anciennete_mois < 3   → SCORE += 10
SINON SI anciennete_mois > 24 → SCORE -= 5  (bonus fidélité)

// Règle R05 : Montant du prêt actuel / capacité estimée
SI ratio_endettement > 0.5 → SCORE += 15
SINON SI ratio_endettement > 0.3 → SCORE += 5

SCORE ← MIN(100, MAX(0, SCORE))  // Borne [0, 100]
RETOURNER SCORE
```

### 5.3 Implémentation SQL (dbt model `int_score_risque_client.sql`)

```sql
WITH historique AS (
    SELECT
        p.id_client,
        MAX(
            CASE WHEN p.statut IN ('EN_RETARD', 'EN_RECOUVREMENT')
            THEN COALESCE(rp.jours_retard_max, 0) ELSE 0 END
        ) AS max_jours_retard_actuel,
        COUNT(CASE WHEN p.statut = 'PERTE' THEN 1 END) AS nb_write_off,
        ROUND(
            COUNT(CASE WHEN e.statut = 'EN_RETARD' THEN 1 END)::NUMERIC /
            NULLIF(COUNT(e.id_echeance), 0), 4
        ) AS taux_retards_historique,
        DATE_PART('month', AGE(CURRENT_DATE, MIN(p.date_decaissement))) AS anciennete_mois
    FROM staging.prets p
    LEFT JOIN staging.echeances e ON p.id_pret = e.id_pret
    LEFT JOIN (
        SELECT id_pret, MAX(CURRENT_DATE - date_echeance) AS jours_retard_max
        FROM staging.echeances
        WHERE statut = 'EN_RETARD'
        GROUP BY id_pret
    ) rp ON p.id_pret = rp.id_pret
    GROUP BY p.id_client
)

SELECT
    id_client,
    -- Règle R01
    CASE
        WHEN max_jours_retard_actuel > 90 THEN 40
        WHEN max_jours_retard_actuel > 30 THEN 25
        WHEN max_jours_retard_actuel > 7  THEN 10
        ELSE 0
    END
    -- Règle R02
    + CASE
        WHEN taux_retards_historique > 0.3  THEN 25
        WHEN taux_retards_historique > 0.1  THEN 15
        WHEN taux_retards_historique > 0    THEN 5
        ELSE 0
    END
    -- Règle R03
    + CASE WHEN nb_write_off > 0 THEN 30 ELSE 0 END
    -- Règle R04
    + CASE
        WHEN anciennete_mois < 3   THEN 10
        WHEN anciennete_mois > 24  THEN -5
        ELSE 0
    END AS score_risque,

    CASE
        WHEN score_risque BETWEEN 0  AND 30 THEN 'FAIBLE'
        WHEN score_risque BETWEEN 31 AND 60 THEN 'MODERE'
        WHEN score_risque BETWEEN 61 AND 80 THEN 'ELEVE'
        ELSE 'TRES_ELEVE'
    END AS categorie_risque

FROM historique
```

**Complexité** : O(P × K) = O(P) car K (nombre de règles) = 5 = constante. Le scan de tous les prêts actifs est inévitable pour un scoring exhaustif.

### 5.4 Évolution possible vers le Machine Learning

À terme, ce scoring à base de règles peut être remplacé ou complété par un modèle de Machine Learning supervisé :

| Approche | Avantages | Inconvénients | Prérequis données |
|---|---|---|---|
| Règles métier (actuel) | Explicable, auditabilité COBAC | Non adaptatif, biais humain | Aucun |
| Régression logistique | Simple, explicable, rapide | Linéaire | 500+ clients historiques |
| Random Forest | Précis, gère non-linéarité | Moins explicable | 1000+ clients historiques |
| Gradient Boosting (XGBoost) | Très précis | Boîte noire, complexe | 2000+ clients historiques |

> Pour la phase de stage, le scoring à base de règles est retenu. L'extension ML est documentée comme perspective d'évolution.
