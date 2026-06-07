-- V25__positions_agents.sql
-- Tracking géolocalisé des agents terrain
--
-- Stratégie :
--   app.utilisateurs        : dernière position connue (upsert rapide)
--   app.positions_agents    : historique complet (1 ligne par ping GPS)
-- TTL automatique : les lignes > 90 jours sont supprimées par pg_cron
-- ou par le DAG Airflow (tâche de maintenance hebdomadaire).

-- ── 1. Dernière position sur app.utilisateurs (colonnes déjà créées en V18) ──
-- S'assurer que les colonnes existent (idempotent)
ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS latitude          DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude         DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS precision_gps_m   NUMERIC(7,1),
    ADD COLUMN IF NOT EXISTS derniere_position_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS position_active   BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN app.utilisateurs.latitude            IS 'Dernière latitude GPS connue (WGS-84)';
COMMENT ON COLUMN app.utilisateurs.longitude           IS 'Dernière longitude GPS connue (WGS-84)';
COMMENT ON COLUMN app.utilisateurs.precision_gps_m     IS 'Précision GPS en mètres (±)';
COMMENT ON COLUMN app.utilisateurs.derniere_position_at IS 'Horodatage du dernier ping GPS';
COMMENT ON COLUMN app.utilisateurs.position_active     IS 'FALSE = agent a désactivé le partage de position';

-- ── 2. Table historique des positions ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.positions_agents (
    id              BIGSERIAL   PRIMARY KEY,
    imf_id          BIGINT      NOT NULL REFERENCES app.imf(id) ON DELETE CASCADE,
    agent_id        BIGINT      NOT NULL REFERENCES app.utilisateurs(id) ON DELETE CASCADE,
    latitude        DOUBLE PRECISION NOT NULL
                        CHECK (latitude  BETWEEN -90  AND  90),
    longitude       DOUBLE PRECISION NOT NULL
                        CHECK (longitude BETWEEN -180 AND 180),
    precision_gps_m NUMERIC(7,1)
                        CHECK (precision_gps_m > 0),
    altitude_m      NUMERIC(8,1),        -- optionnel (altimètre mobile)
    vitesse_kmh     NUMERIC(6,1),        -- optionnel (GPS déplacé)
    cap_degres      NUMERIC(5,1),        -- optionnel (direction 0-360°)
    source          VARCHAR(20)  NOT NULL DEFAULT 'MOBILE'
                        CHECK (source IN ('MOBILE', 'COLLECTE', 'MANUEL')),
    -- source=COLLECTE : la position est celle d'une collecte (rétroalimenté)
    collecte_uuid   UUID,                -- référence si source=COLLECTE
    captured_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  app.positions_agents                IS 'Historique GPS des agents terrain (TTL 90j)';
COMMENT ON COLUMN app.positions_agents.source         IS 'MOBILE=ping autonome, COLLECTE=position d''une collecte';
COMMENT ON COLUMN app.positions_agents.collecte_uuid  IS 'UUID de la collecte_epargne associée si source=COLLECTE';

-- Index pour la carte temps réel (tri par captured_at — filtrage applicatif)
CREATE INDEX IF NOT EXISTS idx_positions_agents_recent
    ON app.positions_agents (imf_id, agent_id, captured_at DESC);

-- Index pour le trajet journalier d'un agent
CREATE INDEX IF NOT EXISTS idx_positions_agents_agent_date
    ON app.positions_agents (agent_id, captured_at DESC);

-- Index PostGIS-ready si extension disponible (sinon ignoré par les requêtes Haversine)
-- CREATE INDEX IF NOT EXISTS idx_positions_agents_geom
--     ON app.positions_agents USING gist (ST_Point(longitude, latitude));

-- ── 3. Vue matérialisée : dernières positions actives (rafraîchie à chaque ping) ─
CREATE OR REPLACE VIEW app.v_agents_position_courante AS
    SELECT
        u.id             AS agent_id,
        u.imf_id,
        u.username,
        u.latitude,
        u.longitude,
        u.precision_gps_m,
        u.derniere_position_at,
        u.position_active,
        u.fcm_token,
        -- En ligne = dernière position < 15 minutes (ping GPS période standard)
        (u.derniere_position_at > NOW() - INTERVAL '15 minutes'
         AND u.position_active = TRUE)  AS en_deplacement
    FROM app.utilisateurs u
    WHERE u.role = 'AGENT'
      AND u.actif = TRUE
      AND u.latitude IS NOT NULL;

COMMENT ON VIEW app.v_agents_position_courante IS
    'Vue des agents avec leur dernière position GPS — filtrée sur agents actifs avec coordonnées';

-- ── 4. Politique de rétention : trigger de nettoyage auto > 90 jours ─────────
-- Exécuté par Airflow (maintenance hebdomadaire) ou pg_cron si disponible.
-- On crée simplement la procédure, l'appel est externe.
CREATE OR REPLACE PROCEDURE app.purger_positions_anciennes(p_retention_jours INT DEFAULT 90)
LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM app.positions_agents
    WHERE captured_at < NOW() - (p_retention_jours || ' days')::INTERVAL;

    RAISE NOTICE 'Positions purgées (> % jours)', p_retention_jours;
END;
$$;
