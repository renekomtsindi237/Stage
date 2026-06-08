-- ──────────────────────────────────────────────────────────────────────────────
-- OTP codes — réinitialisation de mot de passe par email
-- Durée de vie : 10 min, max 3 tentatives, hash SHA-256 du code (jamais en clair)
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.otp_codes (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app.utilisateurs(id) ON DELETE CASCADE,
    code_hash        VARCHAR(64) NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts_used    SMALLINT    NOT NULL DEFAULT 0,
    used             BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_otp_user_id    ON app.otp_codes (user_id);
CREATE INDEX IF NOT EXISTS idx_otp_expires_at ON app.otp_codes (expires_at);

COMMENT ON TABLE app.otp_codes IS 'Codes OTP de réinitialisation de mot de passe — expirés automatiquement après 10 min ou 3 tentatives échouées';
