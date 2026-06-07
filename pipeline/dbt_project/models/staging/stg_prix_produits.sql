{{
    config(
        materialized='incremental',
        unique_key=['code_produit', 'zone_id', 'date_prix', 'source_type'],
        on_schema_change='append_new_columns'
    )
}}

WITH source AS (
    SELECT *
    FROM {{ source('raw', 'prix_marche') }}
    WHERE statut_ingestion IN ('BRUT', 'MAPPE')
    {% if is_incremental() %}
      AND recu_at > (SELECT MAX(_dbt_loaded_at) FROM {{ this }})
    {% endif %}
),

nettoyee AS (
    SELECT
        COALESCE(produit_mappe_code, code_produit_source)    AS code_produit,
        nom_produit_source                                    AS nom_produit,
        zone_id,
        NULLIF(date_prix, '')::DATE                          AS date_prix,
        NULLIF(REGEXP_REPLACE(prix_unitaire, '[^0-9.]', '', 'g'), '')::NUMERIC(12,4) AS prix_unitaire,
        TRIM(UPPER(unite_mesure))                             AS unite_mesure,
        NULLIF(REGEXP_REPLACE(prix_min, '[^0-9.]', '', 'g'), '')::NUMERIC(12,4)     AS prix_min,
        NULLIF(REGEXP_REPLACE(prix_max, '[^0-9.]', '', 'g'), '')::NUMERIC(12,4)     AS prix_max,
        source_type                                           AS source,
        CASE source_type
            WHEN 'API_MINCOMMERCE' THEN 5
            WHEN 'API_ANSP'        THEN 5
            WHEN 'AGENT_TERRAIN'   THEN 3
            WHEN 'SCRAPING_WEB'    THEN 2
            ELSE 2
        END                                                   AS fiabilite,
        id AS _source_raw_id
    FROM source
    WHERE code_produit_source IS NOT NULL
      AND zone_id IS NOT NULL
      AND date_prix IS NOT NULL
),

valide AS (
    SELECT
        *,
        (prix_unitaire IS NULL OR prix_unitaire <= 0) AS est_valeur_aberrante
    FROM nettoyee
    WHERE date_prix >= '{{ var("date_debut_historique") }}'::DATE
      AND prix_unitaire > 0
),

avec_moyennes AS (
    SELECT
        *,
        AVG(prix_unitaire) OVER (
            PARTITION BY code_produit, zone_id
            ORDER BY date_prix
            ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
        ) AS prix_moy_7j,

        AVG(prix_unitaire) OVER (
            PARTITION BY code_produit, zone_id
            ORDER BY date_prix
            ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
        ) AS prix_moy_30j

    FROM valide
),

avec_variations AS (
    SELECT
        *,
        ROUND((prix_unitaire - LAG(prix_unitaire, 7) OVER (
            PARTITION BY code_produit, zone_id ORDER BY date_prix
        )) / NULLIF(LAG(prix_unitaire, 7) OVER (
            PARTITION BY code_produit, zone_id ORDER BY date_prix
        ), 0) * 100, 4) AS variation_7j_pct,

        ROUND((prix_unitaire - LAG(prix_unitaire, 30) OVER (
            PARTITION BY code_produit, zone_id ORDER BY date_prix
        )) / NULLIF(LAG(prix_unitaire, 30) OVER (
            PARTITION BY code_produit, zone_id ORDER BY date_prix
        ), 0) * 100, 4) AS variation_30j_pct
    FROM avec_moyennes
)

SELECT
    code_produit,
    nom_produit,
    NULL::TEXT AS categorie,
    zone_id,
    date_prix,
    prix_unitaire,
    unite_mesure,
    prix_min,
    prix_max,
    source,
    fiabilite,
    ROUND(prix_moy_7j, 4)   AS prix_moy_7j,
    ROUND(prix_moy_30j, 4)  AS prix_moy_30j,
    variation_7j_pct,
    variation_30j_pct,
    FALSE AS est_valeur_aberrante,
    _source_raw_id,
    NOW() AS _dbt_loaded_at,
    NOW() AS _dbt_updated_at

FROM avec_variations
