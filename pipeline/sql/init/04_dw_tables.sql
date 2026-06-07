-- ============================================================
-- 04_dw_tables.sql
-- Data Warehouse — Schéma en étoile
-- Domaine : Collectes d'épargne + Recouvrement de créances
-- Faits : fact_collectes_epargne, fact_creances, fact_actions_recouvrement
-- Dimensions : dim_client, dim_agent, dim_agence, dim_produit_generique, dim_date
-- ============================================================

-- ============================================================
-- DIMENSIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS dw.dim_date (
    date_key        INTEGER PRIMARY KEY,    -- YYYYMMDD
    date_valeur     DATE NOT NULL UNIQUE,
    annee           SMALLINT NOT NULL,
    trimestre       SMALLINT NOT NULL,      -- 1–4
    mois            SMALLINT NOT NULL,      -- 1–12
    semaine_iso     SMALLINT NOT NULL,      -- ISO 1–53
    jour_mois       SMALLINT NOT NULL,      -- 1–31
    jour_semaine    SMALLINT NOT NULL,      -- 1=Lundi, 7=Dimanche
    libelle_mois    VARCHAR(20) NOT NULL,
    libelle_jour    VARCHAR(15) NOT NULL,
    est_week_end    BOOLEAN NOT NULL DEFAULT FALSE,
    est_ferie_cm    BOOLEAN NOT NULL DEFAULT FALSE,
    est_jour_marche BOOLEAN NOT NULL DEFAULT FALSE, -- jour de marché principal
    saison          VARCHAR(15)             -- SAISON_PLUIES, SAISON_SECHE
);

COMMENT ON TABLE dw.dim_date IS 'Dimension date — 2020-2035, jours fériés Cameroun, jours de marché, saisons';

-- Pré-générer les dates 2020-2035
INSERT INTO dw.dim_date (date_key, date_valeur, annee, trimestre, mois, semaine_iso,
                         jour_mois, jour_semaine, libelle_mois, libelle_jour,
                         est_week_end, saison)
SELECT
    TO_CHAR(d, 'YYYYMMDD')::INTEGER,
    d,
    EXTRACT(YEAR FROM d)::SMALLINT,
    EXTRACT(QUARTER FROM d)::SMALLINT,
    EXTRACT(MONTH FROM d)::SMALLINT,
    EXTRACT(ISODOW FROM d + INTERVAL '3 days')::SMALLINT, -- ISO week
    EXTRACT(DAY FROM d)::SMALLINT,
    EXTRACT(ISODOW FROM d)::SMALLINT,
    TO_CHAR(d, 'TMMonth'),
    TO_CHAR(d, 'TMDay'),
    EXTRACT(ISODOW FROM d) IN (6, 7),
    CASE
        WHEN EXTRACT(MONTH FROM d) IN (3,4,5,6,9,10) THEN 'SAISON_PLUIES'
        ELSE 'SAISON_SECHE'
    END
FROM generate_series('2020-01-01'::DATE, '2035-12-31'::DATE, '1 day'::INTERVAL) AS d
ON CONFLICT (date_key) DO NOTHING;

-- Marquer les jours fériés Cameroun (récurrents)
UPDATE dw.dim_date SET est_ferie_cm = TRUE
WHERE (mois = 1  AND jour_mois = 1)   -- Jour de l'An
   OR (mois = 2  AND jour_mois = 11)  -- Fête de la Jeunesse
   OR (mois = 5  AND jour_mois = 1)   -- Fête du Travail
   OR (mois = 5  AND jour_mois = 20)  -- Fête Nationale
   OR (mois = 8  AND jour_mois = 15)  -- Assomption
   OR (mois = 12 AND jour_mois = 25); -- Noël

-- ── Dimension Client (informel) ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_client (
    client_key          BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT NOT NULL,
    client_id_externe   TEXT NOT NULL,
    nom_complet         TEXT,
    telephone           TEXT,
    zone_id             TEXT,
    secteur_principal   TEXT,
    sous_secteur        TEXT,
    revenu_mensuel_estime NUMERIC(12,2),
    latitude_activite   NUMERIC(10,7),
    longitude_activite  NUMERIC(10,7),
    anciennete_jours    INTEGER,
    date_premiere_op    DATE,
    actif               BOOLEAN DEFAULT TRUE,
    _dbt_updated_at     TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (imf_id, client_id_externe)
);

