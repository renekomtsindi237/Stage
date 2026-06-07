{{
    config(
        materialized='incremental',
        unique_key='hash_sha256',
        on_schema_change='append_new_columns'
    )
}}

WITH source AS (
    SELECT *
    FROM {{ source('raw', 'collectes_terrain') }}
    WHERE statut_ingestion = 'RECU'
    {% if is_incremental() %}
      AND recu_at > (SELECT MAX(_dbt_loaded_at) FROM {{ this }})
    {% endif %}
),

nettoyee AS (
    SELECT
        uuid_mobile,
        imf_code,
        agence_code,
        agent_username,
        client_id_externe,
        cycle_ref,

        -- Typage et nettoyage montant
        CASE
            WHEN montant_collecte ~ '^\d+(\.\d+)?$'
            THEN montant_collecte::NUMERIC
            ELSE NULL
        END AS montant_collecte,

        -- Typage date
        CASE
            WHEN date_collecte ~ '^\d{4}-\d{2}-\d{2}$'
            THEN date_collecte::DATE
            ELSE NULL
        END AS date_collecte,

        CASE
            WHEN heure_collecte ~ '^\d{2}:\d{2}(:\d{2})?$'
            THEN heure_collecte::TIME
            ELSE NULL
        END AS heure_collecte,

        UPPER(canal_paiement) AS canal_paiement,
        reference_transaction,

        -- GPS
        CASE WHEN latitude  ~ '^-?\d+(\.\d+)?$' THEN latitude::NUMERIC(10,7)  ELSE NULL END AS latitude,
        CASE WHEN longitude ~ '^-?\d+(\.\d+)?$' THEN longitude::NUMERIC(10,7) ELSE NULL END AS longitude,
        CASE WHEN precision_gps_metres ~ '^\d+(\.\d+)?$' THEN precision_gps_metres::NUMERIC(6,1) ELSE NULL END AS precision_gps_metres,

        observation,
        hash_sha256,
        id AS _source_raw_id

    FROM source
    WHERE uuid_mobile IS NOT NULL
      AND agent_username IS NOT NULL
      AND client_id_externe IS NOT NULL
),

avec_flags AS (
    SELECT
        *,
        -- Déduplication inter-lot par uuid_mobile
        ROW_NUMBER() OVER (PARTITION BY uuid_mobile ORDER BY _source_raw_id) AS rn,

        -- Flags qualité
        (montant_collecte IS NULL OR montant_collecte <= 0)         AS est_montant_nul,
        (montant_collecte > 5000000)                                  AS est_montant_aberrant,
        (date_collecte IS NULL)                                       AS est_date_invalide,
        (latitude IS NOT NULL AND longitude IS NOT NULL)              AS est_geolocalisee,
        'VALIDE'::TEXT                                                AS statut_validation

    FROM nettoyee
    WHERE montant_collecte > 0
      AND date_collecte IS NOT NULL
      AND date_collecte >= '{{ var("date_debut_historique") }}'::DATE
)

SELECT
    uuid_mobile,
    imf_code,
    agence_code,
    agent_username,
    client_id_externe,
    cycle_ref,
    montant_collecte,
    date_collecte,
    heure_collecte,
    canal_paiement,
    reference_transaction,
    latitude,
    longitude,
    precision_gps_metres,
    observation,
    (rn > 1)             AS est_doublon,
    FALSE                AS est_hors_zone,
    est_montant_aberrant,
    est_geolocalisee,
    statut_validation,
    hash_sha256,
    _source_raw_id,
    NOW()                AS _dbt_loaded_at,
    NOW()                AS _dbt_updated_at

FROM avec_flags
WHERE rn = 1
