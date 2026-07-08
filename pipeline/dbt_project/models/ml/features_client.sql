{{
    config(
        materialized='incremental',
        unique_key=['imf_id', 'client_id_externe', 'periode_debut', 'version_features'],
        on_schema_change='append_new_columns'
    )
}}

-- Feature store final MCRS — jointure de toutes les couches de features
-- Grain : un client × une période de calcul

WITH comportemental AS (
    SELECT *
    FROM {{ ref('int_profil_recouvrement_client') }}
),

externe AS (
    SELECT *
    FROM {{ ref('feat_client_externe') }}
),

clients AS (
    -- stg_clients dépend de raw.export_cbs, jamais alimenté (aucune
    -- ingestion CBS réelle configurée à ce jour) — la table client réelle
    -- avec des données est app.clients_informels, jointe à app.imf pour
    -- imf_code (clients_informels ne porte que imf_id). nb_collectes_total/
    -- montant_total_collectes ne sont pas utilisées plus loin dans ce
    -- modèle (déjà couvertes par nb_collectes_12m/montant_total_collectes_12m
    -- issues de int_profil_recouvrement_client) — retirées plutôt que
    -- recalculées inutilement.
    SELECT
        i.code                                          AS imf_code,
        ci.client_id_externe,
        ci.zone_id,
        ci.secteur_principal,
        ci.revenu_mensuel_estime,
        EXTRACT(DAY FROM NOW() - ci.created_at)::INTEGER AS anciennete_jours
    FROM {{ source('app', 'clients_informels') }} ci
    JOIN {{ source('app', 'imf') }} i ON i.id = ci.imf_id
),

