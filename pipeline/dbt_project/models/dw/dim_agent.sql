-- dim_agent.sql
-- Dimension agent de terrain
-- Source : staging.stg_agents

{{
  config(
    materialized = 'table',
    schema = 'dw',
    tags = ['dw', 'dimension']
  )
}}

SELECT
    id_agent,
    nom_agent,
    id_agence,
    nom_agence,
    _dbt_updated_at
FROM {{ ref('stg_agents') }}
WHERE id_agent IS NOT NULL
