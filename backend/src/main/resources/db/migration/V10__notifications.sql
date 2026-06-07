-- ============================================================
-- V10__notifications.sql
-- Table app.notifications — événements SSE persistés
-- Historique temps réel par IMF avec statut lu/non-lu
-- ============================================================

CREATE TABLE IF NOT EXISTS app.notifications (
    id          BIGSERIAL    PRIMARY KEY,
    imf_id      BIGINT       REFERENCES app.imf(id) ON DELETE CASCADE,
    type        VARCHAR(50)  NOT NULL,
    titre       VARCHAR(200) NOT NULL,
    message     TEXT         NOT NULL,
    target_role VARCHAR(50),
    payload     TEXT,
    lu          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notif_imf_lu      ON app.notifications (imf_id, lu);
CREATE INDEX IF NOT EXISTS idx_notif_target_role  ON app.notifications (imf_id, target_role);
CREATE INDEX IF NOT EXISTS idx_notif_created_at   ON app.notifications (created_at DESC);

COMMENT ON TABLE app.notifications IS
    'Notifications temps réel persistées — événements SSE (ALERTE_CREATED, COLLECTE_CONFIRMED, etc.)';
