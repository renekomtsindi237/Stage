-- ============================================================
-- 02_raw_tables.sql
-- Schéma raw — ingestion brute sans transformation
-- Sources : agents terrain (mobile), fichiers CBS, API externes
-- Principe : append-only, aucune modification après INSERT
-- ============================================================

-- ── Collectes terrain brutes (depuis app Flutter) ───────────────────────────
CREATE TABLE IF NOT EXISTS raw.collectes_terrain (
    id                      BIGSERIAL PRIMARY KEY,
    uuid_mobile             UUID         NOT NULL UNIQUE,
    imf_code                VARCHAR(20)  NOT NULL,
    agence_code             VARCHAR(20),
    agent_username          VARCHAR(50)  NOT NULL,
    client_id_externe       VARCHAR(50)  NOT NULL,
    cycle_ref               VARCHAR(50),
    montant_collecte        TEXT,                           -- brut, typage en staging
    date_collecte           TEXT,
    heure_collecte          TEXT,
    canal_paiement          TEXT,
    reference_transaction   TEXT,
    latitude                TEXT,
    longitude               TEXT,
    precision_gps_metres    TEXT,
    observation             TEXT,
    payload_json            JSONB,                          -- payload complet reçu
    statut_ingestion        VARCHAR(20) NOT NULL DEFAULT 'RECU'
                            CHECK (statut_ingestion IN ('RECU','VALIDE','REJETE','DOUBLON')),
    erreur_validation       TEXT,
    hash_sha256             VARCHAR(64) NOT NULL UNIQUE,
    recu_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    traite_at               TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_raw_ct_imf       ON raw.collectes_terrain(imf_code, date_collecte);
CREATE INDEX IF NOT EXISTS idx_raw_ct_agent     ON raw.collectes_terrain(agent_username);
CREATE INDEX IF NOT EXISTS idx_raw_ct_statut    ON raw.collectes_terrain(statut_ingestion);
COMMENT ON TABLE raw.collectes_terrain IS 'Collectes terrain brutes reçues depuis app Flutter — append-only avant validation';

-- ── Export CBS (portefeuille prêts / remboursements) ────────────────────────
CREATE TABLE IF NOT EXISTS raw.export_cbs (
    id                      BIGSERIAL PRIMARY KEY,
    imf_code                VARCHAR(20) NOT NULL,
    id_pret                 TEXT,
    id_client               TEXT,
    nom_client              TEXT,
    telephone_client        TEXT,
    agence_code             TEXT,
    produit_code            TEXT,
    montant_pret            TEXT,
    montant_rembourse       TEXT,
    solde_restant           TEXT,
    date_deblocage          TEXT,
    date_echeance           TEXT,
    date_derniere_echeance_impayee TEXT,
    montant_impaye          TEXT,
    jours_retard            TEXT,
    statut_pret             TEXT,
    type_garantie           TEXT,
    valeur_garantie         TEXT,
    nom_caution             TEXT,
    telephone_caution       TEXT,
    agent_cbs_code          TEXT,
    fichier_source          VARCHAR(200),
    hash_sha256             VARCHAR(64) NOT NULL,
    statut_ingestion        VARCHAR(20) NOT NULL DEFAULT 'BRUT'
                            CHECK (statut_ingestion IN ('BRUT','VALIDE','REJETE')),
    erreur_validation       TEXT,
    recu_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_code, hash_sha256)
);

CREATE INDEX IF NOT EXISTS idx_raw_cbs_imf_pret  ON raw.export_cbs(imf_code, id_pret);
CREATE INDEX IF NOT EXISTS idx_raw_cbs_client     ON raw.export_cbs(imf_code, id_client);
CREATE INDEX IF NOT EXISTS idx_raw_cbs_recu       ON raw.export_cbs(recu_at);
COMMENT ON TABLE raw.export_cbs IS 'Export CBS brut — portefeuille prêts, remboursements, retards par IMF';

