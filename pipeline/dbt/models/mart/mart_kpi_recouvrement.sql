-- mart_kpi_recouvrement.sql
-- Table de faits KPI recouvrement par IMF et agence.
-- Agrège les créances, PAR COBAC, provisions et taux de recouvrement.
-- Matérialisé en TABLE (mise à jour quotidienne par dag_recouvrement).

WITH creances AS (
    SELECT * FROM {{ ref('stg_creances') }}
),

par_imf AS (
    SELECT
        cr.imf_id,
        a.id                                                          AS agence_id,
        a.code                                                        AS agence_code,
        CURRENT_DATE                                                  AS date_snapshot,

        -- Encours
        SUM(cr.montant_impaye)                                        AS encours_total,
        COUNT(*)                                                      AS nb_creances_actives,
        COUNT(DISTINCT cr.client_id)                                  AS nb_clients_concernes,

        -- PAR (montants)
        SUM(CASE WHEN cr.is_par30  THEN cr.montant_impaye ELSE 0 END) AS par30_montant,
        SUM(CASE WHEN cr.is_par60  THEN cr.montant_impaye ELSE 0 END) AS par60_montant,
        SUM(CASE WHEN cr.is_par90  THEN cr.montant_impaye ELSE 0 END) AS par90_montant,
        SUM(CASE WHEN cr.is_par180 THEN cr.montant_impaye ELSE 0 END) AS par180_montant,

        -- PAR (pourcentages)
        ROUND(SUM(CASE WHEN cr.is_par30  THEN cr.montant_impaye ELSE 0 END) /
              NULLIF(SUM(cr.montant_impaye), 0) * 100, 2)            AS par30_pct,
        ROUND(SUM(CASE WHEN cr.is_par60  THEN cr.montant_impaye ELSE 0 END) /
              NULLIF(SUM(cr.montant_impaye), 0) * 100, 2)            AS par60_pct,
        ROUND(SUM(CASE WHEN cr.is_par90  THEN cr.montant_impaye ELSE 0 END) /
              NULLIF(SUM(cr.montant_impaye), 0) * 100, 2)            AS par90_pct,
        ROUND(SUM(CASE WHEN cr.is_par180 THEN cr.montant_impaye ELSE 0 END) /
              NULLIF(SUM(cr.montant_impaye), 0) * 100, 2)            AS par180_pct,

        -- Provisions COBAC
        SUM(cr.taux_provision_dbt * cr.montant_impaye)               AS provisions_totales,
        ROUND(SUM(cr.taux_provision_dbt * cr.montant_impaye) /
              NULLIF(SUM(cr.montant_impaye), 0) * 100, 2)            AS taux_provision_moyen_pct,

        -- Répartition par classe COBAC
        COUNT(CASE WHEN cr.classe_cobac_dbt = 'A' THEN 1 END)        AS nb_classe_a,
        COUNT(CASE WHEN cr.classe_cobac_dbt = 'B' THEN 1 END)        AS nb_classe_b,
        COUNT(CASE WHEN cr.classe_cobac_dbt = 'C' THEN 1 END)        AS nb_classe_c,
        COUNT(CASE WHEN cr.classe_cobac_dbt = 'D' THEN 1 END)        AS nb_classe_d,
        COUNT(CASE WHEN cr.classe_cobac_dbt = 'E' THEN 1 END)        AS nb_classe_e,

        -- Retard moyen
        ROUND(AVG(cr.jours_retard), 1)                               AS retard_moyen_jours,
        MAX(cr.jours_retard)                                          AS retard_max_jours

    FROM creances cr
    JOIN app.agences a ON a.id = cr.agence_id
    GROUP BY cr.imf_id, a.id, a.code
)

SELECT
    *,
    NOW() AS created_at
FROM par_imf
