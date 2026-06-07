{{
    config(
        materialized='incremental',
        unique_key=['imf_code', 'client_id_externe', 'periode_debut'],
        on_schema_change='append_new_columns'
    )
}}

-- Features ML issues des données externes (prix, macro, météo)
-- Rejoint avec le produit principal du client

WITH clients AS (
    SELECT *
    FROM {{ ref('stg_clients') }}
),

produit_principal AS (
    -- Produit vendu le plus fréquemment (ou déclaré principal)
    SELECT DISTINCT ON (cap.client_id)
        ci.imf_code,
        ci.client_id_externe,
        pg.code_produit,
        pg.categorie,
        ci.zone_id
    FROM {{ source('app', 'client_activites_produits') }} cap
    JOIN {{ source('app', 'clients_informels') }} ci ON cap.client_id = ci.id
    JOIN {{ source('app', 'produits_generiques') }} pg ON cap.produit_id = pg.id
    ORDER BY cap.client_id, cap.est_produit_principal DESC, cap.revenu_mensuel_produit DESC NULLS LAST
),

prix_produit AS (
    -- Prix moyen, volatilité, tendance et lag sur 120 jours pour le produit principal
    SELECT
        pp.code_produit,
        pp.zone_id,
        CURRENT_DATE - INTERVAL '90 days'   AS periode_debut,
        CURRENT_DATE                          AS periode_fin,
        AVG(pp.prix_unitaire)
            FILTER (WHERE date_prix >= CURRENT_DATE - INTERVAL '90 days')  AS prix_moy_90j,
        STDDEV(pp.prix_unitaire)
            FILTER (WHERE date_prix >= CURRENT_DATE - INTERVAL '90 days')  AS prix_stddev_90j,
        -- Tendance linéaire (pente de régression sur les 30 derniers jours)
        REGR_SLOPE(prix_unitaire, EXTRACT(EPOCH FROM date_prix))
            FILTER (WHERE date_prix >= CURRENT_DATE - INTERVAL '30 days')  AS tendance_prix_30j,
        AVG(pp.prix_unitaire)
            FILTER (WHERE date_prix >= CURRENT_DATE - INTERVAL '30 days')  AS prix_moy_30j,
        -- Lag 30j : prix moyen de la période 31–60 jours en arrière
        AVG(pp.prix_unitaire)
            FILTER (WHERE date_prix BETWEEN CURRENT_DATE - INTERVAL '60 days'
                                        AND CURRENT_DATE - INTERVAL '31 days') AS prix_lag_30j,
        -- Lag 90j : prix moyen de la période 91–120 jours en arrière
        AVG(pp.prix_unitaire)
            FILTER (WHERE date_prix BETWEEN CURRENT_DATE - INTERVAL '120 days'
                                        AND CURRENT_DATE - INTERVAL '91 days') AS prix_lag_90j
    FROM {{ ref('stg_prix_produits') }} pp
    WHERE date_prix >= CURRENT_DATE - INTERVAL '120 days'  -- étendu à 120j pour les lags
    GROUP BY pp.code_produit, pp.zone_id
),

meteo_zone AS (
    SELECT
        zone_id,
        AVG(precipitation_mm)  AS precipitation_moy_30j,
        MAX(indice_secheresse) AS indice_secheresse_max,  -- ordinal alphabétique suffit pour flag
        COUNT(*) FILTER (WHERE indice_secheresse NOT IN ('NORMAL')) AS nb_jours_anomalie_meteo
    FROM {{ ref('stg_meteo') }}
    WHERE date_observation >= CURRENT_DATE - INTERVAL '30 days'
    GROUP BY zone_id
),

