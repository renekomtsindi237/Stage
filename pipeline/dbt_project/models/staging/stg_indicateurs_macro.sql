{{
    config(
        materialized='incremental',
        unique_key=['indicateur', 'source', 'date_observation'],
        on_schema_change='append_new_columns'
    )
}}

-- app.facteurs_macro utilise déjà les codes indicateurs attendus par
-- feat_client_externe.sql (TAUX_DIRECTEUR_BEAC, TAUX_INFLATION_MENSUEL,
-- INDICE_PRIX_CONSOMMATION, ...) — pas de mapping à faire, contrairement à
-- la version précédente de ce modèle qui supposait des codes minuscules
-- ('taux_directeur', 'inflation_cemac', ...) n'ayant jamais existé dans le
-- schéma réellement migré (V21__donnees_externes.sql). La colonne date
-- réelle est `date_observation`, pas `date_publication` ; il n'y a pas de
-- colonne `unite`/`pays` sur cette table.

WITH source AS (
    SELECT *
    FROM {{ source('app', 'facteurs_macro') }}
    WHERE date_observation IS NOT NULL
      AND valeur IS NOT NULL
    {% if is_incremental() %}
      AND date_observation > (SELECT MAX(date_observation) FROM {{ this }})
    {% endif %}
)

SELECT
    indicateur,
    valeur::NUMERIC(18, 6) AS valeur,
    ''                     AS unite,
    source,
    'CM'                   AS pays,
    date_observation,
    NOW() AS _dbt_loaded_at

FROM source
WHERE date_observation >= '{{ var("date_debut_historique") }}'::DATE
  AND indicateur IN (
      'TAUX_DIRECTEUR_BEAC',
      'TAUX_INFLATION_MENSUEL',
      'INDICE_PRIX_CONSOMMATION'
  )
