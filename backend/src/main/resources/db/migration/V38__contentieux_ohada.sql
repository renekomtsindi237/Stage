-- ============================================================
-- V38 : Contentieux OHADA
--
--   app.procedures_contentieux   — procédures judiciaires OHADA
--   app.intervenants_judiciaires — huissiers, avocats, commissaires-priseurs
--   app.actions_contentieux      — chronologie des actes de procédure
-- ============================================================

-- ── app.procedures_contentieux ────────────────────────────────
CREATE TABLE IF NOT EXISTS app.procedures_contentieux (
    id                   BIGSERIAL    NOT NULL,
    uid                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    dossier_id           BIGINT       NOT NULL REFERENCES app.dossiers_recouvrement(id),
    type_procedure       VARCHAR(40)  NOT NULL,
    juridiction          VARCHAR(200),
    numero_affaire       VARCHAR(100),
    date_saisine         DATE,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
    responsable_id       BIGINT       NOT NULL,
    montant_reclame      NUMERIC(15,2),
    montant_recouvre     NUMERIC(15,2) NOT NULL DEFAULT 0,
    date_decheance_terme DATE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_procedures_contentieux PRIMARY KEY (id),
    CONSTRAINT uq_procedures_contentieux_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_procedures_contentieux_dossier_id
    ON app.procedures_contentieux (dossier_id);
CREATE INDEX IF NOT EXISTS idx_procedures_contentieux_responsable_id
    ON app.procedures_contentieux (responsable_id);

-- ── app.intervenants_judiciaires ──────────────────────────────
CREATE TABLE IF NOT EXISTS app.intervenants_judiciaires (
    id                BIGSERIAL    NOT NULL,
    procedure_id      BIGINT       NOT NULL REFERENCES app.procedures_contentieux(id) ON DELETE CASCADE,
    type              VARCHAR(30)  NOT NULL,
    nom               VARCHAR(200) NOT NULL,
    reference_mission VARCHAR(100),
    date_mandat       DATE,
    honoraires        NUMERIC(12,2),
    statut_mission    VARCHAR(50),
    observations      VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_intervenants_judiciaires PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_intervenants_judiciaires_procedure_id
    ON app.intervenants_judiciaires (procedure_id);

-- ── app.actions_contentieux ───────────────────────────────────
CREATE TABLE IF NOT EXISTS app.actions_contentieux (
    id                BIGSERIAL    NOT NULL,
    procedure_id      BIGINT       NOT NULL REFERENCES app.procedures_contentieux(id) ON DELETE CASCADE,
    type_action       VARCHAR(30)  NOT NULL,
    date_action       DATE         NOT NULL DEFAULT CURRENT_DATE,
    intervenants      VARCHAR(500),
    resultat          VARCHAR(500),
    montant_recouvre  NUMERIC(15,2),
    pj_url            VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_actions_contentieux PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_actions_contentieux_procedure_id
    ON app.actions_contentieux (procedure_id);
