-- V53 : Table des clients API externes (BluCash, CBS, intégrations tiers)
-- Les clés sont créées par le SUPPORT via l'interface d'administration.
-- La clé brute est affichée une seule fois à la création — seul le hash SHA-256 est stocké.

CREATE TABLE IF NOT EXISTS app.api_clients (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL,
    description      TEXT,
    imf_id           BIGINT       NOT NULL REFERENCES app.imf(id) ON DELETE CASCADE,
    system_user_id   BIGINT       REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    key_prefix       VARCHAR(25)  NOT NULL UNIQUE,  -- ex: mcr_live_a1b2c3d4 — affiché dans l'UI
    key_hash         VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 hex de la clé brute
    scopes           TEXT         NOT NULL DEFAULT 'collectes:write,clients:read,creances:read,scores:read,alertes:read',
    statut           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (statut IN ('ACTIVE','REVOKED')),
    created_by       BIGINT       REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at     TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ,
    revoked_by       BIGINT       REFERENCES app.utilisateurs(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_api_clients_imf_id ON app.api_clients(imf_id);
CREATE INDEX IF NOT EXISTS idx_api_clients_key_prefix ON app.api_clients(key_prefix);
CREATE INDEX IF NOT EXISTS idx_api_clients_statut ON app.api_clients(statut);
