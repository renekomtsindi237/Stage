-- ml_features_client.sql
-- Feature store ML : jointure des 3 composantes CRS + RPS + CSI + features camerounaises.
-- Table quotidienne utilisée par dag_ml_scoring pour alimenter le scorer FastAPI.
-- 30 features : 7 CRS + 6 RPS + 13 CSI + 4 CAMEROON (model_xgboost.pkl v2).

WITH comportement AS (
    SELECT * FROM {{ ref('int_comportement_collecte') }}
    WHERE date_feature = CURRENT_DATE
),

risque AS (
    SELECT * FROM {{ ref('int_risque_credit') }}
    WHERE date_feature = CURRENT_DATE
),

contexte AS (
    SELECT * FROM {{ ref('int_contexte_externe') }}
    WHERE date_contexte = CURRENT_DATE
),

clients AS (
    SELECT * FROM {{ ref('stg_clients') }}
),

-- Profils régionaux camerounais (table de référence statique)
region_profiles AS (
    SELECT *
    FROM (VALUES
        ('REG01', 1.15, 0.45, 2),
        ('REG02', 1.00, 0.75, 1),
        ('REG03', 1.20, 0.30, 1),
        ('REG04', 1.45, 0.25, 0),
        ('REG05', 0.90, 0.85, 3),
        ('REG06', 1.30, 0.35, 0),
        ('REG07', 1.25, 0.55, 2),
        ('REG08', 1.00, 0.65, 2),
        ('REG09', 1.10, 0.35, 1),
        ('REG10', 1.20, 0.50, 3)
    ) AS t(region_id, risque_regional, taux_penetration_mobile, zone_agroclimatique)
),

-- Saison de récolte active selon région et mois courant (calendrier agricole)
saison_recolte AS (
    SELECT region_id,
           CASE
             -- Extrême-Nord (REG04) et Nord (REG06) : coton/sorgho sep-nov
             WHEN region_id IN ('REG04','REG06') AND EXTRACT(MONTH FROM CURRENT_DATE) BETWEEN 9  AND 11 THEN 1
             -- Centre (REG02), Est (REG03), Sud (REG09) : cacao grande saison oct-déc
             WHEN region_id IN ('REG02','REG03','REG09') AND EXTRACT(MONTH FROM CURRENT_DATE) BETWEEN 10 AND 12 THEN 1
             -- Centre (REG02) : cacao mi-saison mar-mai
             WHEN region_id = 'REG02' AND EXTRACT(MONTH FROM CURRENT_DATE) BETWEEN 3  AND 5  THEN 1
             -- Nord-Ouest (REG07) et Ouest (REG08) : café arabica nov-fév
             WHEN region_id IN ('REG07','REG08') AND (EXTRACT(MONTH FROM CURRENT_DATE) >= 11 OR EXTRACT(MONTH FROM CURRENT_DATE) <= 2) THEN 1
             -- Highlands maïs (REG08) : jul-aoû
             WHEN region_id = 'REG08' AND EXTRACT(MONTH FROM CURRENT_DATE) BETWEEN 7  AND 8  THEN 1
             -- Littoral (REG05) et Sud-Ouest (REG10) : plantain toute l'année
             WHEN region_id IN ('REG05','REG10') THEN 1
             -- Adamaoua maïs (REG01) : jul-aoû
             WHEN region_id = 'REG01' AND EXTRACT(MONTH FROM CURRENT_DATE) BETWEEN 7  AND 8  THEN 1
             ELSE 0
           END AS saison_recolte_active
    FROM (VALUES ('REG01'),('REG02'),('REG03'),('REG04'),('REG05'),
                 ('REG06'),('REG07'),('REG08'),('REG09'),('REG10')) AS r(region_id)
)

