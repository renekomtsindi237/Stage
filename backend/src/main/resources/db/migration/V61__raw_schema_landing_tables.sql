-- ============================================================
-- V61 — Schéma raw (zone d'atterrissage) + tables export_cbs / collectes_terrain
--
-- Le schéma raw était référencé par pipeline/dbt_project/models/staging/
-- schema.yml et par stg_clients.sql/stg_creances.sql/stg_collectes_epargne.sql
-- depuis l'origine du projet dbt, mais n'avait jamais été créé — aucune
-- ingestion réelle ne l'alimentait, les données arrivant directement dans
-- app.* via l'API Spring Boot. Ce n'est PAS un export CBS/mobile externe :
-- ces deux tables sont alimentées par un job de synchronisation
-- (pipeline/dags/scripts/raw_sync_utils.py, dag_raw_sync) qui recopie
-- app.creances/app.clients_informels et app.collectes_terrain dans la
-- forme texte brute que les modèles dbt staging attendent — même
-- convention "raw = non typé, staging = nettoyé/typé" qu'un vrai
-- atterrissage externe, appliquée à une source interne faute de CBS/mobile
-- money externe réellement connecté (raw.prix_marche/transactions_mtn/
-- transactions_orange restent hors périmètre : aucune source, ni raw ni
-- app, n'existe pour ces trois-là).
-- ============================================================

CREATE SCHEMA IF NOT EXISTS raw;

-- ── Miroir CBS (clients + créances), dérivé de app.clients_informels/app.creances ──
CREATE TABLE IF NOT EXISTS raw.export_cbs (
    id                              BIGSERIAL PRIMARY KEY,
    imf_code                        TEXT,
    id_pret                         TEXT,
    id_client                       TEXT,
    nom_client                      TEXT,
    telephone_client                TEXT,
    agence_code                     TEXT,
    nom_agence                      TEXT,
    produit_code                    TEXT,
    agent_cbs_code                  TEXT,
    montant_pret                    TEXT,
    montant_rembourse               TEXT,
    solde_restant                   TEXT,
    montant_impaye                  TEXT,
    date_deblocage                  TEXT,
    date_echeance                   TEXT,
    date_derniere_echeance_impayee  TEXT,
    jours_retard                    TEXT,
    statut_pret                     TEXT,
    type_garantie                   TEXT,
    valeur_garantie                 TEXT,
    nom_caution                     TEXT,
    date_ingestion                  TEXT,
    statut_ingestion                VARCHAR(20) NOT NULL DEFAULT 'BRUT',
    recu_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_code, id_pret)
);

CREATE INDEX IF NOT EXISTS idx_export_cbs_recu_at ON raw.export_cbs(recu_at);
COMMENT ON TABLE raw.export_cbs IS
    'Miroir texte brut de app.creances/app.clients_informels — alimenté par dag_raw_sync, pas un export CBS externe réel';

-- ── Miroir collectes terrain, dérivé de app.collectes_terrain ──────────────
CREATE TABLE IF NOT EXISTS raw.collectes_terrain (
    id                      BIGSERIAL PRIMARY KEY,
    uuid_mobile             TEXT NOT NULL,
    imf_code                TEXT,
    agence_code             TEXT,
    agent_username          TEXT,
    client_id_externe       TEXT,
    cycle_ref               TEXT,
    montant_collecte        TEXT,
    date_collecte           TEXT,
    heure_collecte          TEXT,
    canal_paiement          TEXT,
    reference_transaction   TEXT,
    latitude                TEXT,
    longitude               TEXT,
    precision_gps_metres    TEXT,
    observation             TEXT,
    hash_sha256             TEXT NOT NULL,
    statut_ingestion        VARCHAR(20) NOT NULL DEFAULT 'RECU',
    recu_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hash_sha256)
);

CREATE INDEX IF NOT EXISTS idx_collectes_terrain_recu_at ON raw.collectes_terrain(recu_at);
COMMENT ON TABLE raw.collectes_terrain IS
    'Miroir texte brut de app.collectes_terrain — alimenté par dag_raw_sync, pas une synchronisation mobile externe réelle';
