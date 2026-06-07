-- stg_prix_produits.sql
-- Nettoyage et déduplication des prix de produits génériques.
-- Gère plusieurs sources (terrain, MINCOMMERCE, APIs) avec score fiabilité.

WITH source AS (
    SELECT * FROM app.prix_produits_generiques
),

dedup AS (
    -- Garde le prix le plus fiable par produit/zone/date
    SELECT DISTINCT ON (produit_id, zone_id, date_prix)
        id,
        produit_id,
        zone_id,
        imf_id,
        date_prix,
        prix_unitaire,
        unite,
        source_donnee,
        score_fiabilite,
        created_at
    FROM source
    WHERE prix_unitaire > 0
      AND score_fiabilite >= 1
    ORDER BY produit_id, zone_id, date_prix, score_fiabilite DESC
),

with_stats AS (
    SELECT
        d.*,

        -- Prix moyen sur 30 jours glissants par produit/zone
        AVG(d.prix_unitaire) OVER (
            PARTITION BY d.produit_id, d.zone_id
            ORDER BY d.date_prix
            ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
        )                                                     AS prix_moyen_30j,

        -- Volatilité (écart-type) sur 30 jours
        STDDEV(d.prix_unitaire) OVER (
            PARTITION BY d.produit_id, d.zone_id
            ORDER BY d.date_prix
            ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
        )                                                     AS volatilite_30j,

        -- Saisonnalité : ratio prix actuel / moyenne annuelle
        d.prix_unitaire / NULLIF(AVG(d.prix_unitaire) OVER (
            PARTITION BY d.produit_id, d.zone_id,
                         EXTRACT(MONTH FROM d.date_prix)
        ), 0)                                                 AS indice_saisonnalite,

        -- Flag prix aberrant (> 3 sigma)
        ABS(d.prix_unitaire - AVG(d.prix_unitaire) OVER (
            PARTITION BY d.produit_id, d.zone_id
            ORDER BY d.date_prix
            ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
        )) > 3 * NULLIF(STDDEV(d.prix_unitaire) OVER (
            PARTITION BY d.produit_id, d.zone_id
            ORDER BY d.date_prix
            ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
        ), 0)                                                 AS flag_prix_aberrant

    FROM dedup d
)

SELECT * FROM with_stats
