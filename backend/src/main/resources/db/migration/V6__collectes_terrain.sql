-- ============================================================
-- V6__collectes_terrain.sql
-- Table des collectes terrain par les agents mobiles.
-- Gestion du mode hors-ligne (offline-first Flutter) :
--   - idCollecteMobile (UUID Flutter) pour déduplication
--   - statut SOUMISE / CONFIRMEE / DOUBLON / REJETEE
--   - canal de paiement MTN / Orange / Espèces / Virement
--   - coordonnées GPS (latitude / longitude)
--   - rattachement multi-tenant (imf_id)
-- ============================================================

CREATE TABLE IF NOT EXISTS app.collectes_terrain (
    id                      BIGSERIAL        PRIMARY KEY,
    uid                     UUID             NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_collecte_mobile      VARCHAR(200)     NOT NULL UNIQUE,
    agent_id                BIGINT           NOT NULL REFERENCES app.utilisateurs(id),
    imf_id                  BIGINT           NOT NULL REFERENCES app.imf(id),
    client_id               VARCHAR(100)     NOT NULL,
    pret_id                 VARCHAR(100),
    date_collecte           DATE             NOT NULL,
    montant_collecte        NUMERIC(15, 2)   NOT NULL CHECK (montant_collecte >= 0),
    canal_paiement          VARCHAR(30)      NOT NULL
                                DEFAULT 'ESPECES'
                                CHECK (canal_paiement IN (
                                    'MTN_MOBILE_MONEY','ORANGE_MONEY',
                                    'ESPECES','VIREMENT')),
    reference_transaction   VARCHAR(200),
    observation             TEXT,
    statut                  VARCHAR(20)      NOT NULL
                                DEFAULT 'SOUMISE'
                                CHECK (statut IN ('SOUMISE','CONFIRMEE','DOUBLON','REJETEE')),
    latitude                NUMERIC(10, 6),
    longitude               NUMERIC(10, 6),
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- Colonnes ajoutées si la table existait déjà sans elles
ALTER TABLE app.collectes_terrain
    ADD COLUMN IF NOT EXISTS uid        UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Contrainte UNIQUE sur uid (si pas déjà présente)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'app.collectes_terrain'::regclass
          AND conname = 'collectes_terrain_uid_key'
    ) THEN
        ALTER TABLE app.collectes_terrain ADD CONSTRAINT collectes_terrain_uid_key UNIQUE (uid);
    END IF;
END $$;

-- Index de déduplication : l'agent ne peut pas soumettre deux fois
-- la même référence de transaction le même jour
CREATE UNIQUE INDEX IF NOT EXISTS idx_collecte_ref_date
    ON app.collectes_terrain (reference_transaction, date_collecte)
    WHERE reference_transaction IS NOT NULL;

-- Index de recherche par agent + IMF (pagination des collectes)
CREATE INDEX IF NOT EXISTS idx_collecte_agent_imf
    ON app.collectes_terrain (agent_id, imf_id, date_collecte DESC);

-- Index multi-tenant
CREATE INDEX IF NOT EXISTS idx_collecte_imf
    ON app.collectes_terrain (imf_id, created_at DESC);

COMMENT ON TABLE  app.collectes_terrain              IS 'Collectes de remboursement terrain par agents mobiles (offline-first)';
COMMENT ON COLUMN app.collectes_terrain.uid          IS 'Identifiant public UUID (exposé API, immuable)';
COMMENT ON COLUMN app.collectes_terrain.id_collecte_mobile IS 'UUID généré côté Flutter pour déduplication sync';
COMMENT ON COLUMN app.collectes_terrain.statut       IS 'SOUMISE→CONFIRMEE (normal) | DOUBLON (UUID connu) | REJETEE (fraude/erreur)';