-- ── Prix produits marché (terrain + scraping + APIs) ────────────────────────
CREATE TABLE IF NOT EXISTS raw.prix_marche (
    id                      BIGSERIAL PRIMARY KEY,
    source_type             VARCHAR(20) NOT NULL
                            CHECK (source_type IN (
                                'AGENT_TERRAIN','SCRAPING_WEB',
                                'API_MINCOMMERCE','API_ANSP','FICHIER_FAO'
                            )),
    code_produit_source     TEXT,
    nom_produit_source      TEXT,
    zone_id                 TEXT,
    marche_nom              TEXT,
    date_prix               TEXT,
    prix_unitaire           TEXT,
    unite_mesure            TEXT,
    prix_min                TEXT,
    prix_max                TEXT,
    collecteur_username     TEXT,
    url_source              TEXT,
    payload_json            JSONB,
    hash_sha256             VARCHAR(64) NOT NULL,
    statut_ingestion        VARCHAR(20) NOT NULL DEFAULT 'BRUT'
                            CHECK (statut_ingestion IN ('BRUT','VALIDE','MAPPE','REJETE')),
    produit_mappe_code      TEXT,
    erreur                  TEXT,
    recu_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_type, hash_sha256)
);

CREATE INDEX IF NOT EXISTS idx_raw_pm_produit_date ON raw.prix_marche(code_produit_source, date_prix);
CREATE INDEX IF NOT EXISTS idx_raw_pm_zone_date    ON raw.prix_marche(zone_id, date_prix);
COMMENT ON TABLE raw.prix_marche IS 'Prix marché bruts — toutes sources (terrain, MINCOMMERCE, scraping) avant mapping';

-- ── Données météo brutes ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS raw.donnees_meteo (
    id                  BIGSERIAL PRIMARY KEY,
    source              VARCHAR(30) NOT NULL,
    zone_id             TEXT,
    station_nom         TEXT,
    date_observation    TEXT,
    temperature_min     TEXT,
    temperature_max     TEXT,
    temperature_moy     TEXT,
    precipitation_mm    TEXT,
    humidite_pct        TEXT,
    vitesse_vent_ms     TEXT,
    payload_json        JSONB,
    hash_sha256         VARCHAR(64) NOT NULL,
    statut_ingestion    VARCHAR(20) NOT NULL DEFAULT 'BRUT',
    recu_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source, zone_id, date_observation)
);

CREATE INDEX IF NOT EXISTS idx_raw_meteo_zone ON raw.donnees_meteo(zone_id, date_observation);
COMMENT ON TABLE raw.donnees_meteo IS 'Données météo brutes (MétéoCam, NASA POWER, Open-Meteo)';

-- ── Indicateurs macro bruts (BEAC, INS, FMI) ────────────────────────────────
CREATE TABLE IF NOT EXISTS raw.indicateurs_macro (
    id                  BIGSERIAL PRIMARY KEY,
    source              VARCHAR(30) NOT NULL,
    indicateur_source   TEXT NOT NULL,
    valeur              TEXT,
    unite               TEXT,
    date_observation    TEXT,
    periode             TEXT,
    payload_json        JSONB,
    hash_sha256         VARCHAR(64) NOT NULL,
    statut_ingestion    VARCHAR(20) NOT NULL DEFAULT 'BRUT',
    indicateur_mappe    VARCHAR(50),
    recu_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source, indicateur_source, date_observation)
);

CREATE INDEX IF NOT EXISTS idx_raw_macro_date ON raw.indicateurs_macro(indicateur_source, date_observation);
COMMENT ON TABLE raw.indicateurs_macro IS 'Indicateurs macro bruts (BEAC, INS, FMI) avant normalisation et mapping';

-- ── Journal des ingestions (audit pipeline) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS raw.journal_ingestions (
    id                  BIGSERIAL PRIMARY KEY,
    dag_id              TEXT NOT NULL,
    dag_run_id          TEXT NOT NULL,
    task_id             TEXT,
    table_cible         TEXT NOT NULL,
    nb_lignes_recues    INTEGER NOT NULL DEFAULT 0,
    nb_lignes_valides   INTEGER NOT NULL DEFAULT 0,
    nb_lignes_rejetees  INTEGER NOT NULL DEFAULT 0,
    nb_doublons         INTEGER NOT NULL DEFAULT 0,
    statut              TEXT NOT NULL DEFAULT 'EN_COURS'
                        CHECK (statut IN ('EN_COURS','SUCCES','ECHEC','PARTIEL')),
    message_erreur      TEXT,
    debut_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fin_at              TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ji_dag_run ON raw.journal_ingestions(dag_id, dag_run_id);
CREATE INDEX IF NOT EXISTS idx_ji_debut   ON raw.journal_ingestions(debut_at DESC);
COMMENT ON TABLE raw.journal_ingestions IS 'Journal de toutes les ingestions du pipeline — audit, monitoring, qualité données';