CREATE INDEX IF NOT EXISTS idx_dw_client_imf    ON dw.dim_client(imf_id, client_id_externe);
CREATE INDEX IF NOT EXISTS idx_dw_client_zone   ON dw.dim_client(zone_id);
CREATE INDEX IF NOT EXISTS idx_dw_client_secteur ON dw.dim_client(secteur_principal);

-- ── Dimension Agent ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_agent (
    agent_key       BIGSERIAL PRIMARY KEY,
    imf_id          BIGINT NOT NULL,
    agent_id_source BIGINT NOT NULL,                        -- FK vers app.utilisateurs
    username        TEXT NOT NULL,
    nom_complet     TEXT,
    agence_code     TEXT,
    zone_id         TEXT,
    est_actif       BOOLEAN DEFAULT TRUE,
    _dbt_updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (imf_id, agent_id_source)
);

-- ── Dimension Agence ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_agence (
    agence_key      BIGSERIAL PRIMARY KEY,
    imf_id          BIGINT NOT NULL,
    agence_id_source BIGINT,
    code_agence     TEXT NOT NULL,
    nom_agence      TEXT NOT NULL,
    ville           TEXT,
    region          TEXT,
    zone_id         TEXT,
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    est_active      BOOLEAN DEFAULT TRUE,
    _dbt_updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (imf_id, code_agence)
);

