-- fact_remboursements.sql
-- Table de faits : état des remboursements par prêt (snapshot quotidien)
-- Alimentée de façon incrémentale — une ligne par prêt par jour

{{
  config(
    materialized = 'incremental',
    schema = 'dw',
    unique_key = 'id_pret',
    tags = ['dw', 'fact']
  )
}}

WITH prets AS (
    SELECT *
    FROM {{ ref('stg_prets') }}
    {% if is_incremental() %}
    WHERE _dbt_updated_at > (SELECT COALESCE(MAX(_dbt_updated_at), '2024-01-01') FROM {{ this }})
    {% endif %}
)

SELECT
    p.id_pret,
    TO_CHAR(CURRENT_DATE, 'YYYYMMDD')::integer  AS date_key,
    dc.client_key,
    da.agence_key,
    dp.produit_key,
    dag.agent_key,
    p.montant_rembourse,
    p.montant_pret                              AS montant_attendu,
    p.solde_restant,
    p.jours_retard,
    p.statut_pret,
    NOW()                                       AS _dbt_updated_at
FROM prets p
LEFT JOIN dw.dim_client dc   ON dc.id_client_source = p.id_client
LEFT JOIN dw.dim_agence da   ON da.id_agence = p.id_agence
LEFT JOIN dw.dim_produit dp  ON dp.id_produit = p.id_produit
LEFT JOIN dw.dim_agent dag   ON dag.id_agent = p.id_agent
