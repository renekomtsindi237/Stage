-- ============================================================
-- V37 : Recouvrement amiable spécialisé
--
-- Enrichissement de app.dossiers_recouvrement :
--   agent_recouvrement_id, date_premiere_relance, nb_relances_amiables, moratoire_propose
--
-- Enrichissement de app.actions_recouvrement :
--   canal, pression_sociale
--
-- Nouvelle table : app.plans_apurement (moratoires formels)
-- ============================================================

-- ── Extension dossiers_recouvrement ──────────────────────────
ALTER TABLE app.dossiers_recouvrement
    ADD COLUMN IF NOT EXISTS agent_recouvrement_id  BIGINT,
    ADD COLUMN IF NOT EXISTS date_premiere_relance   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS nb_relances_amiables    SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS moratoire_propose       BOOLEAN     NOT NULL DEFAULT FALSE;

-- ── Extension actions_recouvrement ───────────────────────────
ALTER TABLE app.actions_recouvrement
    ADD COLUMN IF NOT EXISTS canal            VARCHAR(30),
    ADD COLUMN IF NOT EXISTS pression_sociale BOOLEAN NOT NULL DEFAULT FALSE;

-- ── app.plans_apurement ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.plans_apurement (
    id                   BIGSERIAL    NOT NULL,
    uid                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    dossier_id           BIGINT       NOT NULL REFERENCES app.dossiers_recouvrement(id),
    nb_echeances         SMALLINT     NOT NULL,
    montant_par_echeance NUMERIC(14,2),
    date_debut           DATE,
    signe_client         BOOLEAN      NOT NULL DEFAULT FALSE,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'ACTIF',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_plans_apurement PRIMARY KEY (id),
    CONSTRAINT uq_plans_apurement_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_plans_apurement_dossier_id
    ON app.plans_apurement (dossier_id);
