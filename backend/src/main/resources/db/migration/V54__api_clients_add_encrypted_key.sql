-- V54 : Ajout colonne key_encrypted sur app.api_clients
-- Stocke la clé brute chiffrée en AES-256-GCM pour la fonctionnalité de révélation.
-- La révélation nécessite le mot de passe du compte SUPPORT.

ALTER TABLE app.api_clients
    ADD COLUMN IF NOT EXISTS key_encrypted TEXT;
