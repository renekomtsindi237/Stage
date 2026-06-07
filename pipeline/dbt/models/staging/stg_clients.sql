-- stg_clients.sql
-- Nettoyage des profils clients informels.
-- Normalise les données d'identité et lie les activités économiques.

WITH clients AS (
    SELECT * FROM app.clients_informels
),

activites AS (
    SELECT
        client_id,
        STRING_AGG(produit_id::text, ',') AS produits_ids,
        COUNT(*)                           AS nb_produits_actifs
    FROM app.client_activite_produits
    GROUP BY client_id
),

final AS (
    SELECT
        c.id,
        c.imf_id,
        c.client_id_externe,
        c.nom_complet,
        c.telephone,
        c.region_id,
        c.agence_id,
        c.date_naissance,
        c.secteur_activite,
        c.niveau_kyc,
        c.statut,
        c.created_at,
        c.updated_at,

        -- Enrichissement activités
        COALESCE(a.nb_produits_actifs, 0)                     AS nb_produits_actifs,
        COALESCE(a.produits_ids, '')                           AS produits_ids,

        -- Âge calculé
        EXTRACT(YEAR FROM AGE(CURRENT_DATE, c.date_naissance)) AS age_ans,

        -- Flags qualité
        (c.telephone IS NULL OR LENGTH(c.telephone) < 9)      AS flag_telephone_invalide,
        (c.region_id IS NULL)                                  AS flag_region_manquante,
        (c.statut = 'SUSPENDU')                                AS flag_suspendu

    FROM clients c
    LEFT JOIN activites a ON a.client_id = c.id
    WHERE c.statut != 'ARCHIVE'
)

SELECT * FROM final
