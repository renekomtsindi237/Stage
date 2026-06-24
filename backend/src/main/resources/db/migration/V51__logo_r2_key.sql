-- Clé R2 pour le logo IMF (ex: "imf-logos/ABC-uuid.png")
-- logo_url continue de stocker l'URL publique (proxy ou R2 direct)
ALTER TABLE app.imf
    ADD COLUMN IF NOT EXISTS logo_r2_key VARCHAR(400);
