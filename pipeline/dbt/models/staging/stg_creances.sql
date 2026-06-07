-- stg_creances.sql
-- Nettoyage des créances ingérées depuis les exports CBS.
-- Calcule les jours de retard et la classe COBAC provisoire.

WITH source AS (
    SELECT * FROM app.creances
),

enriched AS (
    SELECT
        id,
        uid,
        imf_id,
        client_id,
        client_id_externe,
        agence_id,
        pret_id_externe,
        montant_decaisse,
        montant_impaye,
        date_decaissement,
        date_derniere_echeance,
        date_dernier_paiement,
        statut_creance,
        classe_cobac,
        taux_provision,
        montant_provision,
        created_at,
        updated_at,

        -- Retard calculé à la date du run dbt
        GREATEST(0, CURRENT_DATE - date_derniere_echeance)    AS jours_retard,

        -- Classe COBAC recalculée (source de vérité dbt vs app)
        CASE
            WHEN CURRENT_DATE - date_derniere_echeance <  30  THEN 'A'
            WHEN CURRENT_DATE - date_derniere_echeance <  90  THEN 'B'
            WHEN CURRENT_DATE - date_derniere_echeance < 180  THEN 'C'
            WHEN CURRENT_DATE - date_derniere_echeance < 360  THEN 'D'
            ELSE 'E'
        END                                                   AS classe_cobac_dbt,

        -- Taux provision COBAC
        CASE
            WHEN CURRENT_DATE - date_derniere_echeance <  30  THEN 0.00
            WHEN CURRENT_DATE - date_derniere_echeance <  90  THEN 0.20
            WHEN CURRENT_DATE - date_derniere_echeance < 180  THEN 0.50
            WHEN CURRENT_DATE - date_derniere_echeance < 360  THEN 0.80
            ELSE 1.00
        END                                                   AS taux_provision_dbt,

        -- PAR flags
        (CURRENT_DATE - date_derniere_echeance >= 30)         AS is_par30,
        (CURRENT_DATE - date_derniere_echeance >= 60)         AS is_par60,
        (CURRENT_DATE - date_derniere_echeance >= 90)         AS is_par90,
        (CURRENT_DATE - date_derniere_echeance >= 180)        AS is_par180,

        -- Flags qualité CBS
        (montant_impaye > montant_decaisse)                   AS flag_impaye_sup_decaisse,
        (date_derniere_echeance < date_decaissement)          AS flag_date_incoherente,
        (client_id_externe IS NULL)                           AS flag_client_manquant

    FROM source
    WHERE statut_creance NOT IN ('SOLDEE', 'ABANDONNEE')
)

SELECT * FROM enriched
