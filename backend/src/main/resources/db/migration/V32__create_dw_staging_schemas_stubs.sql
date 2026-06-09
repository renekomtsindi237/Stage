-- ============================================================
-- V32 : Schémas DW (dw) et Staging (staging) — tables stubs
--
-- Ces tables sont créées vides au démarrage du backend.
-- Elles sont peuplées par les DAGs Airflow/dbt :
--   dag_collectes       → staging.stg_prets, dw.fact_collectes
--   dag_kpis_quotidien  → dw.fact_remboursements, dimensions
--
-- IF NOT EXISTS protège contre une ré-exécution Flyway
-- ou une création préalable par dbt (materialization: table).
-- ============================================================

CREATE SCHEMA IF NOT EXISTS dw;
CREATE SCHEMA IF NOT EXISTS staging;

-- ── dw.dim_date ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_date (
    date_key    INTEGER     NOT NULL,
    date_valeur DATE        NOT NULL,
    CONSTRAINT pk_dim_date PRIMARY KEY (date_key)
);

-- ── dw.dim_agence ────────────────────────────────────────────
-- agence_key : clé entière utilisée par KpiService
-- id_agence  : clé entière utilisée par ExportService / PdfExportService
-- zone_id / nom_zone : utilisés dans le rapport KPI PDF
CREATE TABLE IF NOT EXISTS dw.dim_agence (
    agence_key  INTEGER      NOT NULL,
    id_agence   INTEGER,
    nom_agence  VARCHAR(200),
    zone_id     VARCHAR(50),
    nom_zone    VARCHAR(200),
    CONSTRAINT pk_dim_agence PRIMARY KEY (agence_key)
);

-- ── dw.dim_client ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dw.dim_client (
    id_client_source VARCHAR(100) NOT NULL,
    nom_client       VARCHAR(200),
    CONSTRAINT pk_dim_client PRIMARY KEY (id_client_source)
);

-- ── dw.fact_collectes ────────────────────────────────────────
-- agence_key       : FK logique vers dim_agence (KpiService)
-- id_agence        : FK logique vers dim_agence (ExportService / PdfExportService)
-- id_client_source : FK logique vers dim_client
CREATE TABLE IF NOT EXISTS dw.fact_collectes (
    date_key              INTEGER,
    agence_key            INTEGER,
    id_agence             INTEGER,
    id_client_source      VARCHAR(100),
    canal                 VARCHAR(50),
    montant               NUMERIC(18, 2),
    reference_transaction VARCHAR(100),
    statut                VARCHAR(50),
    nom_fichier_source    VARCHAR(255)
);

-- ── dw.fact_remboursements ───────────────────────────────────
-- agence_key : FK logique vers dim_agence.agence_key (KpiService)
-- id_agence  : FK logique vers dim_agence.id_agence  (PdfExportService)
CREATE TABLE IF NOT EXISTS dw.fact_remboursements (
    date_key          INTEGER,
    agence_key        INTEGER,
    id_agence         INTEGER,
    montant_attendu   NUMERIC(18, 2),
    montant_rembourse NUMERIC(18, 2),
    solde_restant     NUMERIC(18, 2),
    statut_pret       VARCHAR(50),
    jours_retard      INTEGER DEFAULT 0
);

-- ── staging.stg_prets ────────────────────────────────────────
-- Utilisé par ExportService.exportPretsEnRetardCSV()
-- et PdfExportService.exportPretsEnRetardPDF()
CREATE TABLE IF NOT EXISTS staging.stg_prets (
    id_pret       VARCHAR(100),
    id_client     VARCHAR(100),
    nom_client    VARCHAR(200),
    nom_agence    VARCHAR(200),
    nom_produit   VARCHAR(200),
    montant_pret  NUMERIC(18, 2),
    solde_restant NUMERIC(18, 2),
    statut_pret   VARCHAR(50),
    jours_retard  INTEGER DEFAULT 0
);
