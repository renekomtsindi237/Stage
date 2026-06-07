-- ============================================================
-- V11__user_email.sql
-- Ajout de l'adresse email sur les utilisateurs
-- Requis pour l'envoi automatique des identifiants à la création
-- ============================================================

ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS email VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateurs_email
    ON app.utilisateurs (email)
    WHERE email IS NOT NULL;

COMMENT ON COLUMN app.utilisateurs.email IS
    'Adresse email — réception des identifiants et notifications';
