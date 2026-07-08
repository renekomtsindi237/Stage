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
    -- cf. commentaire équivalent dans features_client.sql : stg_clients
    -- dépend de raw.export_cbs, jamais alimenté (pas d'ingestion CBS réelle
    -- configurée) — app.clients_informels est la table client réelle.
    SELECT
        i.code AS imf_code,
        ci.client_id_externe,
        ci.zone_id
    FROM {{ source('app', 'clients_informels') }} ci
    JOIN {{ source('app', 'imf') }} i ON i.id = ci.imf_id
),

produit_principal AS (
    -- Produit vendu le plus fréquemment (ou déclaré principal)
    -- app.clients_informels porte imf_id (FK), pas imf_code — résolu via
    -- app.imf comme partout ailleurs dans le pipeline (aucune colonne
    -- imf_code n'existe sur les tables app.* elles-mêmes).
    SELECT DISTINCT ON (cap.client_id)
        i.code AS imf_code,
        ci.client_id_externe,
        pg.code_produit,
        pg.categorie,
        ci.zone_id
    FROM {{ source('app', 'client_activites_produits') }} cap
    JOIN {{ source('app', 'clients_informels') }} ci ON cap.client_id = ci.id
    JOIN {{ source('app', 'produits_generiques') }} pg ON cap.produit_id = pg.id
    JOIN {{ source('app', 'imf') }} i ON i.id = ci.imf_id
    ORDER BY cap.client_id, cap.est_produit_principal DESC, cap.revenu_mensuel_produit DESC NULLS LAST
),

prix_produit AS (
    -- stg_prix_produits dépend de raw.prix_marche : aucune ingestion de prix
    -- marché n'est configurée à ce jour (pas de scraping/API prix connecté,
    -- au même titre que MTN/Orange/CRB — limites déjà documentées). Plutôt
    -- que de bloquer tout le feature store sur une source qui n'existe pas
    -- encore, ces colonnes restent NULL ici — FastAPI les impute déjà à ses
    -- médianes sectorielles (FEATURE_DEFAULTS dans mcrs_model.py), donc
    -- aucune régression de comportement au scoring. À rebrancher sur
    -- {{ '{{ ref(\'stg_prix_produits\') }}' }} dès qu'une vraie source de
    -- prix existe.
    SELECT
        NULL::TEXT    AS code_produit,
        NULL::TEXT    AS zone_id,
        NULL::NUMERIC AS prix_moy_90j,
        NULL::NUMERIC AS prix_stddev_90j,
        NULL::NUMERIC AS tendance_prix_30j,
        NULL::NUMERIC AS prix_moy_30j,
        NULL::NUMERIC AS prix_lag_30j,
        NULL::NUMERIC AS prix_lag_90j
    WHERE FALSE
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
