-- mart_kpi_collecte.sql
-- Table de faits KPI collectes par agence, agent et cycle.
-- Calcule le taux de réalisation des objectifs et les variations.

WITH collectes AS (
    SELECT * FROM {{ ref('stg_collectes_terrain') }}
),

cycles AS (
    SELECT * FROM app.cycles_collecte
    WHERE statut = 'EN_COURS'
),

kpi_agent AS (
    SELECT
        c.imf_id,
        u.agence_id,
        c.agent_id,
        CURRENT_DATE                                                  AS date_snapshot,

        -- Volume
        SUM(CASE WHEN c.date_collecte = CURRENT_DATE
                 THEN c.montant_collecte ELSE 0 END)                 AS montant_jour,
        SUM(CASE WHEN c.date_collecte >= DATE_TRUNC('week', CURRENT_DATE)
                 THEN c.montant_collecte ELSE 0 END)                 AS montant_semaine,
        SUM(CASE WHEN c.date_collecte >= DATE_TRUNC('month', CURRENT_DATE)
                 THEN c.montant_collecte ELSE 0 END)                 AS montant_mois,

        -- Nb transactions
        COUNT(CASE WHEN c.date_collecte = CURRENT_DATE THEN 1 END)  AS nb_transactions_jour,

        -- Canal
        SUM(CASE WHEN c.famille_canal = 'MOBILE_MONEY'
                  AND c.date_collecte >= DATE_TRUNC('month', CURRENT_DATE)
                 THEN c.montant_collecte ELSE 0 END)                 AS montant_mobile_money,
        SUM(CASE WHEN c.famille_canal = 'PHYSIQUE'
                  AND c.date_collecte >= DATE_TRUNC('month', CURRENT_DATE)
                 THEN c.montant_collecte ELSE 0 END)                 AS montant_especes,
        SUM(CASE WHEN c.famille_canal = 'BANCAIRE'
                  AND c.date_collecte >= DATE_TRUNC('month', CURRENT_DATE)
                 THEN c.montant_collecte ELSE 0 END)                 AS montant_virement,

        -- Variation semaine précédente
        ROUND(
            SUM(CASE WHEN c.date_collecte >= DATE_TRUNC('week', CURRENT_DATE)
                     THEN c.montant_collecte ELSE 0 END) /
            NULLIF(SUM(CASE WHEN c.date_collecte BETWEEN
                                DATE_TRUNC('week', CURRENT_DATE) - INTERVAL '7 days'
                            AND DATE_TRUNC('week', CURRENT_DATE) - INTERVAL '1 day'
                            THEN c.montant_collecte ELSE 0 END), 0) - 1
        , 4)                                                          AS variation_semaine_precedente

    FROM collectes c
    JOIN app.utilisateurs u ON u.id = c.agent_id
    WHERE c.date_collecte >= CURRENT_DATE - 30
    GROUP BY c.imf_id, u.agence_id, c.agent_id
),

-- Taux de réalisation par rapport à l'objectif du cycle
with_objectif AS (
    SELECT
        ka.*,
        cyc.objectif_montant_cycle,
        cyc.date_fin                                                  AS fin_cycle,
        ROUND(ka.montant_mois /
              NULLIF(cyc.objectif_montant_cycle, 0) * 100, 2)        AS taux_realisation_pct,
        cyc.date_fin - CURRENT_DATE                                   AS jours_restants_cycle
    FROM kpi_agent ka
    LEFT JOIN cycles cyc ON cyc.agence_id = ka.agence_id
                         AND cyc.agent_id  = ka.agent_id
)

SELECT
    *,
    -- Flag objectif non atteint à J-3
    (jours_restants_cycle <= 3 AND taux_realisation_pct < 70) AS flag_objectif_risque,
    NOW() AS created_at
FROM with_objectif
