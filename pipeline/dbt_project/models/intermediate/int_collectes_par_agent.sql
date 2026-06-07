{{
    config(materialized='table')
}}

-- Agrégats hebdomadaires et mensuels par agent
-- Sert à alimenter objectifs_collecte et les features ML de régularité

WITH collectes AS (
    SELECT *
    FROM {{ ref('stg_collectes_epargne') }}
    WHERE statut_validation = 'VALIDE'
      AND NOT est_doublon
),

par_agent_hebdo AS (
    SELECT
        imf_code,
        agent_username,
        agence_code,
        DATE_TRUNC('week', date_collecte)::DATE     AS semaine_debut,
        COUNT(*)                                     AS nb_collectes,
        SUM(montant_collecte)                        AS montant_total,
        AVG(montant_collecte)                        AS montant_moyen,
        STDDEV(montant_collecte)                     AS montant_ecart_type,
        COUNT(DISTINCT client_id_externe)            AS nb_clients_uniques,
        SUM(montant_collecte) FILTER (WHERE canal_paiement = 'ESPECES') AS montant_especes,
        SUM(montant_collecte) FILTER (WHERE canal_paiement = 'MTN')     AS montant_mtn,
        SUM(montant_collecte) FILTER (WHERE canal_paiement = 'ORANGE')  AS montant_orange,
        SUM(montant_collecte) FILTER (WHERE canal_paiement = 'WAVE')    AS montant_wave,
        COUNT(*) FILTER (WHERE est_geolocalisee)     AS nb_geolocalisees,
        MIN(date_collecte)                           AS premiere_collecte_semaine,
        MAX(date_collecte)                           AS derniere_collecte_semaine
    FROM collectes
    GROUP BY imf_code, agent_username, agence_code, DATE_TRUNC('week', date_collecte)
),

avec_tendance AS (
    SELECT
        *,
        ROUND(nb_collectes * 1.0 / 5, 2)  AS collectes_par_jour_ouvrable,

        -- Variation vs semaine précédente
        LAG(montant_total) OVER (
            PARTITION BY imf_code, agent_username
            ORDER BY semaine_debut
        ) AS montant_semaine_precedente,

        ROUND((montant_total - LAG(montant_total) OVER (
            PARTITION BY imf_code, agent_username ORDER BY semaine_debut
        )) / NULLIF(LAG(montant_total) OVER (
            PARTITION BY imf_code, agent_username ORDER BY semaine_debut
        ), 0) * 100, 2) AS variation_semaine_pct,

        -- Rang dans l'agence cette semaine
        RANK() OVER (
            PARTITION BY imf_code, agence_code, semaine_debut
            ORDER BY montant_total DESC
        ) AS rang_agence_semaine

    FROM par_agent_hebdo
)

SELECT
    imf_code,
    agent_username,
    agence_code,
    semaine_debut,
    nb_collectes,
    montant_total,
    ROUND(montant_moyen, 2)      AS montant_moyen,
    ROUND(montant_ecart_type, 2) AS montant_ecart_type,
    nb_clients_uniques,
    montant_especes,
    montant_mtn,
    montant_orange,
    montant_wave,
    nb_geolocalisees,
    ROUND(nb_geolocalisees * 100.0 / NULLIF(nb_collectes, 0), 2) AS pct_geolocalisees,
    collectes_par_jour_ouvrable,
    variation_semaine_pct,
    rang_agence_semaine,
    montant_semaine_precedente,
    NOW() AS _dbt_updated_at
FROM avec_tendance
