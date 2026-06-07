-- dim_produit.sql
-- Dimension produit financier — depuis stg_prets

{{
  config(
    materialized = 'table',
    schema = 'dw',
    tags = ['dw', 'dimension']
  )
}}

SELECT DISTINCT
    nom_produit             AS id_produit,
    nom_produit,
    'MICROCREDIT'           AS type_produit,  -- enrichi manuellement si nécessaire
    NULL::numeric(5,2)      AS taux_interet,
    NULL::integer           AS duree_mois,
    TRUE                    AS est_actif,
    NOW()                   AS _dbt_updated_at
FROM {{ ref('stg_prets') }}
WHERE nom_produit IS NOT NULL
