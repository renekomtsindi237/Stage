-- ============================================================
-- V33 : Tables alertes_systeme et tickets_support
--
-- alertes_systeme : incidents infrastructure (CPU, DAG, pod KO...)
--   entité : cm.imf.pipeline.entity.AlerteSysteme
--
-- tickets_support : canal de communication utilisateurs ↔ SUPPORT
--   entité : cm.imf.pipeline.entity.TicketSupport
-- ============================================================

-- ── app.alertes_systeme ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.alertes_systeme (
    id               BIGSERIAL    NOT NULL,
    type             VARCHAR(50)  NOT NULL,
    titre            VARCHAR(200) NOT NULL,
    detail           TEXT         NOT NULL,
    severite         VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    statut           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    source           VARCHAR(100) NOT NULL,
    acquitte_par_id  BIGINT,
    acquitte_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_alertes_systeme PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_alertes_systeme_statut
    ON app.alertes_systeme (statut);

CREATE INDEX IF NOT EXISTS idx_alertes_systeme_severite_statut
    ON app.alertes_systeme (severite, statut);

-- ── app.tickets_support ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.tickets_support (
    id                  BIGSERIAL    NOT NULL,
    uid                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    imf_id              BIGINT,
    auteur_id           BIGINT       NOT NULL,
    auteur_username     VARCHAR(50)  NOT NULL,
    auteur_role         VARCHAR(30)  NOT NULL,
    titre               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    categorie           VARCHAR(50)  NOT NULL,
    priorite            VARCHAR(20)  NOT NULL DEFAULT 'NORMALE',
    statut              VARCHAR(20)  NOT NULL DEFAULT 'OUVERT',
    traite_par_id       BIGINT,
    traite_par_username VARCHAR(50),
    resolution          TEXT,
    date_traitement     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_tickets_support PRIMARY KEY (id),
    CONSTRAINT uq_tickets_support_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_tickets_support_statut
    ON app.tickets_support (statut);

CREATE INDEX IF NOT EXISTS idx_tickets_support_imf_id
    ON app.tickets_support (imf_id);
