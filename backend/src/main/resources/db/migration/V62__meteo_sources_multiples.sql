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

ALTER TABLE app.donnees_meteo DROP CONSTRAINT IF EXISTS donnees_meteo_zone_id_date_observation_key;

ALTER TABLE app.donnees_meteo
    ADD CONSTRAINT donnees_meteo_zone_date_source_key UNIQUE (zone_id, date_observation, source);

ALTER TABLE app.donnees_meteo DROP CONSTRAINT IF EXISTS donnees_meteo_source_check;
ALTER TABLE app.donnees_meteo
    ADD CONSTRAINT donnees_meteo_source_check
    CHECK (source IN ('METEOCAM', 'NASA_POWER', 'OPEN_METEO', 'OPENWEATHER', 'AGENT_TERRAIN'));
