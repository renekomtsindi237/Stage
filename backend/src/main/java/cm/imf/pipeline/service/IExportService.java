package cm.imf.pipeline.service;

import java.time.LocalDate;

/**
 * Contrat du service d'export CSV.
 * Lecture depuis les schémas dw.* et staging.* via JdbcTemplate.
 */
public interface IExportService {

    /**
     * Export CSV des collectes pour une période donnée.
     * Retourne le contenu CSV en String (à streamer dans la réponse HTTP).
     */
    String exportCollectesCSV(LocalDate dateDebut, LocalDate dateFin);

    /**
     * Export CSV des prêts en retard (PAR).
     */
    String exportPretsEnRetardCSV();
}
