-- stg_prets.sql
-- Nettoyage et enrichissement des prêts issus de l'export CBS
-- Source : raw.export_cbs
-- Cible  : staging.stg_prets

{{
  config(
    materialized = 'table',
    schema = 'staging',
    tags = ['staging', 'cbs', 'prets']
  )
}}

WITH source AS (
    -- Prendre la version la plus récente par prêt (si plusieurs exports CBS)
    SELECT DISTINCT ON (id_pret) *
    FROM {{ source('raw', 'export_cbs') }}
    WHERE id_pret IS NOT NULL
    ORDER BY id_pret, date_ingestion DESC
),

nettoyage AS (
    SELECT
        TRIM(id_pret)                                                       AS id_pret,
        TRIM(id_client)                                                     AS id_client,
        INITCAP(TRIM(nom_client))                                           AS nom_client,
        REGEXP_REPLACE(telephone_client, '[^0-9]', '', 'g')                AS telephone_client,

        -- Montants
        NULLIF(REGEXP_REPLACE(montant_pret, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2)       AS montant_pret,
        COALESCE(
            NULLIF(REGEXP_REPLACE(montant_rembourse, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2), 0
        )                                                                               AS montant_rembourse,
        NULLIF(REGEXP_REPLACE(solde_restant, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2)      AS solde_restant,

        -- Dates
        CASE WHEN date_deblocage ~ '^\d{4}-\d{2}-\d{2}' THEN date_deblocage::date
             WHEN date_deblocage ~ '^\d{2}/\d{2}/\d{4}' THEN TO_DATE(date_deblocage, 'DD/MM/YYYY')
             ELSE NULL END                                                              AS date_deblocage,

        CASE WHEN date_echeance ~ '^\d{4}-\d{2}-\d{2}' THEN date_echeance::date
             WHEN date_echeance ~ '^\d{2}/\d{2}/\d{4}' THEN TO_DATE(date_echeance, 'DD/MM/YYYY')
             ELSE NULL END                                                              AS date_echeance,

        -- Statut normalisé
        CASE
            WHEN UPPER(statut_pret) ILIKE '%solde%'     THEN 'SOLDE'
            WHEN UPPER(statut_pret) ILIKE '%retard%'    THEN 'EN_RETARD'
            WHEN UPPER(statut_pret) ILIKE '%write%off%' THEN 'WRITE_OFF'
            WHEN UPPER(statut_pret) ILIKE '%actif%'     THEN 'ACTIF'
            ELSE 'ACTIF'
        END AS statut_pret,

        TRIM(nom_agence)                                                    AS nom_agence,
        MD5(LOWER(TRIM(COALESCE(nom_agence, ''))))                          AS id_agence,
        TRIM(nom_produit)                                                   AS nom_produit,
        MD5(LOWER(TRIM(COALESCE(nom_produit, ''))))                         AS id_produit,
        TRIM(nom_agent)                                                     AS nom_agent,
        MD5(LOWER(TRIM(COALESCE(nom_agent, ''))))                           AS id_agent,
        date_ingestion

    FROM source
    WHERE id_client IS NOT NULL
),

avec_retard AS (
    SELECT
        *,
        CASE
            WHEN statut_pret = 'EN_RETARD' AND date_echeance IS NOT NULL
            THEN GREATEST(0, CURRENT_DATE - date_echeance)
            ELSE 0
        END AS jours_retard
    FROM nettoyage
    WHERE montant_pret IS NOT NULL
      AND montant_pret > 0
)

SELECT
    id_pret,
    id_client,
    nom_client,
    telephone_client,
    montant_pret,
    date_deblocage,
    date_echeance,
    montant_rembourse,
    solde_restant,
    statut_pret,
    id_agence,
    nom_agence,
    id_produit,
    nom_produit,
    id_agent,
    nom_agent,
    jours_retard,
    NOW() AS _dbt_updated_at
FROM avec_retard
