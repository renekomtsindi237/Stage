{{
    config(
        materialized='incremental',
        unique_key=['indicateur', 'source', 'date_observation'],
        on_schema_change='append_new_columns'
    )
}}

-- Normalise les codes indicateurs macro-économiques (BEAC / INS) vers les noms
-- attendus par le feature store ML (feat_client_externe.sql) :
--   taux_directeur  → TAUX_DIRECTEUR_BEAC
--   inflation_cemac → TAUX_INFLATION_MENSUEL
--   ipc_cameroun    → INDICE_PRIX_CONSOMMATION

WITH source AS (
    SELECT *
    FROM {{ source('app', 'facteurs_macro') }}
    WHERE date_publication IS NOT NULL
      AND valeur IS NOT NULL
    {% if is_incremental() %}
      AND date_publication > (SELECT MAX(date_observation) FROM {{ this }})
    {% endif %}
),

normalise AS (
    SELECT
        CASE indicateur
            WHEN 'taux_directeur'         THEN 'TAUX_DIRECTEUR_BEAC'
            WHEN 'inflation_cemac'        THEN 'TAUX_INFLATION_MENSUEL'
            WHEN 'ipc_cameroun'           THEN 'INDICE_PRIX_CONSOMMATION'
            WHEN 'ihpc_zone_cemac'        THEN 'IHPC_CEMAC'
            WHEN 'taux_change_eur_xaf'    THEN 'TAUX_CHANGE_EUR_XAF'
            WHEN 'pib_croissance_cmr'     THEN 'PIB_CROISSANCE_CMR'
            WHEN 'taux_chomage_cameroun'  THEN 'TAUX_CHOMAGE_CMR'
            WHEN 'reserve_change_cemac'   THEN 'RESERVE_CHANGE_CEMAC'
            WHEN 'credit_economie_cmr'    THEN 'CREDIT_ECONOMIE_CMR'
            ELSE UPPER(indicateur)
        END                                AS indicateur,
        valeur::NUMERIC(18, 6)             AS valeur,
        COALESCE(unite, '')                AS unite,
        source,
        COALESCE(pays, 'CM')               AS pays,
        date_publication                   AS date_observation
    FROM source
)

SELECT
    indicateur,
    valeur,
    unite,
    source,
    pays,
    date_observation,
    NOW() AS _dbt_loaded_at

FROM normalise
WHERE date_observation >= '{{ var("date_debut_historique") }}'::DATE
  -- Conserver uniquement les indicateurs reconnus pour le feature store ML
  AND indicateur IN (
      'TAUX_DIRECTEUR_BEAC',
      'TAUX_INFLATION_MENSUEL',
      'INDICE_PRIX_CONSOMMATION',
      'IHPC_CEMAC',
      'TAUX_CHANGE_EUR_XAF',
      'PIB_CROISSANCE_CMR',
      'TAUX_CHOMAGE_CMR',
      'RESERVE_CHANGE_CEMAC',
      'CREDIT_ECONOMIE_CMR'
  )
