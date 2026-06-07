-- ml_psi_reference.sql
-- Distribution de référence des scores MCRS pour le calcul PSI (OBJ-7).
-- Matérialisé une seule fois sur la première exécution, puis figé.
-- Le DAG dag_ml_scoring compare la distribution courante à cette référence.

WITH premier_scoring AS (
    -- Prend le premier batch de scores comme distribution de référence
    SELECT
        scored_at::date AS date_scoring,
        score_mcrs
    FROM ml.client_scores
    WHERE scored_at = (SELECT MIN(scored_at) FROM ml.client_scores)
),

distribution AS (
    SELECT
        COUNT(CASE WHEN score_mcrs < 0.30  THEN 1 END)::float /
            NULLIF(COUNT(*), 0)     AS p_faible,
        COUNT(CASE WHEN score_mcrs >= 0.30
                    AND score_mcrs < 0.55 THEN 1 END)::float /
            NULLIF(COUNT(*), 0)     AS p_modere,
        COUNT(CASE WHEN score_mcrs >= 0.55
                    AND score_mcrs < 0.75 THEN 1 END)::float /
            NULLIF(COUNT(*), 0)     AS p_eleve,
        COUNT(CASE WHEN score_mcrs >= 0.75 THEN 1 END)::float /
            NULLIF(COUNT(*), 0)     AS p_critique,
        MIN(date_scoring)           AS date_reference,
        COUNT(*)                    AS n_clients_reference
    FROM premier_scoring
)

SELECT
    *,
    NOW() AS created_at
FROM distribution
