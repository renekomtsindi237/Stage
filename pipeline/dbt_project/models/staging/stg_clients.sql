-- stg_clients.sql
-- Clients uniques extraits du CBS, une ligne par client
-- Source : raw.export_cbs (via stg_prets)
-- Cible  : staging.stg_clients

{{
  config(
    materialized = 'table',
    schema = 'staging',
    tags = ['staging', 'cbs', 'clients']
  )
}}

WITH source AS (
    -- Dernier état connu par client (export CBS le plus récent)
    SELECT DISTINCT ON (id_client)
        id_client,
        nom_client,
        telephone_client,
        nom_agence,
        date_ingestion
    FROM {{ source('raw', 'export_cbs') }}
    WHERE id_client IS NOT NULL
      AND TRIM(id_client) <> ''
    ORDER BY id_client, date_ingestion DESC
),

nettoyage AS (
    SELECT
        TRIM(id_client)                                         AS id_client,
        INITCAP(TRIM(nom_client))                               AS nom_client,
        REGEXP_REPLACE(telephone_client, '[^0-9]', '', 'g')    AS telephone_client,
        TRIM(nom_agence)                                        AS agence_principale,
        date_ingestion
    FROM source
    WHERE nom_client IS NOT NULL
)

SELECT
    id_client,
    nom_client,
    telephone_client,
    agence_principale,
    date_ingestion  AS date_derniere_maj_cbs,
    NOW()           AS _dbt_updated_at
FROM nettoyage
