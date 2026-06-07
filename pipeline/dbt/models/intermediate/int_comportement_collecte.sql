-- int_comportement_collecte.sql
-- Features CRS (Collection Reliability Score) par client sur 90 jours.
-- Mesure la régularité, la discipline et l'évolution des collectes terrain.

WITH collectes AS (
    SELECT * FROM {{ ref('stg_collectes_terrain') }}
),

window_90j AS (
    SELECT
        c.client_id,
        c.imf_id,
        COUNT(*)                                                      AS nb_collectes_90j,
        COUNT(CASE WHEN c.date_collecte >= CURRENT_DATE - 30 THEN 1 END) AS nb_collectes_30j,
        SUM(c.montant_collecte)                                       AS montant_total_90j,
        AVG(c.montant_collecte)                                       AS montant_moyen_collecte,
        STDDEV(c.montant_collecte)                                    AS ecart_type_montant,
        MIN(c.date_collecte)                                          AS premiere_collecte,
        MAX(c.date_collecte)                                          AS derniere_collecte,

        -- Nombre de semaines avec au moins une collecte (sur 13 semaines)
        COUNT(DISTINCT DATE_TRUNC('week', c.date_collecte))           AS semaines_avec_collecte,

        -- Montant des 30 derniers jours vs 31-60 jours (tendance)
        SUM(CASE WHEN c.date_collecte >= CURRENT_DATE - 30
                 THEN c.montant_collecte ELSE 0 END)                  AS montant_30j_recent,
        SUM(CASE WHEN c.date_collecte BETWEEN CURRENT_DATE - 60
                                          AND CURRENT_DATE - 31
                 THEN c.montant_collecte ELSE 0 END)                  AS montant_30j_precedent,

        -- Distribution par canal
        COUNT(CASE WHEN famille_canal = 'MOBILE_MONEY' THEN 1 END)   AS nb_mobile_money,
        COUNT(CASE WHEN famille_canal = 'PHYSIQUE'     THEN 1 END)   AS nb_especes

    FROM collectes c
    WHERE c.date_collecte >= CURRENT_DATE - 90
    GROUP BY c.client_id, c.imf_id
),

with_scores AS (
    SELECT
        w.*,

        -- Régularité : semaines actives / 13 semaines de la fenêtre
        ROUND(w.semaines_avec_collecte::numeric / 13, 4)              AS regularite_collecte_pct,

        -- Coefficient de variation (instabilité des montants)
        ROUND(COALESCE(w.ecart_type_montant, 0) /
              NULLIF(w.montant_moyen_collecte, 0), 4)                 AS coefficient_variation_collecte,

        -- Semaines SANS collecte
        13 - w.semaines_avec_collecte                                 AS nb_semaines_sans_collecte,

        -- Tendance collecte 30j : ratio montant récent / précédent
        ROUND(COALESCE(w.montant_30j_recent, 0) /
              NULLIF(w.montant_30j_precedent, 1), 4) - 1              AS tendance_collecte_30j,

        -- Ratio collecte / encours crédit
        ROUND(COALESCE(w.montant_total_90j, 0) /
              NULLIF((SELECT SUM(cr.montant_impaye)
                      FROM {{ ref('stg_creances') }} cr
                      WHERE cr.client_id = w.client_id), 0), 4)      AS ratio_collecte_credit

    FROM window_90j w
),

-- Rang relatif dans l'agence (pour rang_collecte_agence)
ranked AS (
    SELECT
        s.*,
        u.agence_id,
        ROUND(
            PERCENT_RANK() OVER (
                PARTITION BY u.agence_id
                ORDER BY s.montant_total_90j
            )::numeric, 4
        )                                                             AS rang_collecte_agence
    FROM with_scores s
    JOIN app.utilisateurs u ON u.id = s.client_id
)

SELECT
    r.client_id,
    r.imf_id,
    r.agence_id,
    CURRENT_DATE                                                      AS date_feature,
    r.nb_collectes_30j,
    r.montant_moyen_collecte,
    r.regularite_collecte_pct,
    r.coefficient_variation_collecte,
    r.nb_semaines_sans_collecte,
    r.tendance_collecte_30j,
    r.ratio_collecte_credit,
    r.rang_collecte_agence
FROM ranked r
