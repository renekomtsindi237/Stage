-- dim_agence.sql
-- Dimension agence — depuis stg_prets

{{
  config(
    materialized = 'table',
    schema = 'dw',
    tags = ['dw', 'dimension']
  )
}}

SELECT DISTINCT
    nom_agence                          AS id_agence,
    nom_agence,
    NULL::text                          AS ville,
    NULL::text                          AS region,
    NULL::text                          AS responsable,
    TRUE                                AS est_active,
    NOW()                               AS _dbt_updated_at
FROM {{ ref('stg_prets') }}
WHERE nom_agence IS NOT NULL
