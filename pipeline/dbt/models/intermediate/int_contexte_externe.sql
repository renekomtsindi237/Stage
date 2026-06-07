-- int_contexte_externe.sql
-- Features CSI (Client Solvency Index) : facteurs externes macroéconomiques,
-- météo et prix des produits génériques liés à l'activité du client.

WITH prix AS (
    SELECT * FROM {{ ref('stg_prix_produits') }}
    WHERE date_prix >= CURRENT_DATE - 30
      AND flag_prix_aberrant = FALSE
),

-- Prix moyen pondéré par les produits actifs du client
prix_client AS (
    SELECT
        cap.client_id,
        AVG(p.prix_moyen_30j)        AS prix_moyen_30j,
        AVG(p.volatilite_30j)        AS volatilite_prix_30j,
        AVG(p.indice_saisonnalite)   AS saisonnalite_prix,
        COUNT(DISTINCT p.produit_id) AS nb_produits_avec_prix
    FROM app.client_activite_produits cap
    JOIN prix p ON p.produit_id = cap.produit_id
    WHERE p.date_prix = (
        SELECT MAX(p2.date_prix)
        FROM {{ ref('stg_prix_produits') }} p2
        WHERE p2.produit_id = p.produit_id
          AND p2.date_prix  >= CURRENT_DATE - 30
    )
    GROUP BY cap.client_id
),

-- Données météo (précipitations et sécheresse par région)
meteo AS (
    SELECT
        region_id,
        AVG(precipitations_mm)       AS precipitations_30j,
        MAX(indice_secheresse)       AS indice_secheresse
    FROM app.donnees_meteo
    WHERE date_mesure >= CURRENT_DATE - 30
    GROUP BY region_id
),

-- Indicateurs macro BEAC/INS (dernière valeur disponible)
macro AS (
    SELECT
        inflation,
        taux_beac,
        ipc,
        chomage
    FROM app.indicateurs_macro
    ORDER BY date_publication DESC
    LIMIT 1
),

-- Indice de résilience = combinaison régularité collecte + diversification produits
resilience AS (
    SELECT
        cap.client_id,
        COUNT(DISTINCT cap.produit_id)::float /
            NULLIF(MAX(tot.nb_produits), 0)     AS score_diversification_produits,
        LEAST(
            (COUNT(DISTINCT cap.produit_id)::float / NULLIF(MAX(tot.nb_produits), 0)) * 0.5
            + COALESCE(col_reg.regularite, 0) * 0.5
        , 1.0)                                   AS indice_resilience
    FROM app.client_activite_produits cap
    CROSS JOIN (SELECT COUNT(*) AS nb_produits FROM app.produits_generiques WHERE actif) tot
    LEFT JOIN (
        SELECT client_id,
               ROUND(COUNT(DISTINCT DATE_TRUNC('week', date_collecte))::numeric / 13, 2)
                   AS regularite
        FROM app.collectes_terrain
        WHERE date_collecte >= CURRENT_DATE - 90
          AND statut = 'CONFIRMEE'
        GROUP BY client_id
    ) col_reg ON col_reg.client_id = cap.client_id::varchar
    GROUP BY cap.client_id
)

SELECT
    cl.id                            AS client_id,
    cl.imf_id,
    cl.region_id,
    CURRENT_DATE                     AS date_contexte,

    -- Prix produits
    COALESCE(pc.prix_moyen_30j,    0)   AS prix_moyen_30j,
    COALESCE(pc.volatilite_prix_30j, 0) AS volatilite_prix_30j,
    COALESCE(pc.saisonnalite_prix, 1)   AS saisonnalite_prix,

    -- Météo / climat
    COALESCE(m.precipitations_30j,  0)  AS precipitations_30j,
    COALESCE(m.indice_secheresse,   0)  AS indice_secheresse,

    -- Macro
    COALESCE(mac.inflation,        3.0) AS inflation,
    COALESCE(mac.taux_beac,        4.5) AS taux_beac,
    COALESCE(mac.ipc,            100.0) AS ipc,
    COALESCE(mac.chomage,          3.5) AS chomage,

    -- Résilience et diversification
    COALESCE(r.indice_resilience,  0.5) AS indice_resilience,
    COALESCE(r.score_diversification_produits, 0.5) AS score_diversification_produits

FROM app.clients_informels cl
LEFT JOIN prix_client pc       ON pc.client_id  = cl.id
LEFT JOIN meteo m              ON m.region_id   = cl.region_id
CROSS JOIN macro mac
LEFT JOIN resilience r         ON r.client_id   = cl.id
WHERE cl.statut != 'ARCHIVE'
