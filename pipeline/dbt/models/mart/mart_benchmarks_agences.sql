-- mart_benchmarks_agences.sql
-- Benchmarks inter-agences par IMF (EF-D04).
-- Calcule les z-scores collecte et recouvrement pour le classement dashboard DIRECTEUR.

WITH kpi_rec AS (
    SELECT * FROM {{ ref('mart_kpi_recouvrement') }}
    WHERE date_snapshot = CURRENT_DATE
),

kpi_col AS (
    SELECT
        imf_id,
        agence_id,
        SUM(montant_mois) AS montant_collecte_mois,
        AVG(taux_realisation_pct) AS taux_realisation_moyen
    FROM {{ ref('mart_kpi_collecte') }}
    WHERE date_snapshot = CURRENT_DATE
    GROUP BY imf_id, agence_id
),

combined AS (
    SELECT
        r.imf_id,
        r.agence_id,
        r.agence_code,
        r.date_snapshot,

        -- Métriques recouvrement
        r.par30_pct,
        r.par90_pct,
        r.taux_provision_moyen_pct,
        r.retard_moyen_jours,
        r.nb_clients_concernes,

        -- Métriques collecte
        COALESCE(c.montant_collecte_mois, 0)    AS montant_collecte_mois,
        COALESCE(c.taux_realisation_moyen, 0)   AS taux_realisation_collecte_pct

    FROM kpi_rec r
    LEFT JOIN kpi_col c USING (imf_id, agence_id)
),

-- Z-scores par IMF (normalisation inter-agences)
z_scores AS (
    SELECT
        *,

        -- Z-score PAR30 (négatif = mieux que la moyenne)
        ROUND(
            (par30_pct - AVG(par30_pct)    OVER (PARTITION BY imf_id)) /
            NULLIF(STDDEV(par30_pct)       OVER (PARTITION BY imf_id), 0)
        , 3)                                                          AS zscore_par30,

        -- Z-score collecte (positif = mieux)
        ROUND(
            (montant_collecte_mois - AVG(montant_collecte_mois) OVER (PARTITION BY imf_id)) /
            NULLIF(STDDEV(montant_collecte_mois)                 OVER (PARTITION BY imf_id), 0)
        , 3)                                                          AS zscore_collecte,

        -- Z-score taux réalisation
        ROUND(
            (taux_realisation_collecte_pct - AVG(taux_realisation_collecte_pct) OVER (PARTITION BY imf_id)) /
            NULLIF(STDDEV(taux_realisation_collecte_pct)                         OVER (PARTITION BY imf_id), 0)
        , 3)                                                          AS zscore_realisation

    FROM combined
),

-- Score global benchmark (pondéré : collecte 50%, recouvrement 50%)
final AS (
    SELECT
        z.*,

        -- Score composite [-3, +3] — positif = bonne performance
        ROUND(
            0.40 * COALESCE(z.zscore_collecte,    0)
          + 0.30 * COALESCE(z.zscore_realisation,  0)
          - 0.30 * COALESCE(z.zscore_par30,        0)   -- inversé : PAR30 élevé = mauvais
        , 3)                                                          AS score_benchmark_global,

        -- Rang dans l'IMF (1 = meilleure agence)
        RANK() OVER (
            PARTITION BY z.imf_id
            ORDER BY
                0.40 * COALESCE(z.zscore_collecte,   0)
              + 0.30 * COALESCE(z.zscore_realisation, 0)
              - 0.30 * COALESCE(z.zscore_par30,       0)
            DESC
        )                                                             AS rang_imf

    FROM z_scores z
)

SELECT
    *,
    NOW() AS created_at
FROM final
ORDER BY imf_id, rang_imf
