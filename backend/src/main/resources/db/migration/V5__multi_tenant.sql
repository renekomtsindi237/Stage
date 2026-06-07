-- ============================================================
-- V5__multi_tenant.sql
-- Introduction du multi-tenant : table app.imf + imf_id
-- sur toutes les tables métier + must_change_password
-- ============================================================

-- ------------------------------------------------------------
-- 1. Table des institutions de microfinance (tenants)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.imf (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,   -- ex: 'CAMCCUL', 'MUCCC'
    nom         VARCHAR(100) NOT NULL,
    pays        VARCHAR(50)  NOT NULL DEFAULT 'Cameroun',
    actif       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE app.imf IS 'Institutions de microfinance — un enregistrement = un tenant';

-- ------------------------------------------------------------
-- 2. Mise à jour de la contrainte role pour inclure SUPER_ADMIN
-- ------------------------------------------------------------
ALTER TABLE app.utilisateurs DROP CONSTRAINT IF EXISTS utilisateurs_role_check;
ALTER TABLE app.utilisateurs
    ADD CONSTRAINT utilisateurs_role_check
    CHECK (role IN (
        'SUPER_ADMIN',
        'DIRECTEUR',
        'RESPONSABLE_RECOUVREMENT',
        'ANALYSTE',
        'DSI',
        'AGENT'
    ));

-- ------------------------------------------------------------
-- 3. Colonne imf_id sur app.utilisateurs (nullable : SUPER_ADMIN = NULL)
-- ------------------------------------------------------------
ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS imf_id BIGINT REFERENCES app.imf(id),
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- ------------------------------------------------------------
-- 4. Mise à jour de l'admin seed → SUPER_ADMIN (sans IMF)
-- ------------------------------------------------------------
UPDATE app.utilisateurs
SET    role = 'SUPER_ADMIN'
WHERE  username = 'admin';

-- ------------------------------------------------------------
-- 5. Colonne imf_id sur les tables métier (NOT NULL différé)
--    On ajoute nullable d'abord pour ne pas bloquer les bases
--    existantes qui auraient des données, puis on contraindra
--    au niveau applicatif (NOT NULL uniquement pour non-SUPER_ADMIN)
-- ------------------------------------------------------------
ALTER TABLE app.collectes_terrain  ADD COLUMN IF NOT EXISTS imf_id BIGINT REFERENCES app.imf(id);
ALTER TABLE app.alertes_impayes    ADD COLUMN IF NOT EXISTS imf_id BIGINT REFERENCES app.imf(id);
ALTER TABLE app.echeances_app      ADD COLUMN IF NOT EXISTS imf_id BIGINT REFERENCES app.imf(id);
ALTER TABLE app.sync_logs          ADD COLUMN IF NOT EXISTS imf_id BIGINT REFERENCES app.imf(id);
ALTER TABLE app.journal_audit      ADD COLUMN IF NOT EXISTS imf_id BIGINT REFERENCES app.imf(id);

-- ------------------------------------------------------------
-- 6. Index pour les requêtes filtrées par IMF
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_utilisateurs_imf_id   ON app.utilisateurs      (imf_id);
CREATE INDEX IF NOT EXISTS idx_collectes_imf_id      ON app.collectes_terrain  (imf_id);
CREATE INDEX IF NOT EXISTS idx_alertes_imf_id        ON app.alertes_impayes    (imf_id);
CREATE INDEX IF NOT EXISTS idx_echeances_imf_id      ON app.echeances_app      (imf_id);
CREATE INDEX IF NOT EXISTS idx_sync_logs_imf_id      ON app.sync_logs          (imf_id);
CREATE INDEX IF NOT EXISTS idx_journal_audit_imf_id  ON app.journal_audit      (imf_id);

-- ------------------------------------------------------------
-- 7. Trigger updated_at sur app.imf
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS set_updated_at_imf ON app.imf;
CREATE TRIGGER set_updated_at_imf
    BEFORE UPDATE ON app.imf
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();
