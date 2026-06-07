-- stg_agents.sql
-- Agents de terrain uniques extraits du CBS
-- Clé surrogate : MD5(nom_agent normalisé) — cohérente avec stg_prets
-- Source : raw.export_cbs
-- Cible  : staging.stg_agents

{{
  config(
    materialized = 'table',
    schema = 'staging',
    tags = ['staging', 'cbs', 'agents']
  )
}}

WITH source AS (
    SELECT DISTINCT ON (nom_agent_norm)
        LOWER(TRIM(nom_agent))                          AS nom_agent_norm,
        INITCAP(TRIM(nom_agent))                        AS nom_agent,
        MD5(LOWER(TRIM(COALESCE(nom_agent, ''))))       AS id_agent,
        TRIM(nom_agence)                                AS nom_agence,
        MD5(LOWER(TRIM(COALESCE(nom_agence, ''))))      AS id_agence,
        date_ingestion
    FROM {{ source('raw', 'export_cbs') }}
    WHERE nom_agent IS NOT NULL
      AND TRIM(nom_agent) <> ''
    ORDER BY nom_agent_norm, date_ingestion DESC
)

SELECT
    id_agent,
    nom_agent,
    id_agence,
    nom_agence,
    date_ingestion  AS date_derniere_maj_cbs,
    NOW()           AS _dbt_updated_at
FROM source
