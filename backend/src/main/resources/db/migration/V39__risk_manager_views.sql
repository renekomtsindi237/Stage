-- ============================================================
-- V39 : Vues Risk Manager — PAR et concentration sectorielle
--
-- v_par_par_imf         — PAR30 / PAR60 / PAR90 par IMF
-- v_concentration_risque — exposition par secteur d'activité
-- ============================================================

CREATE OR REPLACE VIEW app.v_par_par_imf AS
SELECT
    imf_id,
    COUNT(*) FILTER (WHERE jours_retard > 30)   AS encours_par30,
    COUNT(*) FILTER (WHERE jours_retard > 60)   AS encours_par60,
    COUNT(*) FILTER (WHERE jours_retard > 90)   AS encours_par90,
    SUM(montant_en_retard)                          AS total_impaye,
    SUM(montant_en_retard) FILTER (WHERE jours_retard > 30) AS montant_par30,
    SUM(montant_en_retard) FILTER (WHERE jours_retard > 60) AS montant_par60,
    SUM(montant_en_retard) FILTER (WHERE jours_retard > 90) AS montant_par90
FROM app.alertes_impayes
WHERE statut_alerte = 'ACTIVE'
GROUP BY imf_id;

CREATE OR REPLACE VIEW app.v_concentration_risque AS
SELECT
    imf_id,
    COALESCE(secteur_activite, 'NON_RENSEIGNE') AS secteur_activite,
    COUNT(*)                                     AS nb_dossiers,
    SUM(montant_demande)                         AS exposition_totale,
    ROUND(
        100.0 * SUM(montant_demande)
            / NULLIF(SUM(SUM(montant_demande)) OVER (PARTITION BY imf_id), 0),
        2
    )                                            AS pct_portefeuille
FROM app.dossiers_credit
WHERE statut IN ('APPROUVE', 'DEBLOQUE')
GROUP BY imf_id, secteur_activite;
