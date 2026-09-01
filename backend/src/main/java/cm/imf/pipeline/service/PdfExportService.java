package cm.imf.pipeline.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Génération de rapports PDF via OpenPDF (fork Apache 2.0 de iText 4).
 * Produit : collectes, prêts en retard, rapport KPI synthèse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService implements IPdfExportService {

    private final JdbcTemplate jdbcTemplate;
    private final IExportService exportService;

    @Value("${imf.pipeline.dw-schema:dw}")
    private String dwSchema;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;

    private static final Font TITLE_FONT  = new Font(Font.HELVETICA, 16, Font.BOLD, Color.DARK_GRAY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 9,  Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT   = new Font(Font.HELVETICA, 8,  Font.NORMAL, Color.BLACK);
    private static final Font LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
    private static final Color HEADER_BG  = new Color(33, 97, 140);
    private static final Color ROW_ALT    = new Color(235, 243, 251);

    // ── Collectes ─────────────────────────────────────────────────────────────

    public byte[] exportCollectesPDF(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
                SELECT d.date_valeur, fc.canal, da.nom_agence, dc.nom_client,
                       fc.reference_transaction, fc.montant, fc.statut
                FROM %s.fact_collectes fc
                JOIN %s.dim_date d ON fc.date_key = d.date_key
                JOIN %s.dim_agence da ON fc.id_agence = da.id_agence
                LEFT JOIN %s.dim_client dc ON fc.id_client_source = dc.id_client_source
                WHERE d.date_valeur BETWEEN ? AND ?
                ORDER BY d.date_valeur, da.nom_agence
                """.formatted(dwSchema, dwSchema, dwSchema, dwSchema);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                dateDebut.format(DateTimeFormatter.ISO_DATE),
                dateFin.format(DateTimeFormatter.ISO_DATE));

        String title = "Rapport Collectes — %s au %s".formatted(
                dateDebut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                dateFin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        String[] headers = {"Date", "Canal", "Agence", "Client", "Référence", "Montant", "Statut"};
        String[] keys    = {"date_valeur", "canal", "nom_agence", "nom_client",
                            "reference_transaction", "montant", "statut"};
        float[]  widths  = {1.2f, 1f, 1.5f, 2f, 2f, 1.2f, 1f};

        return buildTablePdf(title, headers, keys, widths, rows);
    }

    // ── Prêts en retard ───────────────────────────────────────────────────────

    public byte[] exportPretsEnRetardPDF() {
        String sql = """
                SELECT id_pret, nom_client, nom_agence, nom_produit,
                       montant_pret, solde_restant, statut_pret, jours_retard
                FROM %s.stg_prets
                WHERE statut_pret IN ('EN_RETARD', 'EN_RECOUVREMENT')
                ORDER BY jours_retard DESC
                """.formatted(stagingSchema);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        String title = "Prêts en Retard (PAR) — %s".formatted(
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        String[] headers = {"ID Prêt", "Client", "Agence", "Produit",
                            "Montant", "Solde Restant", "Statut", "Jours Retard"};
        String[] keys    = {"id_pret", "nom_client", "nom_agence", "nom_produit",
                            "montant_pret", "solde_restant", "statut_pret", "jours_retard"};
        float[]  widths  = {1.2f, 2f, 1.5f, 1.5f, 1.3f, 1.3f, 1.2f, 1.2f};

        return buildTablePdf(title, headers, keys, widths, rows);
    }

    // ── Rapport KPI ───────────────────────────────────────────────────────────

    public byte[] exportKpiRapportPDF(LocalDate dateDebut, LocalDate dateFin) {
        List<Map<String, Object>> parRows = loadParRows(dateDebut, dateFin);
        List<Map<String, Object>> collecteRows = loadCollecteRows(dateDebut, dateFin);
        return buildKpiPdf(dateDebut, dateFin, parRows, collecteRows);
    }

    private List<Map<String, Object>> loadParRows(LocalDate dateDebut, LocalDate dateFin) {
        try {
            String parSql = """
                    SELECT z.zone_id, z.nom_zone,
                           COUNT(*) FILTER (WHERE fp.jours_retard > 0)  AS nb_en_retard,
                           COUNT(*) FILTER (WHERE fp.jours_retard > 30) AS nb_par30,
                           COUNT(*) FILTER (WHERE fp.jours_retard > 90) AS nb_par90,
                           SUM(fp.solde_restant) AS encours_total
                    FROM %s.fact_remboursements fp
                    JOIN %s.dim_agence z ON fp.id_agence = z.id_agence
                    JOIN %s.dim_date d ON fp.date_key = d.date_key
                    WHERE d.date_valeur BETWEEN ? AND ?
                    GROUP BY z.zone_id, z.nom_zone
                    ORDER BY nb_par30 DESC
                    """.formatted(dwSchema, dwSchema, dwSchema);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(parSql,
                    dateDebut.format(DateTimeFormatter.ISO_DATE),
                    dateFin.format(DateTimeFormatter.ISO_DATE));
            if (!rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("Warehouse PAR indisponible pour le PDF KPI : {}", e.getMessage());
        }
        try {
            return jdbcTemplate.queryForList("""
                    SELECT 'Portefeuille' AS zone_id,
                           COALESCE(MAX(nom_client), 'Toutes agences') AS nom_zone,
                           COUNT(*) FILTER (WHERE jours_retard > 0)  AS nb_en_retard,
                           COUNT(*) FILTER (WHERE jours_retard > 30) AS nb_par30,
                           COUNT(*) FILTER (WHERE jours_retard > 90) AS nb_par90,
                           COALESCE(SUM(montant_impaye), 0) AS encours_total
                    FROM app.creances
                    """);
        } catch (Exception e) {
            log.warn("Repli app.creances indisponible pour le PDF KPI : {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> loadCollecteRows(LocalDate dateDebut, LocalDate dateFin) {
        try {
            String collecteSql = """
                    SELECT da.nom_agence, fc.canal,
                           COUNT(*) AS nb_collectes,
                           SUM(fc.montant) AS montant_total
                    FROM %s.fact_collectes fc
                    JOIN %s.dim_date d ON fc.date_key = d.date_key
                    JOIN %s.dim_agence da ON fc.id_agence = da.id_agence
                    WHERE d.date_valeur BETWEEN ? AND ?
                    GROUP BY da.nom_agence, fc.canal
                    ORDER BY da.nom_agence, fc.canal
                    """.formatted(dwSchema, dwSchema, dwSchema);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(collecteSql,
                    dateDebut.format(DateTimeFormatter.ISO_DATE),
                    dateFin.format(DateTimeFormatter.ISO_DATE));
            if (!rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("Warehouse collectes indisponible pour le PDF KPI : {}", e.getMessage());
        }
        try {
            return jdbcTemplate.queryForList("""
                    SELECT 'Terrain' AS nom_agence,
                           COALESCE(ct.canal_paiement::text, 'TERRAIN') AS canal,
                           COUNT(*) AS nb_collectes,
                           COALESCE(SUM(ct.montant_collecte), 0) AS montant_total
                    FROM app.collectes_terrain ct
                    WHERE ct.date_collecte BETWEEN ? AND ?
                    GROUP BY ct.canal_paiement
                    ORDER BY canal
                    """, dateDebut, dateFin);
        } catch (Exception e) {
            log.warn("Repli app.collectes_terrain indisponible pour le PDF KPI : {}", e.getMessage());
            return List.of();
        }
    }

    // ── Builders PDF ─────────────────────────────────────────────────────────

    private byte[] buildTablePdf(String title, String[] headers, String[] keys,
                                  float[] widths, List<Map<String, Object>> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 30, 30);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, title);

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);
            table.setWidths(widths);
            table.setSpacingBefore(10);

            // En-têtes
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Données
            boolean alt = false;
            for (Map<String, Object> row : rows) {
                Color bg = alt ? ROW_ALT : Color.WHITE;
                for (String key : keys) {
                    PdfPCell cell = new PdfPCell(new Phrase(safe(row, key), CELL_FONT));
                    cell.setBackgroundColor(bg);
                    cell.setPadding(4);
                    table.addCell(cell);
                }
                alt = !alt;
            }

            doc.add(table);
            addFooter(doc, rows.size());
            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur génération PDF", e);
            throw new RuntimeException("Impossible de générer le rapport PDF", e);
        }
    }

    private byte[] buildKpiPdf(LocalDate dateDebut, LocalDate dateFin,
                                List<Map<String, Object>> parRows,
                                List<Map<String, Object>> collecteRows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            String period = "%s — %s".formatted(
                    dateDebut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    dateFin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            addHeader(doc, "Rapport KPI Synthèse IMF — " + period);

            // Section PAR
            doc.add(new Paragraph("1. Portefeuille à Risque (PAR) par Zone", LABEL_FONT));
            doc.add(Chunk.NEWLINE);

            PdfPTable parTable = new PdfPTable(6);
            parTable.setWidthPercentage(100);
            parTable.setWidths(new float[]{2f, 2f, 1.2f, 1.2f, 1.2f, 2f});
            parTable.setSpacingBefore(8);

            String[] parHeaders = {"Zone ID", "Zone", "En Retard", "PAR30", "PAR90", "Encours Total"};
            for (String h : parHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                parTable.addCell(cell);
            }
            boolean alt = false;
            for (Map<String, Object> row : parRows) {
                Color bg = alt ? ROW_ALT : Color.WHITE;
                for (String k : new String[]{"zone_id","nom_zone","nb_en_retard","nb_par30","nb_par90","encours_total"}) {
                    PdfPCell cell = new PdfPCell(new Phrase(safe(row, k), CELL_FONT));
                    cell.setBackgroundColor(bg);
                    cell.setPadding(4);
                    parTable.addCell(cell);
                }
                alt = !alt;
            }
            doc.add(parTable);

            // Section Collectes
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("2. Volume des Collectes par Agence et Canal", LABEL_FONT));
            doc.add(Chunk.NEWLINE);

            PdfPTable colTable = new PdfPTable(4);
            colTable.setWidthPercentage(100);
            colTable.setWidths(new float[]{2.5f, 1.5f, 1.5f, 2f});
            colTable.setSpacingBefore(8);

            String[] colHeaders = {"Agence", "Canal", "Nb Collectes", "Montant Total"};
            for (String h : colHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                colTable.addCell(cell);
            }
            alt = false;
            for (Map<String, Object> row : collecteRows) {
                Color bg = alt ? ROW_ALT : Color.WHITE;
                for (String k : new String[]{"nom_agence","canal","nb_collectes","montant_total"}) {
                    PdfPCell cell = new PdfPCell(new Phrase(safe(row, k), CELL_FONT));
                    cell.setBackgroundColor(bg);
                    cell.setPadding(4);
                    colTable.addCell(cell);
                }
                alt = !alt;
            }
            doc.add(colTable);
            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur génération PDF KPI", e);
            throw new RuntimeException("Impossible de générer le rapport KPI PDF", e);
        }
    }

    private void addHeader(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, TITLE_FONT);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(4);
        doc.add(p);

        Paragraph sub = new Paragraph(
                "Généré le %s — IMF Pipeline Cameroun".formatted(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY));
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(10);
        doc.add(sub);
    }

    private void addFooter(Document doc, int count) throws DocumentException {
        Paragraph footer = new Paragraph(
                "Total : %d ligne(s)".formatted(count),
                new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY));
        footer.setSpacingBefore(6);
        doc.add(footer);
    }

    private String safe(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }
}
