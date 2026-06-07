-- V16 : Préférences utilisateur granulaires
-- Permet à chaque utilisateur de personnaliser son expérience : thème visuel,
-- notifications par type d'événement et taille de page dans les listes.

ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS pref_theme        VARCHAR(10) NOT NULL DEFAULT 'auto',
    ADD COLUMN IF NOT EXISTS notif_alertes     BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notif_collectes   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS notif_sync        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS notif_pipeline    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS elements_par_page INTEGER     NOT NULL DEFAULT 20;

COMMENT ON COLUMN app.utilisateurs.pref_theme        IS 'Thème visuel préféré : light, dark ou auto (suit le système)';
COMMENT ON COLUMN app.utilisateurs.notif_alertes     IS 'Recevoir les notifications ALERTE_CREATED / ALERTE_UPDATED';
COMMENT ON COLUMN app.utilisateurs.notif_collectes   IS 'Recevoir les notifications COLLECTE_CONFIRMED';
COMMENT ON COLUMN app.utilisateurs.notif_sync        IS 'Recevoir les notifications SYNC_COMPLETED';
COMMENT ON COLUMN app.utilisateurs.notif_pipeline    IS 'Recevoir les notifications PIPELINE_STATUS (technique)';
COMMENT ON COLUMN app.utilisateurs.elements_par_page IS 'Nombre d''éléments par page dans les listes paginées (10/20/50)';
