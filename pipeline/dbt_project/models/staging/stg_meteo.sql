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
    COALESCE(precipitation_mm, 0) AS precipitation_mm,
    temperature_max,
    temperature_min,
    COALESCE(indice_secheresse, 'NORMAL') AS indice_secheresse,
    NOW() AS _dbt_loaded_at

FROM source
WHERE date_observation >= '{{ var("date_debut_historique") }}'::DATE
