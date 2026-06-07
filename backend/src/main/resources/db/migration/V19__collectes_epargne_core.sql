-- ============================================================
-- V19 — Collectes d'épargne terrain (domaine central)
-- Cycles configurables + collectes par agent + objectifs
-- ============================================================

-- ── Cycles de collecte (configurables par IMF/agence) ──────────────────────
CREATE TABLE IF NOT EXISTS app.cycles_collecte (
    id                      BIGSERIAL PRIMARY KEY,
    imf_id                  BIGINT NOT NULL REFERENCES app.imf(id),
    agence_id               BIGINT REFERENCES app.agences(id),
    nom_cycle               VARCHAR(100) NOT NULL,
    periodicite             VARCHAR(20)  NOT NULL DEFAULT 'HEBDOMADAIRE'
                            CHECK (periodicite IN ('QUOTIDIEN','HEBDOMADAIRE','BIMENSUEL','MENSUEL','TRIMESTRIEL','LIBRE')),
    date_debut              DATE NOT NULL,
    date_fin                DATE,
    objectif_montant        NUMERIC(15,2),
    objectif_nb_transactions INTEGER,
    description             TEXT,
    actif                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cycle_imf        ON app.cycles_collecte(imf_id);
CREATE INDEX IF NOT EXISTS idx_cycle_agence      ON app.cycles_collecte(agence_id);
CREATE INDEX IF NOT EXISTS idx_cycle_actif       ON app.cycles_collecte(imf_id, actif);
COMMENT ON TABLE app.cycles_collecte IS 'Cycles de collecte d''épargne configurables par IMF et agence';

-- ── Collectes d'épargne terrain ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.collectes_epargne (
    id                      BIGSERIAL PRIMARY KEY,
    uuid_mobile             UUID         NOT NULL UNIQUE,   -- déduplication offline-first
    imf_id                  BIGINT       NOT NULL REFERENCES app.imf(id),
    agence_id               BIGINT       REFERENCES app.agences(id),
    cycle_id                BIGINT       REFERENCES app.cycles_collecte(id),
    agent_id                BIGINT       NOT NULL REFERENCES app.utilisateurs(id),
    client_id_externe       VARCHAR(50)  NOT NULL,          -- référence CBS externe
    montant_collecte        NUMERIC(15,2) NOT NULL CHECK (montant_collecte > 0),
    date_collecte           DATE         NOT NULL,
    heure_collecte          TIME,
    canal_paiement          VARCHAR(20)  NOT NULL
                            CHECK (canal_paiement IN ('ESPECES','MTN','ORANGE','WAVE','VIREMENT','CHEQUE')),
    reference_transaction   VARCHAR(100),
    latitude                NUMERIC(10,7),
    longitude               NUMERIC(10,7),
    precision_gps_metres    NUMERIC(6,1),
    statut                  VARCHAR(20)  NOT NULL DEFAULT 'SOUMISE'
                            CHECK (statut IN ('SOUMISE','VALIDEE','DOUBLON','REJETEE','EN_ATTENTE')),
    motif_rejet             TEXT,
    observation             TEXT,
    synced_at               TIMESTAMPTZ,                    -- null = en attente sync
    validated_by_id         BIGINT REFERENCES app.utilisateurs(id),
    validated_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ce_imf_date      ON app.collectes_epargne(imf_id, date_collecte);
CREATE INDEX IF NOT EXISTS idx_ce_agent_date    ON app.collectes_epargne(agent_id, date_collecte);
CREATE INDEX IF NOT EXISTS idx_ce_client        ON app.collectes_epargne(imf_id, client_id_externe);
CREATE INDEX IF NOT EXISTS idx_ce_cycle         ON app.collectes_epargne(cycle_id);
CREATE INDEX IF NOT EXISTS idx_ce_statut        ON app.collectes_epargne(imf_id, statut);
CREATE INDEX IF NOT EXISTS idx_ce_sync          ON app.collectes_epargne(synced_at) WHERE synced_at IS NULL;
COMMENT ON TABLE app.collectes_epargne IS 'Collectes d''épargne terrain saisies par agents (offline-first, déduplication UUID)';

-- ── Objectifs de collecte par agent/cycle ──────────────────────────────────
CREATE TABLE IF NOT EXISTS app.objectifs_collecte (
    id                          BIGSERIAL PRIMARY KEY,
    cycle_id                    BIGINT NOT NULL REFERENCES app.cycles_collecte(id),
    agent_id                    BIGINT NOT NULL REFERENCES app.utilisateurs(id),
    agence_id                   BIGINT REFERENCES app.agences(id),
    objectif_montant            NUMERIC(15,2),
    objectif_nb_transactions    INTEGER,
    realise_montant             NUMERIC(15,2) NOT NULL DEFAULT 0,
    realise_nb_transactions     INTEGER       NOT NULL DEFAULT 0,
    taux_realisation_montant    NUMERIC(5,2)  GENERATED ALWAYS AS (
        CASE WHEN objectif_montant > 0
             THEN ROUND(realise_montant / objectif_montant * 100, 2)
             ELSE NULL END
    ) STORED,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cycle_id, agent_id)
);

CREATE INDEX IF NOT EXISTS idx_obj_cycle    ON app.objectifs_collecte(cycle_id);
CREATE INDEX IF NOT EXISTS idx_obj_agent    ON app.objectifs_collecte(agent_id);
COMMENT ON TABLE app.objectifs_collecte IS 'Objectifs de collecte par agent et par cycle, avec suivi du réalisé';
