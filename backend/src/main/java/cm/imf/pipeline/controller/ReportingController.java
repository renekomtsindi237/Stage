package cm.imf.pipeline.controller;

import cm.imf.pipeline.service.IExportService;
import cm.imf.pipeline.service.IPdfExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/reporting")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Export CSV et PDF des collectes, prêts et KPI")
public class ReportingController {

    private final IExportService exportService;
    private final IPdfExportService pdfExportService;

    // ── CSV Exports ──────────────────────────────────────────────────────────

    @Operation(summary = "Export CSV des collectes pour une période")
    @GetMapping("/collectes/csv")
    public ResponseEntity<byte[]> exportCollectesCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        String csv = exportService.exportCollectesCSV(dateDebut, dateFin);
        String filename = "collectes_%s_%s.csv".formatted(
                dateDebut.format(DateTimeFormatter.BASIC_ISO_DATE),
                dateFin.format(DateTimeFormatter.BASIC_ISO_DATE));
        return csvResponse(csv, filename);
    }

    @Operation(summary = "Export CSV des prêts en retard (PAR)")
    @GetMapping("/prets-retard/csv")
    public ResponseEntity<byte[]> exportPretsEnRetardCSV() {
        String csv = exportService.exportPretsEnRetardCSV();
        return csvResponse(csv, "prets_en_retard_%s.csv".formatted(
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)));
    }

    // ── PDF Exports ──────────────────────────────────────────────────────────

    @Operation(summary = "Export PDF des collectes pour une période")
    @GetMapping("/collectes/pdf")
    public ResponseEntity<byte[]> exportCollectesPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] pdf = pdfExportService.exportCollectesPDF(dateDebut, dateFin);
        String filename = "collectes_%s_%s.pdf".formatted(
                dateDebut.format(DateTimeFormatter.BASIC_ISO_DATE),
                dateFin.format(DateTimeFormatter.BASIC_ISO_DATE));
        return pdfResponse(pdf, filename);
    }

    @Operation(summary = "Export PDF des prêts en retard (PAR)")
    @GetMapping("/prets-retard/pdf")
    public ResponseEntity<byte[]> exportPretsEnRetardPDF() {
        byte[] pdf = pdfExportService.exportPretsEnRetardPDF();
        return pdfResponse(pdf, "prets_en_retard_%s.pdf".formatted(
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)));
    }

    @Operation(summary = "Rapport réglementaire COBAC PDF (année en cours par défaut)")
    @GetMapping("/cobac/pdf")
    public ResponseEntity<byte[]> exportCobacPDF(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        LocalDate end   = dateFin   != null ? dateFin   : LocalDate.now();
        LocalDate start = dateDebut != null ? dateDebut : end.withDayOfYear(1);
        byte[] pdf = pdfExportService.exportKpiRapportPDF(start, end);
        String filename = "rapport_cobac_%s.pdf".formatted(end.format(DateTimeFormatter.BASIC_ISO_DATE));
        return pdfResponse(pdf, filename);
    }

    @Operation(summary = "Rapport KPI PDF synthèse pour une période")
    @GetMapping("/kpi/pdf")
    public ResponseEntity<byte[]> exportKpiPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] pdf = pdfExportService.exportKpiRapportPDF(dateDebut, dateFin);
        String filename = "rapport_kpi_%s_%s.pdf".formatted(
                dateDebut.format(DateTimeFormatter.BASIC_ISO_DATE),
                dateFin.format(DateTimeFormatter.BASIC_ISO_DATE));
        return pdfResponse(pdf, filename);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
