-- V18 — Géolocalisation des utilisateurs + logo IMF

-- Coordonnées GPS optionnelles pour les agents et utilisateurs terrain
ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

ALTER TABLE app.imf
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);
