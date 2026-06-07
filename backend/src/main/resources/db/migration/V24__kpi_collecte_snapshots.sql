-- ============================================================
-- V24 — KPI snapshots collectes d'épargne
-- Calculés périodiquement par le pipeline DAG
-- ============================================================

CREATE TABLE IF NOT EXISTS app.kpi_collecte_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    imf_id                      BIGINT      NOT NULL REFERENCES app.imf(id),
    agence_id                   BIGINT      REFERENCES app.agences(id),
    cycle_id                    BIGINT      REFERENCES app.cycles_collecte(id),
    agent_id                    BIGINT      REFERENCES app.utilisateurs(id),    -- null = agrégat
    date_calcul                 DATE        NOT NULL,
    periode                     VARCHAR(20) NOT NULL DEFAULT 'HEBDOMADAIRE'
                                CHECK (periode IN ('QUOTIDIEN','HEBDOMADAIRE','MENSUEL','TRIMESTRIEL')),
    -- Volumes collecte
    nb_collectes                INTEGER     NOT NULL DEFAULT 0,
    montant_total               NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_moyen               NUMERIC(12,2),
    nb_clients_uniques          INTEGER     NOT NULL DEFAULT 0,
    -- Taux de réalisation
    objectif_montant            NUMERIC(15,2),
    taux_realisation_pct        NUMERIC(7,4),
    -- Qualité collecte
    taux_ponctualite_pct        NUMERIC(7,4),               -- % collectes dans les délais
    taux_rejet_pct              NUMERIC(7,4),
    nb_doublons_detectes        INTEGER     NOT NULL DEFAULT 0,
    -- Canaux
    montant_especes             NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_mtn                 NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_orange              NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_wave                NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_autres              NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Géolocalisation
    pct_collectes_geolocalisees NUMERIC(5,2),
    -- Métadonnées
    dag_run_id                  TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode)
);

CREATE INDEX IF NOT EXISTS idx_kpi_col_imf_date  ON app.kpi_collecte_snapshots(imf_id, date_calcul);
CREATE INDEX IF NOT EXISTS idx_kpi_col_agence     ON app.kpi_collecte_snapshots(agence_id, date_calcul);
CREATE INDEX IF NOT EXISTS idx_kpi_col_agent      ON app.kpi_collecte_snapshots(agent_id, date_calcul);
COMMENT ON TABLE app.kpi_collecte_snapshots IS 'Snapshots KPI collectes épargne — volumes, taux réalisation, canaux, qualité — par IMF/agence/agent';

-- ── Benchmarks inter-agences ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.benchmarks_agences (
    id                      BIGSERIAL PRIMARY KEY,
    imf_id                  BIGINT NOT NULL REFERENCES app.imf(id),
    agence_id               BIGINT NOT NULL REFERENCES app.agences(id),
    date_calcul             DATE   NOT NULL,
    periode                 VARCHAR(20) NOT NULL DEFAULT 'MENSUEL',
    -- Collecte
    rang_collecte           SMALLINT,
    score_collecte_zscore   NUMERIC(8,4),                   -- z-score vs autres agences
    -- Recouvrement
    rang_recouvrement       SMALLINT,
    score_recouvrement_zscore NUMERIC(8,4),
    -- Global
    rang_global             SMALLINT,
    score_global            NUMERIC(5,4),
    nb_agences_comparees    SMALLINT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_id, agence_id, date_calcul, periode)
);

CREATE INDEX IF NOT EXISTS idx_bench_imf_date ON app.benchmarks_agences(imf_id, date_calcul);
COMMENT ON TABLE app.benchmarks_agences IS 'Benchmarks inter-agences — scores et rangs relatifs pour comparaisons directionnelles';

-- ── Alertes opérationnelles multi-canal ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.alertes_operationnelles (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT      NOT NULL REFERENCES app.imf(id),
    type_alerte         VARCHAR(40) NOT NULL
                        CHECK (type_alerte IN (
                            'OBJECTIF_NON_ATTEINT',
                            'AGENT_INACTIF',
                            'TAUX_REJET_ELEVE',
                            'COLLECTE_ANOMALIE_GPS',
                            'PAR_SEUIL_DEPASSE',
                            'PROMESSE_ECHEANCE',
                            'DOSSIER_SANS_ACTION',
                            'SYNCHRONISATION_RETARD',
                            'PROVISION_INSUFFISANTE'
                        )),
    niveau              VARCHAR(10) NOT NULL DEFAULT 'INFO'
                        CHECK (niveau IN ('INFO','AVERTISSEMENT','CRITIQUE')),
    titre               VARCHAR(200) NOT NULL,
    message             TEXT,
    entite_type         VARCHAR(30),                        -- 'AGENCE','AGENT','CREANCE', etc.
    entite_id           BIGINT,
    valeur_observee     NUMERIC(15,4),
    seuil_configure     NUMERIC(15,4),
    statut              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (statut IN ('ACTIVE','LUES','TRAITEE','IGNOREE')),
    destinataire_role   VARCHAR(30),                        -- DIRECTEUR, RESPONSABLE_RECOUVREMENT, etc.
    fcm_sent            BOOLEAN NOT NULL DEFAULT FALSE,
    sse_sent            BOOLEAN NOT NULL DEFAULT FALSE,
    dag_run_id          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ao_imf_statut    ON app.alertes_operationnelles(imf_id, statut);
CREATE INDEX IF NOT EXISTS idx_ao_niveau        ON app.alertes_operationnelles(imf_id, niveau);
CREATE INDEX IF NOT EXISTS idx_ao_created       ON app.alertes_operationnelles(created_at DESC);
COMMENT ON TABLE app.alertes_operationnelles IS 'Alertes opérationnelles multi-canal — objectifs, anomalies, provisions, inactivité agents';
