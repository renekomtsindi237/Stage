-- dim_client.sql
-- Dimension client — consolidée depuis stg_prets (CBS)

{{
  config(
    materialized = 'table',
    schema = 'dw',
    tags = ['dw', 'dimension']
  )
}}

WITH clients AS (
    SELECT DISTINCT ON (id_client)
        id_client               AS id_client_source,
        nom_client,
        telephone_client        AS telephone,
        nom_agence              AS zone_geographique,
        MIN(date_deblocage) OVER (PARTITION BY id_client) AS date_premiere_op
    FROM {{ ref('stg_prets') }}
    WHERE id_client IS NOT NULL
    ORDER BY id_client, _dbt_updated_at DESC
)

SELECT
    id_client_source,
    nom_client,
    telephone,
    zone_geographique,
    date_premiere_op,
    NOW() AS _dbt_updated_at
FROM clients
