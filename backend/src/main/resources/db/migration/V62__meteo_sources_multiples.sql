-- ============================================================
-- V62 — Permet plusieurs sources météo par zone/jour
--
-- app.donnees_meteo avait UNIQUE(zone_id, date_observation) : une seule
-- observation par zone/jour, toutes sources confondues. Pour croiser
-- Open-Meteo, OpenWeatherMap et NASA POWER (les 3 sources CHECK déjà/
-- désormais autorisées) sur une même zone/jour plutôt que la dernière
-- source à écrire n'écrase les autres, l'unicité passe à
-- (zone_id, date_observation, source) — stg_meteo.sql agrège (moyenne)
-- entre sources disponibles pour un signal plus robuste.
-- ============================================================

-- ADD CONSTRAINT n'a pas d'équivalent IF NOT EXISTS en Postgres — gardé par
-- un bloc DO pour que cette migration reste rejouable si les contraintes
-- cibles existent déjà (ex. appliquées manuellement en staging avant que
-- Flyway n'ait eu l'occasion de l'exécuter lui-même).
ALTER TABLE app.donnees_meteo DROP CONSTRAINT IF EXISTS donnees_meteo_zone_id_date_observation_key;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'donnees_meteo_zone_date_source_key'
    ) THEN
        ALTER TABLE app.donnees_meteo
            ADD CONSTRAINT donnees_meteo_zone_date_source_key UNIQUE (zone_id, date_observation, source);
    END IF;
END $$;

ALTER TABLE app.donnees_meteo DROP CONSTRAINT IF EXISTS donnees_meteo_source_check;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'donnees_meteo_source_check'
    ) THEN
        ALTER TABLE app.donnees_meteo
            ADD CONSTRAINT donnees_meteo_source_check
            CHECK (source IN ('METEOCAM', 'NASA_POWER', 'OPEN_METEO', 'OPENWEATHER', 'AGENT_TERRAIN'));
    END IF;
END $$;