-- ── Dimension Produit Générique ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_produit_generique (
    produit_key     BIGSERIAL PRIMARY KEY,
    code_produit    TEXT NOT NULL UNIQUE,
    nom_produit     TEXT NOT NULL,
    categorie       TEXT,
    sous_categorie  TEXT,
    unite_ref       TEXT,
    saisonnalite    BOOLEAN DEFAULT TRUE,
    mois_saison_haute INTEGER[],
    _dbt_updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ── Dimension Cycle Collecte ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_cycle (
    cycle_key       BIGSERIAL PRIMARY KEY,
    imf_id          BIGINT NOT NULL,
    cycle_id_source BIGINT NOT NULL,
    nom_cycle       TEXT NOT NULL,
    periodicite     TEXT,
    date_debut      DATE,
    date_fin        DATE,
    _dbt_updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (imf_id, cycle_id_source)
);

-- ============================================================
-- FAITS — Collectes d'épargne
-- ============================================================

CREATE TABLE IF NOT EXISTS dw.fact_collectes_epargne (
    collecte_key        BIGSERIAL PRIMARY KEY,
    -- Clés dimensions
    date_key            INTEGER REFERENCES dw.dim_date(date_key),
    client_key          BIGINT  REFERENCES dw.dim_client(client_key),
    agent_key           BIGINT  REFERENCES dw.dim_agent(agent_key),
    agence_key          BIGINT  REFERENCES dw.dim_agence(agence_key),
    cycle_key           BIGINT  REFERENCES dw.dim_cycle(cycle_key),
    -- Mesures
    montant_collecte    NUMERIC(15,2) NOT NULL,
    canal_paiement      TEXT NOT NULL,
    statut              TEXT NOT NULL,
    -- Géolocalisation
    latitude            NUMERIC(10,7),
    longitude           NUMERIC(10,7),
    distance_agence_km  NUMERIC(8,2),
    -- Qualité
    est_hors_cycle      BOOLEAN NOT NULL DEFAULT FALSE,
    est_geolocalisee    BOOLEAN NOT NULL DEFAULT FALSE,
    heure_collecte      TIME,
    -- Traçabilité
    uuid_mobile         UUID NOT NULL,
    id_source_app       BIGINT,                             -- FK vers app.collectes_epargne
    _dbt_updated_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fact_ce_date     ON dw.fact_collectes_epargne(date_key);
CREATE INDEX IF NOT EXISTS idx_fact_ce_client   ON dw.fact_collectes_epargne(client_key);
CREATE INDEX IF NOT EXISTS idx_fact_ce_agent    ON dw.fact_collectes_epargne(agent_key);
CREATE INDEX IF NOT EXISTS idx_fact_ce_agence   ON dw.fact_collectes_epargne(agence_key);
CREATE INDEX IF NOT EXISTS idx_fact_ce_statut   ON dw.fact_collectes_epargne(statut);
COMMENT ON TABLE dw.fact_collectes_epargne IS 'Fait collectes épargne — grain = une transaction de collecte terrain validée';

-- ============================================================
-- FAITS — Créances / Portefeuille
-- ============================================================

CREATE TABLE IF NOT EXISTS dw.fact_creances (
    creance_key             BIGSERIAL PRIMARY KEY,
    -- Clés dimensions
    date_key                INTEGER REFERENCES dw.dim_date(date_key),   -- date snapshot
    client_key              BIGINT  REFERENCES dw.dim_client(client_key),
    agent_key               BIGINT  REFERENCES dw.dim_agent(agent_key),
    agence_key              BIGINT  REFERENCES dw.dim_agence(agence_key),
    -- Mesures portefeuille
    montant_initial         NUMERIC(15,2) NOT NULL,
    montant_impaye          NUMERIC(15,2) NOT NULL,
    capital_restant_du      NUMERIC(15,2),
    interets_retard         NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_provision       NUMERIC(15,2) NOT NULL DEFAULT 0,
    jours_retard            INTEGER NOT NULL DEFAULT 0,
    -- Classification
    categorie_par           TEXT NOT NULL,
    classe_risque_cobac     TEXT,
    taux_provision_cobac    NUMERIC(5,2),
    statut_creance          TEXT NOT NULL,
    -- Score ML
    score_mcrs              NUMERIC(5,4),
    classe_risque_ml        TEXT,
    -- Traçabilité
    id_pret_externe         TEXT NOT NULL,
    id_source_app           BIGINT,                         -- FK vers app.creances
    _dbt_updated_at         TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fact_cr_date     ON dw.fact_creances(date_key);
CREATE INDEX IF NOT EXISTS idx_fact_cr_client   ON dw.fact_creances(client_key);
CREATE INDEX IF NOT EXISTS idx_fact_cr_par      ON dw.fact_creances(categorie_par);
CREATE INDEX IF NOT EXISTS idx_fact_cr_statut   ON dw.fact_creances(statut_creance);
COMMENT ON TABLE dw.fact_creances IS 'Fait créances — snapshot journalier du portefeuille à risque, PAR, provisions COBAC';

-- ============================================================
-- FAITS — Actions de recouvrement
-- ============================================================

CREATE TABLE IF NOT EXISTS dw.fact_actions_recouvrement (
    action_key          BIGSERIAL PRIMARY KEY,
    date_key            INTEGER REFERENCES dw.dim_date(date_key),
    client_key          BIGINT  REFERENCES dw.dim_client(client_key),
    agent_key           BIGINT  REFERENCES dw.dim_agent(agent_key),
    agence_key          BIGINT  REFERENCES dw.dim_agence(agence_key),
    -- Mesures
    type_action         TEXT NOT NULL,
    resultat            TEXT,
    montant_recouvre    NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_promis      NUMERIC(15,2),
    date_promesse       DATE,
    frais_engages       NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Phase recouvrement
    phase_dossier       TEXT NOT NULL,
    -- Traçabilité
    dossier_id_source   BIGINT,
    action_id_source    BIGINT,
    _dbt_updated_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fact_ar_date     ON dw.fact_actions_recouvrement(date_key);
CREATE INDEX IF NOT EXISTS idx_fact_ar_type     ON dw.fact_actions_recouvrement(type_action);
CREATE INDEX IF NOT EXISTS idx_fact_ar_resultat ON dw.fact_actions_recouvrement(resultat);
COMMENT ON TABLE dw.fact_actions_recouvrement IS 'Fait actions recouvrement — grain = une action terrain avec résultat et montant récupéré';

-- ============================================================
-- FAITS — Prix produits (série temporelle)
-- ============================================================

CREATE TABLE IF NOT EXISTS dw.fact_prix_produits (
    prix_key            BIGSERIAL PRIMARY KEY,
    date_key            INTEGER REFERENCES dw.dim_date(date_key),
    produit_key         BIGINT  REFERENCES dw.dim_produit_generique(produit_key),
    zone_id             TEXT NOT NULL,
    prix_unitaire       NUMERIC(12,4) NOT NULL,
    prix_min            NUMERIC(12,4),
    prix_max            NUMERIC(12,4),
    unite_mesure        TEXT NOT NULL,
    source              TEXT NOT NULL,
    fiabilite           SMALLINT,
    _dbt_updated_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fact_pp_produit_date ON dw.fact_prix_produits(produit_key, date_key);
CREATE INDEX IF NOT EXISTS idx_fact_pp_zone         ON dw.fact_prix_produits(zone_id, date_key);
COMMENT ON TABLE dw.fact_prix_produits IS 'Fait prix produits — séries temporelles marché pour features ML et analyses macro';

-- ============================================================
-- VUES REPORTING
-- ============================================================

CREATE SCHEMA IF NOT EXISTS reporting;

CREATE OR REPLACE VIEW reporting.v_par_par_agence AS
SELECT
    dd.date_valeur,
    da.code_agence,
    da.nom_agence,
    da.region,
    SUM(fc.montant_impaye) FILTER (WHERE fc.categorie_par IN ('PAR30','PAR60','PAR90','PAR180','PERTE')) AS encours_par30,
    SUM(fc.montant_impaye) FILTER (WHERE fc.categorie_par IN ('PAR90','PAR180','PERTE'))                 AS encours_par90,
    SUM(fc.montant_initial)                                                                               AS encours_total,
    COUNT(*) FILTER (WHERE fc.statut_creance = 'ACTIVE')                                                 AS nb_creances_actives,
    ROUND(SUM(fc.montant_impaye) FILTER (WHERE fc.categorie_par IN ('PAR30','PAR60','PAR90','PAR180','PERTE'))
          / NULLIF(SUM(fc.montant_initial), 0) * 100, 2)                                                 AS taux_par30_pct
FROM dw.fact_creances fc
JOIN dw.dim_date   dd ON fc.date_key  = dd.date_key
JOIN dw.dim_agence da ON fc.agence_key = da.agence_key
GROUP BY dd.date_valeur, da.code_agence, da.nom_agence, da.region;

COMMENT ON VIEW reporting.v_par_par_agence IS 'PAR 30/90 par agence et par date — dashboard directeur';

CREATE OR REPLACE VIEW reporting.v_collectes_agent_semaine AS
SELECT
    dd.annee,
    dd.semaine_iso,
    dg.username,
    dg.nom_complet,
    da.nom_agence,
    COUNT(*)                    AS nb_collectes,
    SUM(fc.montant_collecte)    AS montant_total,
    AVG(fc.montant_collecte)    AS montant_moyen,
    COUNT(DISTINCT fc.client_key) AS nb_clients_uniques,
    SUM(fc.montant_collecte) FILTER (WHERE fc.canal_paiement = 'ESPECES')  AS montant_especes,
    SUM(fc.montant_collecte) FILTER (WHERE fc.canal_paiement = 'MTN')      AS montant_mtn,
    SUM(fc.montant_collecte) FILTER (WHERE fc.canal_paiement = 'ORANGE')   AS montant_orange
FROM dw.fact_collectes_epargne fc
JOIN dw.dim_date  dd ON fc.date_key  = dd.date_key
JOIN dw.dim_agent dg ON fc.agent_key = dg.agent_key
JOIN dw.dim_agence da ON fc.agence_key = da.agence_key
WHERE fc.statut = 'VALIDEE'
GROUP BY dd.annee, dd.semaine_iso, dg.username, dg.nom_complet, da.nom_agence;

COMMENT ON VIEW reporting.v_collectes_agent_semaine IS 'Collectes par agent par semaine — performance terrain avec décomposition canaux';

CREATE OR REPLACE VIEW reporting.v_tendance_prix_produits AS
SELECT
    pg.code_produit,
    pg.nom_produit,
    pg.categorie,
    fp.zone_id,
    dd.date_valeur,
    dd.mois,
    dd.annee,
    fp.prix_unitaire,
    AVG(fp.prix_unitaire) OVER (
        PARTITION BY fp.produit_key, fp.zone_id
        ORDER BY dd.date_valeur
        ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
    ) AS prix_moy_30j,
    AVG(fp.prix_unitaire) OVER (
        PARTITION BY fp.produit_key, fp.zone_id
        ORDER BY dd.date_valeur
        ROWS BETWEEN 89 PRECEDING AND CURRENT ROW
    ) AS prix_moy_90j
FROM dw.fact_prix_produits fp
JOIN dw.dim_date             dd ON fp.date_key   = dd.date_key
JOIN dw.dim_produit_generique pg ON fp.produit_key = pg.produit_key;

COMMENT ON VIEW reporting.v_tendance_prix_produits IS 'Tendances prix produits avec moyennes mobiles 30j et 90j — corrélation collecte';
