-- stg_collectes_mtn.sql
-- Nettoyage et normalisation des transactions MTN Mobile Money
-- Source : raw.transactions_mtn
-- Cible  : staging.stg_collectes_mtn

{{
  config(
    materialized = 'table',
    schema = 'staging',
    tags = ['staging', 'mtn']
  )
}}

WITH source AS (
    SELECT *
    FROM {{ source('raw', 'transactions_mtn') }}
    WHERE statut ILIKE ANY (ARRAY['%success%', '%succes%', '%completed%', '%reussi%'])
),

nettoyage AS (
    SELECT
        transaction_id,

        -- Normalisation de la date (formats possibles : DD/MM/YYYY, YYYY-MM-DD, DD-MM-YYYY HH:MI)
        CASE
            WHEN date_transaction ~ '^\d{4}-\d{2}-\d{2}'
                THEN date_transaction::date
            WHEN date_transaction ~ '^\d{2}/\d{2}/\d{4}'
                THEN TO_DATE(date_transaction, 'DD/MM/YYYY')
            WHEN date_transaction ~ '^\d{2}-\d{2}-\d{4}'
                THEN TO_DATE(SPLIT_PART(date_transaction, ' ', 1), 'DD-MM-YYYY')
            ELSE NULL
        END AS date_transaction,

        -- Nettoyage du montant (supprimer espaces, virgules décimales, symbole XAF)
        REGEXP_REPLACE(montant, '[^0-9.]', '', 'g')::NUMERIC(15, 2) AS montant,

        -- Nettoyage téléphone (garder uniquement les chiffres)
        REGEXP_REPLACE(telephone_payeur, '[^0-9]', '', 'g') AS telephone_payeur,

        TRIM(reference_externe)                            AS reference_externe,
        UPPER(TRIM(statut))                                AS statut,
        hash_sha256,
        date_ingestion

    FROM source
    WHERE transaction_id IS NOT NULL
      AND montant IS NOT NULL
      AND REGEXP_REPLACE(montant, '[^0-9.]', '', 'g') ~ '^\d+(\.\d+)?$'
      AND REGEXP_REPLACE(montant, '[^0-9.]', '', 'g')::NUMERIC > 0
),

deduplication AS (
    -- Garder la première occurrence par hash (au cas où raw aurait des doublons résiduels)
    SELECT DISTINCT ON (hash_sha256) *
    FROM nettoyage
    WHERE date_transaction IS NOT NULL
    ORDER BY hash_sha256, date_transaction
)

SELECT
    transaction_id,
    date_transaction,
    montant,
    telephone_payeur,
    reference_externe,
    statut,
    hash_sha256,
    date_ingestion,
    NOW() AS _dbt_updated_at
FROM deduplication
