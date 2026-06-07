{{
    config(materialized='table')
}}

-- Profil comportemental recouvrement par client
-- Combinaison créances CBS + historique collectes pour feature engineering MCRS

WITH creances AS (
    SELECT *
    FROM {{ ref('stg_creances') }}
),

collectes_12m AS (
    SELECT
        imf_code,
        client_id_externe,
        COUNT(*)                    AS nb_collectes_12m,
        SUM(montant_collecte)       AS montant_total_collectes_12m,
        AVG(montant_collecte)       AS montant_moy_collecte,
        STDDEV(montant_collecte)    AS ecart_type_collecte,
        MIN(date_collecte)          AS premiere_collecte,
        MAX(date_collecte)          AS derniere_collecte,
        COUNT(DISTINCT DATE_TRUNC('week', date_collecte)) AS nb_semaines_actives
    FROM {{ ref('stg_collectes_epargne') }}
    WHERE statut_validation = 'VALIDE'
      AND NOT est_doublon
      AND date_collecte >= CURRENT_DATE - INTERVAL '365 days'
    GROUP BY imf_code, client_id_externe
),

par_client AS (
    SELECT
        c.imf_code,
        c.id_client,
        COUNT(*)                                       AS nb_creances_total,
        COUNT(*) FILTER (WHERE c.categorie_par != 'COURANT') AS nb_creances_probleme,
        MAX(c.jours_retard)                            AS jours_retard_max,
        AVG(c.jours_retard) FILTER (WHERE c.jours_retard > 0) AS jours_retard_moyen,
        SUM(c.montant_impaye)                          AS montant_impaye_total,
        SUM(c.montant_initial)                         AS encours_total,
        SUM(c.montant_rembourse)                       AS montant_rembourse_total,
        ROUND(SUM(c.montant_rembourse) / NULLIF(SUM(c.montant_initial), 0), 4) AS taux_remboursement_pct,
        COUNT(*) FILTER (WHERE c.categorie_par = 'PAR90')  AS nb_par90,
        COUNT(*) FILTER (WHERE c.categorie_par = 'PERTE')  AS nb_pertes
    FROM creances c
    GROUP BY c.imf_code, c.id_client
)

SELECT
    pc.imf_code,
    pc.id_client AS client_id_externe,
    pc.nb_creances_total,
    pc.nb_creances_probleme,
    pc.jours_retard_max,
    COALESCE(ROUND(pc.jours_retard_moyen, 2), 0) AS jours_retard_moyen,
    pc.montant_impaye_total,
    pc.encours_total,
    COALESCE(pc.taux_remboursement_pct, 0) AS taux_remboursement_pct,
    pc.nb_par90,
    pc.nb_pertes,
    -- Collectes
    COALESCE(cl.nb_collectes_12m, 0)            AS nb_collectes_12m,
    COALESCE(cl.montant_total_collectes_12m, 0)  AS montant_total_collectes_12m,
    ROUND(cl.montant_moy_collecte, 2)            AS montant_moy_collecte,
    ROUND(cl.ecart_type_collecte, 2)             AS ecart_type_collecte,
    cl.premiere_collecte,
    cl.derniere_collecte,
    COALESCE(cl.nb_semaines_actives, 0)          AS nb_semaines_actives_12m,
    -- Régularité : fraction de semaines actives sur 52 semaines
    ROUND(COALESCE(cl.nb_semaines_actives, 0) * 1.0 / 52, 4) AS regularite_collecte_pct,
    -- Ratio collecte / crédit
    ROUND(COALESCE(cl.montant_total_collectes_12m, 0)
          / NULLIF(pc.encours_total, 0), 4)      AS ratio_collecte_credit,
    NOW()                                        AS _dbt_updated_at
FROM par_client pc
LEFT JOIN collectes_12m cl
    ON  pc.imf_code        = cl.imf_code
    AND pc.id_client       = cl.client_id_externe
