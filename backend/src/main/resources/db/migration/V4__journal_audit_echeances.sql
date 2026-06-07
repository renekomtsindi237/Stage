-- ============================================================
-- V3__journal_audit_echeances.sql
-- 1. Journal d'audit des actions utilisateurs (RGPD / traçabilité)
-- 2. Table des échéances applicatives (suivi des remboursements)
-- ============================================================

-- ------------------------------------------------------------
-- Journal d'audit — trace chaque action sensible de l'API
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.journal_audit (
    id              BIGSERIAL PRIMARY KEY,
    utilisateur_id  BIGINT       REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    username        VARCHAR(50)  NOT NULL,                        -- dénormalisé pour conservation après suppression
    action          VARCHAR(100) NOT NULL,                        -- ex: ALERTE_CLOTUREE, COLLECTE_SOUMISE, LOGIN
    entite          VARCHAR(50),                                  -- ex: AlerteImpaye, CollecteTerrain
    entite_id       VARCHAR(100),                                 -- PK de l'entité concernée
    details         TEXT,                                         -- JSON ou message libre
    ip_client       VARCHAR(45),
    user_agent      VARCHAR(500),
    statut          VARCHAR(20)  NOT NULL DEFAULT 'SUCCES'
                    CHECK (statut IN ('SUCCES', 'ECHEC', 'REFUS')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ja_utilisateur_id ON app.journal_audit (utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_ja_action         ON app.journal_audit (action);
CREATE INDEX IF NOT EXISTS idx_ja_created_at     ON app.journal_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ja_entite         ON app.journal_audit (entite, entite_id);

COMMENT ON TABLE app.journal_audit IS
    'Journal d''audit des actions sensibles — une ligne par action API (RGPD)';

-- ------------------------------------------------------------
-- Échéances applicatives
-- Table intermédiaire pour suivre le calendrier de remboursement
-- côté application (hors pipeline ETL).
-- Le pipeline ETL gère staging.echeances — cette table est
-- destinée aux prêts saisis manuellement ou en attente d'intégration.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.echeances_app (
    id                  BIGSERIAL PRIMARY KEY,
    id_pret             VARCHAR(30)   NOT NULL,                   -- référence métier
    agent_id            BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    num_echeance        INTEGER       NOT NULL CHECK (num_echeance > 0),
    date_echeance       DATE          NOT NULL,
    montant_du          NUMERIC(15,2) NOT NULL CHECK (montant_du > 0),
    montant_paye        NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (montant_paye >= 0),
    date_paiement       DATE,
    statut              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE'
                        CHECK (statut IN ('EN_ATTENTE', 'PAYEE', 'PARTIELLE', 'EN_RETARD', 'ANNULEE')),
    collecte_id         BIGINT        REFERENCES app.collectes_terrain(id) ON DELETE SET NULL,
    observation         TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pret_echeance UNIQUE (id_pret, num_echeance)
);

CREATE INDEX IF NOT EXISTS idx_ea_id_pret       ON app.echeances_app (id_pret);
CREATE INDEX IF NOT EXISTS idx_ea_agent_id      ON app.echeances_app (agent_id);
CREATE INDEX IF NOT EXISTS idx_ea_date_echeance ON app.echeances_app (date_echeance);
CREATE INDEX IF NOT EXISTS idx_ea_statut        ON app.echeances_app (statut);

COMMENT ON TABLE app.echeances_app IS
    'Échéances de remboursement applicatives — calendrier suivi côté Spring Boot';

-- ------------------------------------------------------------
-- Trigger automatique updated_at sur echeances_app
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION app.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_echeances_app_updated_at ON app.echeances_app;
CREATE TRIGGER trg_echeances_app_updated_at
    BEFORE UPDATE ON app.echeances_app
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();
