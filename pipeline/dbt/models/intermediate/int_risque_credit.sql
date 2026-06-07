-- int_risque_credit.sql
-- Features RPS (Recovery Prediction Score) par client.
-- Historique de remboursement, incidents et comportement de crédit.

WITH creances AS (
    SELECT * FROM {{ ref('stg_creances') }}
),

historique AS (
    SELECT
        cr.client_id,
        cr.imf_id,

        -- Retard actuel (pire créance active)
        MAX(cr.jours_retard)                                          AS jours_retard_actuel,

        -- Incidents de paiement 12 mois
        COUNT(CASE WHEN cr.is_par30
                    AND cr.date_derniere_echeance >= CURRENT_DATE - 365
                   THEN 1 END)                                        AS nb_incidents_paiement_12m,

        -- Taux de remboursement historique
        ROUND(
            1.0 - SUM(cr.montant_impaye) /
                  NULLIF(SUM(cr.montant_decaisse), 0)
        , 4)                                                          AS taux_remboursement_historique,

        -- Ratio créance/revenus (encours total vs montant moyen collecté)
        ROUND(
            SUM(cr.montant_impaye) /
            NULLIF((
                SELECT AVG(col.montant_collecte) * 12
                FROM app.collectes_terrain col
                WHERE col.client_id = cr.client_id_externe
                  AND col.date_collecte >= CURRENT_DATE - 365
                  AND col.statut = 'CONFIRMEE'
            ), 0)
        , 4)                                                          AS ratio_creance_revenus,

        -- Nombre de rééchelonnements
        COUNT(DISTINCT ar.id)                                         AS nb_reechelonnements,

        -- Score RPS précédent (continuité)
        COALESCE((
            SELECT cs.score_rps
            FROM ml.client_scores cs
            WHERE cs.client_id_externe = cr.client_id_externe
              AND cs.imf_id            = cr.imf_id
            ORDER BY cs.scored_at DESC
            LIMIT 1
        ), 0.5)                                                       AS score_rps_precedent,

        -- Capacité de remboursement (ratio collecte mensuelle / échéance mensuelle)
        ROUND(
            COALESCE((
                SELECT SUM(col.montant_collecte) / 3.0
                FROM app.collectes_terrain col
                WHERE col.client_id = cr.client_id_externe
                  AND col.date_collecte >= CURRENT_DATE - 90
                  AND col.statut = 'CONFIRMEE'
            ), 0) /
            NULLIF(SUM(cr.montant_impaye) / GREATEST(MAX(cr.jours_retard) / 30.0, 1), 0)
        , 4)                                                          AS capacite_remboursement

    FROM creances cr
    LEFT JOIN app.accords_reechelonnement ar
           ON ar.creance_id = cr.id
    GROUP BY cr.client_id, cr.imf_id, cr.client_id_externe
)

SELECT
    h.*,
    CURRENT_DATE AS date_feature
FROM historique h
