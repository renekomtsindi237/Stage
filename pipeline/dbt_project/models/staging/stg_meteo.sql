{{
    config(
        materialized='incremental',
        unique_key=['zone_id', 'date_observation'],
        on_schema_change='append_new_columns'
    )
}}

-- app.donnees_meteo est déjà au format wide (une ligne par zone/jour, avec
-- indice_secheresse en enum VARCHAR prêt à l'emploi) — pas de pivot narrow
-- à faire, contrairement à la version précédente de ce modèle qui supposait
-- un format (variable, valeur) et une colonne `date_meteo` qui n'ont jamais
-- existé dans le schéma réellement migré (V21__donnees_externes.sql). Cette
-- table ne porte pas non plus de latitude/longitude (non utilisées par
-- feat_client_externe.sql, qui les prend de app.clients_informels/agences).
--
-- Depuis V62, plusieurs sources (Open-Meteo/OpenWeatherMap/NASA POWER)
-- peuvent coexister pour un même (zone_id, date_observation) — agrégé ici
-- par moyenne plutôt que de laisser le merge incrémental dbt en choisir
-- une arbitrairement (unique_key=[zone_id, date_observation] exige une
-- seule ligne par clé en sortie de ce modèle).

WITH source AS (
    SELECT *
    FROM {{ source('app', 'donnees_meteo') }}
    WHERE date_observation IS NOT NULL
    {% if is_incremental() %}
      AND date_observation > (SELECT MAX(date_observation) FROM {{ this }})
    {% endif %}
)

SELECT
    zone_id,
    date_observation,
    COALESCE(AVG(precipitation_mm), 0) AS precipitation_mm,
    AVG(temperature_max)               AS temperature_max,
    AVG(temperature_min)               AS temperature_min,
    -- indice_secheresse est déjà recalculé de façon cohérente pour toutes
    -- les sources d'un même jour par _maj_indice_secheresse() (moyenne des
    -- sources en amont) — MAX() ici ne fait que dédupliquer, pas arbitrer.
    COALESCE(MAX(indice_secheresse), 'NORMAL') AS indice_secheresse,
    COUNT(DISTINCT source)             AS nb_sources,
    NOW() AS _dbt_loaded_at

FROM source
WHERE date_observation >= '{{ var("date_debut_historique") }}'::DATE
GROUP BY zone_id, date_observation
