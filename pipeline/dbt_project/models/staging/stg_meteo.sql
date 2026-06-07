{{
    config(
        materialized='incremental',
        unique_key=['zone_id', 'date_observation'],
        on_schema_change='append_new_columns'
    )
}}

-- Pivot les observations météo Open-Meteo du format narrow (variable, valeur)
-- vers un format wide (une ligne par zone et par jour) pour le feature store ML.

WITH source AS (
    SELECT *
    FROM {{ source('app', 'donnees_meteo') }}
    WHERE date_meteo IS NOT NULL
    {% if is_incremental() %}
      AND date_meteo > (SELECT MAX(date_observation) FROM {{ this }})
    {% endif %}
),

pivote AS (
    SELECT
        zone_nom                                                                  AS zone_id,
        date_meteo                                                                AS date_observation,
        MAX(latitude)                                                             AS latitude,
        MAX(longitude)                                                            AS longitude,
        MAX(valeur) FILTER (WHERE variable = 'precipitation')                    AS precipitation_mm,
        MAX(valeur) FILTER (WHERE variable = 'temperature_2m_max')               AS temperature_max,
        MAX(valeur) FILTER (WHERE variable = 'temperature_2m_min')               AS temperature_min,
        MAX(valeur) FILTER (WHERE variable = 'wind_speed_10m_max')               AS vent_max_kmh,
        MAX(valeur) FILTER (WHERE variable = 'et0_fao_evapotranspiration')       AS evapotranspiration_mm,
        -- Anomalie de précipitation (calculée par maj_app_donnees_meteo)
        MAX(anomalie_pct) FILTER (WHERE variable = 'precipitation')              AS anomalie_precipitation_pct,
        -- Indice de sécheresse booléen enrichi par le DAG
        BOOL_OR(indice_secheresse) FILTER (WHERE variable = 'precipitation')     AS est_secheresse
    FROM source
    GROUP BY zone_nom, date_meteo
),

avec_indice_categoriel AS (
    SELECT
        zone_id,
        date_observation,
        latitude,
        longitude,
        COALESCE(precipitation_mm, 0)           AS precipitation_mm,
        temperature_max,
        temperature_min,
        vent_max_kmh,
        evapotranspiration_mm,
        COALESCE(anomalie_precipitation_pct, 0) AS anomalie_precipitation_pct,
        -- Catégorisation de la sécheresse (ordinal alphabétique stable pour MAX dans feat_client_externe)
        CASE
            WHEN est_secheresse IS NULL OR precipitation_mm IS NULL THEN 'INCONNU'
            WHEN est_secheresse = TRUE AND COALESCE(anomalie_precipitation_pct, 0) <= -60 THEN 'SEVERE'
            WHEN est_secheresse = TRUE AND COALESCE(anomalie_precipitation_pct, 0) <= -40 THEN 'MODEREE'
            WHEN est_secheresse = TRUE                                                     THEN 'FAIBLE'
            ELSE                                                                                'NORMAL'
        END                                     AS indice_secheresse
    FROM pivote
)

SELECT
    zone_id,
    date_observation,
    latitude,
    longitude,
    precipitation_mm,
    temperature_max,
    temperature_min,
    vent_max_kmh,
    evapotranspiration_mm,
    indice_secheresse,
    anomalie_precipitation_pct,
    NOW() AS _dbt_loaded_at

FROM avec_indice_categoriel
WHERE date_observation >= '{{ var("date_debut_historique") }}'::DATE
