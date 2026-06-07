-- fact_collectes.sql
-- Table de faits : toutes les collectes (MTN + Orange + Terrain)
-- Alimentation incrémentale sur id_source (idempotente)

{{
  config(
    materialized = 'incremental',
    schema = 'dw',
    unique_key = 'id_source',
    tags = ['dw', 'fact']
  )
}}

WITH mtn AS (
    SELECT
        transaction_id          AS id_source,
        'MTN'                   AS source,
        'MTN'                   AS canal,
        date_transaction,
        montant,
        telephone_payeur,
        reference_externe,
        statut,
        NULL::text              AS id_pret_associe,
        NULL::text              AS id_agent,
        hash_sha256
    FROM {{ ref('stg_collectes_mtn') }}
    {% if is_incremental() %}
    WHERE date_transaction > (SELECT COALESCE(MAX(d.date_valeur), '2024-01-01'::date)
                               FROM {{ this }} f
                               JOIN {{ ref('dim_date') }} d ON d.date_key = f.date_key
                               WHERE f.source = 'MTN')
    {% endif %}
),

orange AS (
    SELECT
        transaction_id          AS id_source,
        'ORANGE'                AS source,
        'ORANGE'                AS canal,
        date_transaction,
        montant,
        telephone_payeur,
        reference_externe,
        statut,
        NULL::text              AS id_pret_associe,
        NULL::text              AS id_agent,
        hash_sha256
    FROM {{ ref('stg_collectes_orange') }}
    {% if is_incremental() %}
    WHERE date_transaction > (SELECT COALESCE(MAX(d.date_valeur), '2024-01-01'::date)
                               FROM {{ this }} f
                               JOIN {{ ref('dim_date') }} d ON d.date_key = f.date_key
                               WHERE f.source = 'ORANGE')
    {% endif %}
),

terrain AS (
    SELECT
        id_collecte_mobile      AS id_source,
        'TERRAIN'               AS source,
        CASE
            WHEN UPPER(canal) = 'MTN'     THEN 'TERRAIN_MOBILE'
            WHEN UPPER(canal) = 'ORANGE'  THEN 'TERRAIN_MOBILE'
            ELSE 'TERRAIN_ESPECES'
        END                     AS canal,
        date_collecte::date     AS date_transaction,
        montant::numeric(15,2)  AS montant,
        NULL::text              AS telephone_payeur,
        reference_mobile        AS reference_externe,
        'CONFIRMED'             AS statut,
        id_pret                 AS id_pret_associe,
        id_agent,
        id_collecte_mobile      AS hash_sha256
    FROM raw.collectes_terrain
    WHERE statut_sync = 'CONFIRMED'
    {% if is_incremental() %}
    AND date_ingestion > (SELECT COALESCE(MAX(date_ingestion), '2024-01-01'::timestamp)
                          FROM {{ this }} WHERE source = 'TERRAIN')
    {% endif %}
),

toutes_collectes AS (
    SELECT * FROM mtn
    UNION ALL
    SELECT * FROM orange
    UNION ALL
    SELECT * FROM terrain
),

avec_dimensions AS (
    SELECT
        c.id_source,
        c.source,
        c.canal,
        c.montant,
        c.reference_externe,
        c.id_pret_associe,
        c.statut,

        -- Clé date
        TO_CHAR(c.date_transaction, 'YYYYMMDD')::integer AS date_key,

        -- Clé client (via téléphone → dim_client)
        dc.client_key,

        -- Clé agence (via agent → dim_agent → dim_agence)
        da.agence_key,

        -- Clé agent
        dag.agent_key,

        -- Score ML (NULL à ce stade, alimenté par dag_scoring_quotidien)
        NULL::numeric(5,4) AS score_risque_ml

    FROM toutes_collectes c
    LEFT JOIN dw.dim_client dc
        ON dc.id_client_source = c.telephone_payeur
    LEFT JOIN dw.dim_agent dag
        ON dag.id_agent = c.id_agent
    LEFT JOIN dw.dim_agence da
        ON da.agence_key = dag.agence_key
)

SELECT
    id_source,
    date_key,
    client_key,
    agence_key,
    agent_key,
    montant,
    canal,
    statut,
    reference_externe,
    id_pret_associe,
    score_risque_ml,
    source,
    NOW() AS _dbt_updated_at
FROM avec_dimensions
