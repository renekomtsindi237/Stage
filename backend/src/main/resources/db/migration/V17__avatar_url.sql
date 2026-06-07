-- ============================================================
-- V17 : Ajout de l'URL d'avatar sur les utilisateurs
-- ============================================================

ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
