-- ============================================================
-- 03_staging_tables.sql
-- Schéma staging — données nettoyées, typage corrigé, validées
-- Alimenté par dbt run (couche staging)
-- Lues par les couches intermediate, mart et le pipeline de scoring
-- ============================================================

-- ── Collectes épargne nettoyées ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.stg_collectes_epargne (
    id                      BIGSERIAL PRIMARY KEY,
    uuid_mobile             UUID         NOT NULL UNIQUE,
    imf_code                VARCHAR(20)  NOT NULL,
    agence_code             VARCHAR(20),
    agent_username          VARCHAR(50)  NOT NULL,
    client_id_externe       VARCHAR(50)  NOT NULL,
    cycle_ref               VARCHAR(50),
    montant_collecte        NUMERIC(15,2) NOT NULL CHECK (montant_collecte > 0),
    date_collecte           DATE         NOT NULL,
    heure_collecte          TIME,
    canal_paiement          VARCHAR(20)  NOT NULL,
    reference_transaction   VARCHAR(100),
    latitude                NUMERIC(10,7),
    longitude               NUMERIC(10,7),
    precision_gps_metres    NUMERIC(6,1),
    observation             TEXT,
    statut_validation       VARCHAR(20)  NOT NULL DEFAULT 'VALIDE',
    -- Flags qualité dbt
    est_doublon             BOOLEAN NOT NULL DEFAULT FALSE,
    est_hors_zone           BOOLEAN NOT NULL DEFAULT FALSE,
    est_montant_aberrant    BOOLEAN NOT NULL DEFAULT FALSE,
    hash_sha256             VARCHAR(64)  NOT NULL UNIQUE,
    _source_raw_id          BIGINT,
    _dbt_loaded_at          TIMESTAMPTZ DEFAULT NOW(),
    _dbt_updated_at         TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stg_ce_imf_date  ON staging.stg_collectes_epargne(imf_code, date_collecte);
CREATE INDEX IF NOT EXISTS idx_stg_ce_agent     ON staging.stg_collectes_epargne(agent_username, date_collecte);
CREATE INDEX IF NOT EXISTS idx_stg_ce_client    ON staging.stg_collectes_epargne(imf_code, client_id_externe);
COMMENT ON TABLE staging.stg_collectes_epargne IS 'Collectes épargne validées — typage correct, doublons flagués, anomalies GPS marquées';

-- ── Créances nettoyées (depuis export CBS) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.stg_creances (
    id                          BIGSERIAL PRIMARY KEY,
    imf_code                    VARCHAR(20)  NOT NULL,
    id_pret                     TEXT         NOT NULL,
    id_client                   TEXT         NOT NULL,
    nom_client                  TEXT,
    telephone_client            TEXT,
    agence_code                 TEXT,
    produit_code                TEXT,
    montant_initial             NUMERIC(15,2),
    montant_rembourse           NUMERIC(15,2) DEFAULT 0,
    solde_restant               NUMERIC(15,2),
    montant_impaye              NUMERIC(15,2) DEFAULT 0,
    interets_retard             NUMERIC(15,2) DEFAULT 0,
    date_deblocage              DATE,
    date_echeance               DATE,
    date_premiere_echeance_impayee DATE,
    jours_retard                INTEGER       NOT NULL DEFAULT 0,
    statut_pret                 TEXT          NOT NULL,
    -- Classification calculée par dbt
    categorie_par               TEXT          NOT NULL DEFAULT 'COURANT',
    classe_risque_cobac         TEXT,
    taux_provision_cobac        NUMERIC(5,2)  NOT NULL DEFAULT 0,
    montant_provision           NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Garanties
    type_garantie               TEXT,
    valeur_garantie             NUMERIC(15,2),
    nom_caution                 TEXT,
    -- Agents CBS
    agent_cbs_code              TEXT,
    -- Qualité
    est_donnee_incomplete       BOOLEAN NOT NULL DEFAULT FALSE,
    _source_raw_id              BIGINT,
    _dbt_loaded_at              TIMESTAMPTZ DEFAULT NOW(),
    _dbt_updated_at             TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (imf_code, id_pret)
);

CREATE INDEX IF NOT EXISTS idx_stg_cr_imf_par     ON staging.stg_creances(imf_code, categorie_par);
CREATE INDEX IF NOT EXISTS idx_stg_cr_client      ON staging.stg_creances(imf_code, id_client);
CREATE INDEX IF NOT EXISTS idx_stg_cr_retard      ON staging.stg_creances(jours_retard);
COMMENT ON TABLE staging.stg_creances IS 'Créances CBS nettoyées — PAR calculé, provisions COBAC, doublons résolus';

-- ── Clients informels nettoyés ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.stg_clients (
    id                          BIGSERIAL PRIMARY KEY,
    imf_code                    VARCHAR(20) NOT NULL,
    client_id_externe           TEXT        NOT NULL,
    nom_complet                 TEXT,
    telephone_principal         TEXT,
    zone_id                     TEXT,
    agence_code                 TEXT,
    secteur_principal           TEXT,
    revenu_mensuel_estime       NUMERIC(12,2),
    latitude_activite           NUMERIC(10,7),
    longitude_activite          NUMERIC(10,7),
    -- Ancienneté calculée
    date_premiere_collecte      DATE,
    date_premier_pret           DATE,
    anciennete_jours            INTEGER,
    -- Agrégats comportementaux (alimentés par dbt intermediate)
    nb_collectes_total          INTEGER    NOT NULL DEFAULT 0,
    montant_total_collectes     NUMERIC(15,2) NOT NULL DEFAULT 0,
    nb_prets_total              INTEGER    NOT NULL DEFAULT 0,
    taux_remboursement_historique NUMERIC(5,4),
    _dbt_loaded_at              TIMESTAMPTZ DEFAULT NOW(),
    _dbt_updated_at             TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (imf_code, client_id_externe)
);

CREATE INDEX IF NOT EXISTS idx_stg_cl_imf_zone ON staging.stg_clients(imf_code, zone_id);
COMMENT ON TABLE staging.stg_clients IS 'Clients nettoyés avec profil informel et agrégats comportementaux';

-- ── Prix produits nettoyés ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.stg_prix_produits (
    id                  BIGSERIAL PRIMARY KEY,
    code_produit        TEXT NOT NULL,
    nom_produit         TEXT NOT NULL,
    categorie           TEXT,
    zone_id             TEXT NOT NULL,
    date_prix           DATE NOT NULL,
    prix_unitaire       NUMERIC(12,4) NOT NULL CHECK (prix_unitaire > 0),
    unite_mesure        TEXT NOT NULL,
    prix_min            NUMERIC(12,4),
    prix_max            NUMERIC(12,4),
    source              TEXT NOT NULL,
    fiabilite           SMALLINT DEFAULT 3,
    -- Features calculées par dbt
    prix_moy_7j         NUMERIC(12,4),
    prix_moy_30j        NUMERIC(12,4),
    variation_7j_pct    NUMERIC(8,4),
    variation_30j_pct   NUMERIC(8,4),
    est_valeur_aberrante BOOLEAN NOT NULL DEFAULT FALSE,
    _source_raw_id      BIGINT,
    _dbt_loaded_at      TIMESTAMPTZ DEFAULT NOW(),
    _dbt_updated_at     TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (code_produit, zone_id, date_prix, source)
);

CREATE INDEX IF NOT EXISTS idx_stg_pp_produit_date ON staging.stg_prix_produits(code_produit, date_prix);
CREATE INDEX IF NOT EXISTS idx_stg_pp_zone_date    ON staging.stg_prix_produits(zone_id, date_prix);
COMMENT ON TABLE staging.stg_prix_produits IS 'Prix produits nettoyés avec moyennes mobiles et détection aberrations';

-- ── Indicateurs macro nettoyés ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.stg_indicateurs_macro (
    id                  BIGSERIAL PRIMARY KEY,
    indicateur          TEXT NOT NULL,
    valeur              NUMERIC(18,6) NOT NULL,
    date_observation    DATE NOT NULL,
    periode             TEXT NOT NULL DEFAULT 'MENSUEL',
    source              TEXT NOT NULL,
    -- Dérivés
    variation_precedent NUMERIC(10,6),
    tendance            TEXT CHECK (tendance IN ('HAUSSE','BAISSE','STABLE')),
    _dbt_loaded_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (indicateur, date_observation)
);

CREATE INDEX IF NOT EXISTS idx_stg_macro_indicateur ON staging.stg_indicateurs_macro(indicateur, date_observation);
COMMENT ON TABLE staging.stg_indicateurs_macro IS 'Indicateurs macro normalisés avec variation et tendance';

-- ── Météo nettoyée ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.stg_meteo (
    id                  BIGSERIAL PRIMARY KEY,
    zone_id             TEXT NOT NULL,
    date_observation    DATE NOT NULL,
    temperature_moy     NUMERIC(5,2),
    precipitation_mm    NUMERIC(8,2) DEFAULT 0,
    humidite_pct        NUMERIC(5,2),
    indice_secheresse   TEXT NOT NULL DEFAULT 'NORMAL',
    -- Dérivés
    precipitation_cumul_30j NUMERIC(10,2),
    est_anomalie_meteo  BOOLEAN NOT NULL DEFAULT FALSE,
    _dbt_loaded_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (zone_id, date_observation)
);

CREATE INDEX IF NOT EXISTS idx_stg_meteo_zone ON staging.stg_meteo(zone_id, date_observation);
COMMENT ON TABLE staging.stg_meteo IS 'Météo nettoyée avec cumuls, indices sécheresse et détection anomalies';

-- ── Alertes impayés (générées par le pipeline) ──────────────────────────────
CREATE TABLE IF NOT EXISTS staging.alertes_impayes (
    id                  BIGSERIAL PRIMARY KEY,
    imf_code            TEXT NOT NULL,
    id_pret             TEXT NOT NULL,
    id_client           TEXT,
    nom_client          TEXT,
    telephone_client    TEXT,
    agence_code         TEXT,
    agent_cbs_code      TEXT,
    montant_impaye      NUMERIC(15,2),
    jours_retard        INTEGER,
    categorie_par       TEXT NOT NULL,
    score_mcrs          NUMERIC(5,4),
    classe_risque       TEXT,
    action_recommandee  TEXT,
    statut              TEXT NOT NULL DEFAULT 'ACTIVE'
                        CHECK (statut IN ('ACTIVE','RESOLUE','ESCALADEE','PERTE')),
    dag_run_id          TEXT,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_alerte_pret_actif UNIQUE (imf_code, id_pret, statut)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_alerte_imf_statut  ON staging.alertes_impayes(imf_code, statut);
CREATE INDEX IF NOT EXISTS idx_alerte_par         ON staging.alertes_impayes(categorie_par);
COMMENT ON TABLE staging.alertes_impayes IS 'Alertes impayés avec scoring MCRS et recommandation d''action';
