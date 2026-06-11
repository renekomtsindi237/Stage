-- ============================================================
-- V35 : Workflow d'octroi de crédit
--
-- Tables créées :
--   app.dossiers_credit     — dossier complet d'instruction (agent → comité → décision)
--   app.garanties_credit    — garanties attachées à un dossier (aval, hypothèque, etc.)
--   app.comite_decisions    — séances du comité de crédit (agence / siège / grand comité)
--   app.votes_comite        — votes individuels des membres du comité
--   app.visites_conformite  — visite terrain J+15 post-déblocage par l'agent de crédit
-- ============================================================

-- ── app.dossiers_credit ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.dossiers_credit (
    id                      BIGSERIAL    NOT NULL,
    uid                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    imf_id                  BIGINT       NOT NULL,
    agence_id               BIGINT,
    agent_credit_id         BIGINT       NOT NULL,
    client_id               VARCHAR(100) NOT NULL,
    client_nom              VARCHAR(200),
    montant_demande         NUMERIC(15,2) NOT NULL,
    duree_mois              SMALLINT     NOT NULL,
    objet_financement       VARCHAR(300) NOT NULL,
    secteur_activite        VARCHAR(100),
    revenu_estime           NUMERIC(15,2),
    charges_mensuelles      NUMERIC(15,2),
    capacite_remboursement  NUMERIC(15,2),
    statut                  VARCHAR(20)  NOT NULL DEFAULT 'INSTRUCTION',
    note_analyse            TEXT,
    date_soumission         TIMESTAMPTZ,
    date_decision           TIMESTAMPTZ,
    chef_agence_id          BIGINT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_dossiers_credit PRIMARY KEY (id),
    CONSTRAINT uq_dossiers_credit_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_dossiers_credit_imf_id
    ON app.dossiers_credit (imf_id);
CREATE INDEX IF NOT EXISTS idx_dossiers_credit_agent_credit_id
    ON app.dossiers_credit (agent_credit_id);
CREATE INDEX IF NOT EXISTS idx_dossiers_credit_statut
    ON app.dossiers_credit (statut);
CREATE INDEX IF NOT EXISTS idx_dossiers_credit_imf_statut
    ON app.dossiers_credit (imf_id, statut);

-- ── app.garanties_credit ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.garanties_credit (
    id                  BIGSERIAL    NOT NULL,
    uid                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    dossier_id          BIGINT       NOT NULL REFERENCES app.dossiers_credit(id) ON DELETE CASCADE,
    type                VARCHAR(40)  NOT NULL,
    description         VARCHAR(300) NOT NULL,
    valeur_estimee      NUMERIC(15,2),
    reference_document  VARCHAR(200),
    caution_nom         VARCHAR(200),
    caution_telephone   VARCHAR(30),
    statut              VARCHAR(20)  NOT NULL DEFAULT 'COLLECTEE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_garanties_credit PRIMARY KEY (id),
    CONSTRAINT uq_garanties_credit_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_garanties_credit_dossier_id
    ON app.garanties_credit (dossier_id);

-- ── app.comite_decisions ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.comite_decisions (
    id               BIGSERIAL    NOT NULL,
    uid              UUID         NOT NULL DEFAULT gen_random_uuid(),
    dossier_id       BIGINT       NOT NULL REFERENCES app.dossiers_credit(id) ON DELETE CASCADE,
    type_comite      VARCHAR(20)  NOT NULL,
    president_id     BIGINT       NOT NULL,
    date_seance      TIMESTAMPTZ,
    decision         VARCHAR(20),
    montant_approuve NUMERIC(15,2),
    taux_approuve    NUMERIC(6,4),
    duree_approuvee  SMALLINT,
    conditions       TEXT,
    quorum_atteint   BOOLEAN      NOT NULL DEFAULT FALSE,
    motif_rejet      VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_comite_decisions PRIMARY KEY (id),
    CONSTRAINT uq_comite_decisions_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_comite_decisions_dossier_id
    ON app.comite_decisions (dossier_id);

-- ── app.votes_comite ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.votes_comite (
    id          BIGSERIAL   NOT NULL,
    comite_id   BIGINT      NOT NULL REFERENCES app.comite_decisions(id) ON DELETE CASCADE,
    votant_id   BIGINT      NOT NULL,
    role_votant VARCHAR(30) NOT NULL,
    vote        VARCHAR(15) NOT NULL,
    commentaire VARCHAR(500),
    voted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_votes_comite PRIMARY KEY (id),
    CONSTRAINT uq_votes_comite_votant UNIQUE (comite_id, votant_id)
);

CREATE INDEX IF NOT EXISTS idx_votes_comite_comite_id
    ON app.votes_comite (comite_id);

-- ── app.visites_conformite ────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.visites_conformite (
    id                  BIGSERIAL   NOT NULL,
    uid                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    dossier_id          BIGINT      NOT NULL REFERENCES app.dossiers_credit(id) ON DELETE CASCADE,
    agent_credit_id     BIGINT      NOT NULL,
    date_visite         DATE        NOT NULL DEFAULT CURRENT_DATE,
    conformite_observee BOOLEAN     NOT NULL,
    observations        TEXT        NOT NULL,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_visites_conformite PRIMARY KEY (id),
    CONSTRAINT uq_visites_conformite_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_visites_conformite_dossier_id
    ON app.visites_conformite (dossier_id);
