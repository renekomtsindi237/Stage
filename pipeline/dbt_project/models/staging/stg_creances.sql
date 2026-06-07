{{
    config(
        materialized='incremental',
        unique_key=['imf_code', 'id_pret'],
        on_schema_change='append_new_columns'
    )
}}

WITH source AS (
    SELECT *
    FROM {{ source('raw', 'export_cbs') }}
    WHERE statut_ingestion = 'BRUT'
    {% if is_incremental() %}
      AND recu_at > (SELECT MAX(_dbt_loaded_at) FROM {{ this }})
    {% endif %}
),

nettoyee AS (
    SELECT
        imf_code,
        id_pret,
        id_client,
        nom_client,
        telephone_client,
        agence_code,
        produit_code,
        agent_cbs_code,

        -- Typage montants
        NULLIF(REGEXP_REPLACE(montant_pret, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2)      AS montant_initial,
        COALESCE(NULLIF(REGEXP_REPLACE(montant_rembourse, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2), 0) AS montant_rembourse,
        NULLIF(REGEXP_REPLACE(solde_restant, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2)     AS solde_restant,
        COALESCE(NULLIF(REGEXP_REPLACE(montant_impaye, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2), 0)    AS montant_impaye,

        -- Typage dates
        NULLIF(date_deblocage, '')::DATE                                                  AS date_deblocage,
        NULLIF(date_echeance, '')::DATE                                                   AS date_echeance,
        NULLIF(date_derniere_echeance_impayee, '')::DATE                                  AS date_premiere_echeance_impayee,

        -- Jours retard
        COALESCE(NULLIF(REGEXP_REPLACE(jours_retard, '[^0-9]', '', 'g'), '')::INTEGER, 0) AS jours_retard,

        UPPER(TRIM(statut_pret))  AS statut_pret,
        type_garantie,
        NULLIF(REGEXP_REPLACE(valeur_garantie, '[^0-9.]', '', 'g'), '')::NUMERIC(15,2) AS valeur_garantie,
        nom_caution,

        id AS _source_raw_id

    FROM source
    WHERE id_pret IS NOT NULL
      AND id_client IS NOT NULL
      AND imf_code IS NOT NULL
),

avec_par AS (
    SELECT
        *,

        -- Classification PAR (COBAC CEMAC)
        CASE
            WHEN jours_retard = 0                            THEN 'COURANT'
            WHEN jours_retard BETWEEN 1  AND 29              THEN 'COURANT'
            WHEN jours_retard BETWEEN 30 AND 59              THEN 'PAR30'
            WHEN jours_retard BETWEEN 60 AND 89              THEN 'PAR60'
            WHEN jours_retard BETWEEN 90 AND 179             THEN 'PAR90'
            WHEN jours_retard BETWEEN 180 AND 359            THEN 'PAR180'
            ELSE 'PERTE'
        END AS categorie_par,

        -- Classe risque COBAC (Règlement 01/02/CEMAC)
        CASE
            WHEN jours_retard = 0                            THEN 'A'
            WHEN jours_retard BETWEEN 1  AND 29              THEN 'A'
            WHEN jours_retard BETWEEN 30 AND 89              THEN 'B'
            WHEN jours_retard BETWEEN 90 AND 179             THEN 'C'
            WHEN jours_retard BETWEEN 180 AND 359            THEN 'D'
            ELSE 'E'
        END AS classe_risque_cobac,

        -- Taux provision COBAC
        CASE
            WHEN jours_retard < 30                           THEN 0
            WHEN jours_retard BETWEEN 30  AND 89             THEN 20
            WHEN jours_retard BETWEEN 90  AND 179            THEN 50
            WHEN jours_retard BETWEEN 180 AND 359            THEN 80
            ELSE 100
        END AS taux_provision_cobac,

        (montant_initial IS NULL OR montant_rembourse IS NULL) AS est_donnee_incomplete,

        ROW_NUMBER() OVER (PARTITION BY imf_code, id_pret ORDER BY _source_raw_id DESC) AS rn

    FROM nettoyee
    WHERE montant_initial > 0
)

SELECT
    imf_code,
    id_pret,
    id_client,
    nom_client,
    telephone_client,
    agence_code,
    produit_code,
    agent_cbs_code,
    montant_initial,
    montant_rembourse,
    solde_restant,
    montant_impaye,
    COALESCE(montant_impaye * taux_provision_cobac / 100.0, 0) AS montant_provision,
    0::NUMERIC(15,2)   AS interets_retard,
    date_deblocage,
    date_echeance,
    date_premiere_echeance_impayee,
    jours_retard,
    statut_pret,
    categorie_par,
    classe_risque_cobac,
    taux_provision_cobac,
    type_garantie,
    valeur_garantie,
    nom_caution,
    est_donnee_incomplete,
    _source_raw_id,
    NOW() AS _dbt_loaded_at,
    NOW() AS _dbt_updated_at

FROM avec_par
WHERE rn = 1
