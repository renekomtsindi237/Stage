-- ============================================================
-- V2__sync_logs.sql
-- Table de traçabilité des synchronisations hors-ligne → en ligne
-- Chaque tentative de sync depuis un appareil mobile est enregistrée.
-- ============================================================

CREATE TABLE IF NOT EXISTS app.sync_logs (
    id                  BIGSERIAL PRIMARY KEY,
    sync_id             VARCHAR(36)  NOT NULL UNIQUE,    -- UUID côté client
    device_id           VARCHAR(100) NOT NULL,            -- identifiant de l'appareil Flutter
    agent_id            BIGINT       NOT NULL REFERENCES app.utilisateurs(id),
    nb_items_soumis     INTEGER      NOT NULL DEFAULT 0,
    nb_succes           INTEGER      NOT NULL DEFAULT 0,
    nb_doublons         INTEGER      NOT NULL DEFAULT 0,
    nb_conflits         INTEGER      NOT NULL DEFAULT 0,
    nb_erreurs          INTEGER      NOT NULL DEFAULT 0,
    statut_sync         VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS'
                        CHECK (statut_sync IN ('EN_COURS','PARTIELLE','COMPLETE','ECHEC')),
    message_sync        TEXT,                             -- résumé lisible par l'utilisateur
    sync_started_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sync_completed_at   TIMESTAMP WITH TIME ZONE,
    ip_client           VARCHAR(45)                       -- IPv4 ou IPv6
);

CREATE INDEX IF NOT EXISTS idx_sl_agent_id    ON app.sync_logs (agent_id);
CREATE INDEX IF NOT EXISTS idx_sl_device_id   ON app.sync_logs (device_id);
CREATE INDEX IF NOT EXISTS idx_sl_statut_sync ON app.sync_logs (statut_sync);
CREATE INDEX IF NOT EXISTS idx_sl_started_at  ON app.sync_logs (sync_started_at DESC);

COMMENT ON TABLE app.sync_logs IS
    'Journal des synchronisations mobiles — une ligne par tentative de sync depuis un appareil Flutter';

-- ============================================================
-- Ajout de colonnes de traçabilité sur collectes_terrain
-- pour lier chaque collecte à sa session de synchronisation
-- ============================================================

ALTER TABLE app.collectes_terrain
    ADD COLUMN IF NOT EXISTS sync_id         VARCHAR(36),   -- FK logique vers sync_logs.sync_id
    ADD COLUMN IF NOT EXISTS device_id       VARCHAR(100),  -- appareil source
    ADD COLUMN IF NOT EXISTS sync_attempt    INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS message_statut  TEXT;          -- message explicatif du statut actuel

COMMENT ON COLUMN app.collectes_terrain.sync_id IS
    'Identifiant de la session de synchronisation qui a créé cette collecte';
COMMENT ON COLUMN app.collectes_terrain.message_statut IS
    'Message lisible par l''utilisateur expliquant le statut actuel de la collecte';