SELECT
    cl.client_id_externe,
    cl.imf_id,
    cl.region_id,
    CURRENT_DATE                                               AS date_feature,

    -- ── CRS features (7) ───────────────────────────────────────────────────
    COALESCE(cc.regularite_collecte_pct,       0.0)  AS regularite_collecte_pct,
    COALESCE(cc.nb_collectes_30j,               0.0)  AS nb_collectes_30j,
    COALESCE(cc.montant_moyen_collecte,         0.0)  AS montant_moyen_collecte,
    COALESCE(cc.tendance_collecte_30j,          0.0)  AS tendance_collecte_30j,
    COALESCE(cc.coefficient_variation_collecte, 0.0)  AS coefficient_variation_collecte,
    COALESCE(cc.nb_semaines_sans_collecte,      0.0)  AS nb_semaines_sans_collecte,
    COALESCE(cc.rang_collecte_agence,           0.5)  AS rang_collecte_agence,

    -- ── RPS features (6) ───────────────────────────────────────────────────
    COALESCE(rc.jours_retard_actuel,            0.0)  AS jours_retard_actuel,
    COALESCE(rc.nb_incidents_paiement_12m,      0.0)  AS nb_incidents_paiement_12m,
    COALESCE(rc.taux_remboursement_historique,  0.5)  AS taux_remboursement_historique,
    COALESCE(rc.ratio_creance_revenus,          0.0)  AS ratio_creance_revenus,
    COALESCE(rc.nb_reechelonnements,            0.0)  AS nb_reechelonnements,
    COALESCE(rc.score_rps_precedent,            0.5)  AS score_rps_precedent,

    -- ── CSI features (13) ──────────────────────────────────────────────────
    COALESCE(ce.prix_moyen_30j,                0.0)  AS prix_moyen_30j,
    COALESCE(ce.volatilite_prix_30j,           0.0)  AS volatilite_prix_30j,
    COALESCE(ce.saisonnalite_prix,             1.0)  AS saisonnalite_prix,
    COALESCE(ce.precipitations_30j,            0.0)  AS precipitations_30j,
    COALESCE(ce.indice_secheresse,             0.0)  AS indice_secheresse,
    COALESCE(ce.inflation,                     3.0)  AS inflation,
    COALESCE(ce.taux_beac,                     4.5)  AS taux_beac,
    COALESCE(ce.ipc,                         100.0)  AS ipc,
    COALESCE(ce.chomage,                       3.5)  AS chomage,
    COALESCE(ce.indice_resilience,             0.5)  AS indice_resilience,
    COALESCE(rc.capacite_remboursement,        1.0)  AS capacite_remboursement,
    COALESCE(cc.ratio_collecte_credit,         0.0)  AS ratio_collecte_credit,
    COALESCE(ce.score_diversification_produits,0.5)  AS score_diversification_produits,

    -- ── Features camerounaises (4) — zones agroclimatiques, mobile money ───
    COALESCE(rp.risque_regional,          1.12) AS risque_regional,
    COALESCE(rp.taux_penetration_mobile,  0.50) AS taux_penetration_mobile,
    COALESCE(rp.zone_agroclimatique,      1)    AS zone_agroclimatique,
    COALESCE(sr.saison_recolte_active,    0)    AS saison_recolte_active,

    -- Métadonnées
    NOW()                                             AS created_at

FROM clients cl
LEFT JOIN comportement   cc ON cc.client_id  = cl.id
                            AND cc.imf_id    = cl.imf_id
LEFT JOIN risque         rc ON rc.client_id  = cl.id
                            AND rc.imf_id    = cl.imf_id
LEFT JOIN contexte       ce ON ce.client_id  = cl.id
                            AND ce.imf_id    = cl.imf_id
LEFT JOIN region_profiles rp ON rp.region_id = cl.region_id
LEFT JOIN saison_recolte  sr ON sr.region_id = cl.region_id

-- Seuls les clients avec au moins une créance active
WHERE EXISTS (
    SELECT 1
    FROM {{ ref('stg_creances') }} cr
    WHERE cr.client_id = cl.id
)