macro AS (
    SELECT
        AVG(valeur) FILTER (WHERE indicateur = 'TAUX_INFLATION_MENSUEL')  AS inflation_moy_3m,
        MAX(valeur) FILTER (WHERE indicateur = 'TAUX_DIRECTEUR_BEAC')     AS taux_directeur_beac,
        AVG(valeur) FILTER (WHERE indicateur = 'INDICE_PRIX_CONSOMMATION') AS ipc_moy_3m
    FROM {{ ref('stg_indicateurs_macro') }}
    WHERE date_observation >= CURRENT_DATE - INTERVAL '90 days'
),

evenements AS (
    SELECT
        zone_id,
        COUNT(*) FILTER (WHERE impact_collecte = 'NEGATIF')  AS nb_evenements_negatifs_30j,
        COUNT(*) FILTER (WHERE impact_collecte = 'POSITIF')  AS nb_evenements_positifs_30j
    FROM {{ source('app', 'evenements_exterieurs') }}
    WHERE date_debut >= CURRENT_DATE - INTERVAL '30 days'
      AND (date_fin IS NULL OR date_fin >= CURRENT_DATE - INTERVAL '30 days')
    GROUP BY zone_id
)

SELECT
    c.imf_code,
    c.client_id_externe,
    (CURRENT_DATE - INTERVAL '90 days')::DATE  AS periode_debut,
    CURRENT_DATE                                AS periode_fin,

    -- Prix produit principal
    ROUND(pp_prix.prix_moy_90j::NUMERIC, 4)    AS prix_produit_principal_moy,
    ROUND(pp_prix.prix_stddev_90j::NUMERIC, 4) AS volatilite_prix_produit,
    ROUND(pp_prix.tendance_prix_30j::NUMERIC, 6) AS tendance_prix_30j,
    ROUND(pp_prix.prix_moy_30j::NUMERIC, 4)    AS prix_moy_30j,
    -- Lag features : comparaison inter-périodes pour détecter les tendances retardées
    ROUND(COALESCE(pp_prix.prix_lag_30j, pp_prix.prix_moy_30j)::NUMERIC, 4)  AS prix_lag_30j,
    ROUND(COALESCE(pp_prix.prix_lag_90j, pp_prix.prix_moy_90j)::NUMERIC, 4)  AS prix_lag_90j,

    -- Météo
    ROUND(COALESCE(mz.precipitation_moy_30j, 0)::NUMERIC, 2) AS precipitation_moy_mm,
    COALESCE(mz.indice_secheresse_max, 'NORMAL')               AS indice_secheresse_max,
    COALESCE(mz.nb_jours_anomalie_meteo, 0)                    AS nb_jours_anomalie_meteo,

    -- Macro
    ROUND(COALESCE(m.inflation_moy_3m, 0)::NUMERIC, 4)        AS inflation_mensuelle_moy,
    ROUND(COALESCE(m.taux_directeur_beac, 0)::NUMERIC, 4)     AS taux_directeur_beac,
    ROUND(COALESCE(m.ipc_moy_3m, 0)::NUMERIC, 4)              AS ipc_moy_3m,

    -- Événements
    COALESCE(ev.nb_evenements_negatifs_30j, 0) AS nb_evenements_negatifs,
    COALESCE(ev.nb_evenements_positifs_30j, 0) AS nb_evenements_positifs,

    -- Produit principal
    pp.code_produit AS produit_principal_code,
    pp.categorie    AS categorie_produit_principal,

    '{{ var("version_features") }}'             AS version_features,
    NOW()                                       AS computed_at

FROM clients c
LEFT JOIN produit_principal pp
    ON  c.imf_code         = pp.imf_code
    AND c.client_id_externe = pp.client_id_externe
LEFT JOIN prix_produit pp_prix
    ON  pp.code_produit = pp_prix.code_produit
    AND COALESCE(c.zone_id, 'YAOUNDE') = pp_prix.zone_id
LEFT JOIN meteo_zone mz
    ON  COALESCE(c.zone_id, 'YAOUNDE') = mz.zone_id
LEFT JOIN evenements ev
    ON  COALESCE(c.zone_id, 'YAOUNDE') = ev.zone_id
CROSS JOIN macro m
