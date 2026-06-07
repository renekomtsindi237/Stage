-- stg_collectes_terrain.sql
-- Nettoyage et typage des collectes terrain (app → staging).
-- Filtre les doublons, valide les montants et les coordonnées GPS.

WITH source AS (
    SELECT * FROM app.collectes_terrain
),

cleaned AS (
    SELECT
        id,
        uid,
        id_collecte_mobile,
        agent_id,
        imf_id,
        client_id,
        pret_id,
        date_collecte,
        montant_collecte,
        canal_paiement,
        reference_transaction,
        observation,
        statut,
        latitude,
        longitude,
        created_at,
        updated_at,

        -- Flags qualité
        (montant_collecte <= 0)                               AS flag_montant_negatif,
        (latitude IS NOT NULL AND (latitude < -90 OR latitude > 90))  AS flag_gps_invalide,
        (created_at > NOW())                                  AS flag_date_future,

        -- Enrichissement
        EXTRACT(DOW FROM date_collecte)                       AS jour_semaine,
        EXTRACT(MONTH FROM date_collecte)                     AS mois,
        CASE canal_paiement
            WHEN 'ESPECES'          THEN 'PHYSIQUE'
            WHEN 'VIREMENT'         THEN 'BANCAIRE'
            ELSE 'MOBILE_MONEY'
        END                                                   AS famille_canal

    FROM source
    WHERE statut IN ('CONFIRMEE', 'SOUMISE')
      AND montant_collecte > 0
)

SELECT * FROM cleaned
