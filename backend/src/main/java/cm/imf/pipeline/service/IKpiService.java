package cm.imf.pipeline.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Contrat du service d'indicateurs clés de performance (KPI).
 * Lit depuis les schémas dw.* et staging.* via JdbcTemplate.
 * Toutes les méthodes sont mises en cache Redis.
 */
public interface IKpiService {

    /**
     * Portefeuille À Risque (PAR30 / PAR90) par zone et par agence pour une période.
     *
     * @return liste de maps {nom_agence, date_valeur, encours_par30, encours_par90, ...}
     */
    List<Map<String, Object>> getParStats(LocalDate dateDebut, LocalDate dateFin);

    /**
     * Volume des collectes par canal et par agence pour une période.
     *
     * @return liste de maps {date_valeur, canal, nom_agence, nb_collectes, montant_total}
     */
    List<Map<String, Object>> getCollecteStats(LocalDate dateDebut, LocalDate dateFin);

    /**
     * Résumé du tableau de bord : KPI agrégés des 30 derniers jours.
     *
     * @return map {totalCollectes, nbCollectes, encoursPar30, encoursPar90, nbAlertesActives, ...}
     */
    Map<String, Object> getDashboardSummary();
}