-- Géospatial (distance marché — app.agences n'a pas de latitude/longitude,
-- contrairement à ce que ce modèle supposait ; distance_agence_km reste
-- structurellement NULL tant que cette colonne n'existe pas sur agences)
geo AS (
    -- QUALIFY n'existe pas en Postgres (syntaxe Snowflake/BigQuery) — remplacé
    -- par une sous-requête + ROW_NUMBER/WHERE. imf_code résolu via app.imf
    -- (clients_informels ne porte que imf_id), même remède que les CTE
    -- ci-dessus et que feat_client_externe.sql.
    SELECT imf_code, client_id_externe, distance_agence_km, distance_marche_km
    FROM (
        SELECT
            i.code AS imf_code,
            ci.client_id_externe,
            NULL::NUMERIC AS distance_agence_km,
            ROUND(SQRT(
                POW((ci.latitude_activite  - ml.latitude)  * 111, 2) +
                POW((ci.longitude_activite - ml.longitude) * 111 * COS(RADIANS(ci.latitude_activite)), 2)
            )::NUMERIC, 2) AS distance_marche_km,
            -- Postgres n'autorise pas la référence à un alias du même SELECT
            -- dans un ORDER BY de window function : ré-écrit l'expression.
            ROW_NUMBER() OVER (
                PARTITION BY i.code, ci.client_id_externe
                ORDER BY SQRT(
                    POW((ci.latitude_activite  - ml.latitude)  * 111, 2) +
                    POW((ci.longitude_activite - ml.longitude) * 111 * COS(RADIANS(ci.latitude_activite)), 2)
                ) NULLS LAST
            ) AS rn
        FROM {{ source('app', 'clients_informels') }} ci
        JOIN {{ source('app', 'imf') }} i ON i.id = ci.imf_id
        LEFT JOIN {{ source('app', 'marches_locaux') }} ml
            ON ci.zone_id = ml.zone_id
            AND ml.actif = TRUE
    ) ranked
    WHERE rn = 1
),

-- Nombre de produits vendus
nb_produits AS (
    SELECT
        i.code AS imf_code,
        ci.client_id_externe,
        COUNT(DISTINCT cap.produit_id) AS nb_produits_vendus
    FROM {{ source('app', 'clients_informels') }} ci
    JOIN {{ source('app', 'imf') }} i ON i.id = ci.imf_id
    JOIN {{ source('app', 'client_activites_produits') }} cap ON ci.id = cap.client_id
    GROUP BY i.code, ci.client_id_externe
),

assemblee AS (
    SELECT
        -- ml.features_client (V23) exige imf_id (FK), pas imf_code — les CTE
        -- ci-dessus utilisent toutes imf_code comme clé de jointure interne
        -- (héritée de int_profil_recouvrement_client/feat_client_externe),
        -- résolu ici en imf_id juste pour l'écriture finale.
        i.id AS imf_id,
        c.client_id_externe,
        COALESCE(e.periode_debut, CURRENT_DATE - INTERVAL '90 days')::DATE AS periode_debut,
        COALESCE(e.periode_fin,   CURRENT_DATE)::DATE                       AS periode_fin,

        -- ── Comportement collecte ─────────────────────────────────────────
        COALESCE(b.nb_collectes_12m, 0)             AS nb_collectes_12m,
        COALESCE(b.montant_total_collectes_12m, 0)  AS montant_total_collectes_12m,
        COALESCE(b.regularite_collecte_pct, 0)      AS regularite_collecte_pct,
        b.montant_moy_collecte,
        b.ecart_type_collecte,
        NULL::NUMERIC(8,4)                          AS tendance_collecte_3m,  -- calculé dbt window
        COALESCE(52 - b.nb_semaines_actives_12m, 52) AS nb_cycles_manques_12m,

        -- ── Comportement remboursement ────────────────────────────────────
        COALESCE(b.nb_creances_total, 0)            AS nb_remboursements_12m,
        COALESCE(b.taux_remboursement_pct, 0)       AS taux_remboursement_pct,
        COALESCE(b.jours_retard_moyen, 0)           AS jours_retard_moyen,
        b.jours_retard_max,
        COALESCE(b.nb_par90, 0)                     AS nb_incidents_paiement,
        COALESCE(b.montant_impaye_total, 0)         AS montant_impaye_courant,

        -- ── Profil client ─────────────────────────────────────────────────
        COALESCE(c.anciennete_jours, 0)             AS anciennete_client_jours,
        c.secteur_principal,
        COALESCE(np.nb_produits_vendus, 0)          AS nb_produits_vendus,
        c.revenu_mensuel_estime,

        -- ── Facteurs externes ─────────────────────────────────────────────
        e.prix_produit_principal_moy,
        e.volatilite_prix_produit,
        e.tendance_prix_30j,
        COALESCE(e.precipitation_moy_mm, 0)         AS precipitation_moy_mm,
        COALESCE(e.indice_secheresse_max, 'NORMAL') AS indice_secheresse_max,
        COALESCE(e.inflation_mensuelle_moy, 0)      AS inflation_mensuelle_moy,
        COALESCE(e.taux_directeur_beac, 0)          AS taux_directeur_beac,
        COALESCE(e.nb_evenements_negatifs, 0)       AS nb_evenements_negatifs,

        -- ── Géospatial ────────────────────────────────────────────────────
        g.distance_agence_km,
        g.distance_marche_km,
        NULL::NUMERIC(8,4)                          AS densite_agents_zone,

        -- ── Features dérivées ─────────────────────────────────────────────
        COALESCE(b.ratio_collecte_credit, 0)        AS ratio_collecte_credit,
        -- capacité remboursement = revenu - montant_impaye estimé mensuel
        GREATEST(
            COALESCE(c.revenu_mensuel_estime, 0)
            - COALESCE(b.montant_impaye_total / NULLIF(b.nb_creances_total, 0), 0),
            0
        )::NUMERIC(12,2)                            AS capacite_remboursement,
        -- Indice de résilience = diversification produits normalisée [0,1]
        ROUND(LEAST(COALESCE(np.nb_produits_vendus, 0) * 1.0 / 5, 1)::NUMERIC, 4) AS indice_resilience,

        '{{ var("version_features") }}'             AS version_features,
        NULL::TEXT                                  AS dag_run_id,
        NOW()                                       AS computed_at

    FROM clients c
    JOIN {{ source('app', 'imf') }} i ON i.code = c.imf_code
    LEFT JOIN comportemental b
        ON  c.imf_code         = b.imf_code
        AND c.client_id_externe = b.client_id_externe
    LEFT JOIN externe e
        ON  c.imf_code         = e.imf_code
        AND c.client_id_externe = e.client_id_externe
    LEFT JOIN geo g
        ON  c.imf_code         = g.imf_code
        AND c.client_id_externe = g.client_id_externe
    LEFT JOIN nb_produits np
        ON  c.imf_code         = np.imf_code
        AND c.client_id_externe = np.client_id_externe
)

SELECT
    {{ dbt_utils.generate_surrogate_key(['imf_id', 'client_id_externe', 'periode_debut', 'version_features']) }} AS feature_id,
    a.*
FROM assemblee a
