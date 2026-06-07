package cm.imf.pipeline.service;

import java.time.LocalDate;

/**
 * Contrat du service d'export PDF.
 * Génère des rapports PDF via OpenPDF (Apache 2.0).
 */
public interface IPdfExportService {

    /**
     * Export PDF des collectes pour une période donnée.
     */
    byte[] exportCollectesPDF(LocalDate dateDebut, LocalDate dateFin);

    /**
     * Export PDF des prêts en retard (PAR).
     */
    byte[] exportPretsEnRetardPDF();

    /**
     * Rapport KPI PDF synthèse pour une période donnée.
     */
    byte[] exportKpiRapportPDF(LocalDate dateDebut, LocalDate dateFin);
}
