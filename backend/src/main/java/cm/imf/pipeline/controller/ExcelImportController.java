package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ImportResultResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.ExcelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import Excel multi-entités pour le rôle Directeur (et DSI/SUPER_ADMIN).
 *
 * Templates : GET  /api/v1/import/template/{type}
 * Import    : POST /api/v1/import/{type}
 *
 * Types : clients | agents | agences | utilisateurs
 */
@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DIRECTEUR','DSI','SUPER_ADMIN')")
@Tag(name = "Import Excel", description = "Import en masse via fichier .xlsx — clients, agents, agences, utilisateurs")
public class ExcelImportController {

    private final ExcelImportService excelService;

    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // ── Téléchargement des modèles ────────────────────────────────────────────

    @Operation(summary = "Télécharger le modèle Excel vide pour le type demandé")
    @GetMapping("/template/{type}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) throws Exception {
        byte[] data = switch (type.toLowerCase()) {
            case "clients"       -> excelService.genererTemplateClients();
            case "agents"        -> excelService.genererTemplateAgents();
            case "agences"       -> excelService.genererTemplateAgences();
            case "utilisateurs"  -> excelService.genererTemplateUtilisateurs();
            default -> throw new IllegalArgumentException("Type inconnu : " + type);
        };
        String filename = "modele_import_" + type + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(data);
    }

    // ── Import clients ────────────────────────────────────────────────────────

    @Operation(summary = "Importer des clients depuis un fichier Excel (.xlsx)")
    @PostMapping(value = "/clients", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultResponse>> importClients(
            @RequestPart("fichier") MultipartFile fichier) throws Exception {

        Long imfId = requireImfId();
        if (fichier.isEmpty()) return badRequest("Le fichier est vide.");
        return ResponseEntity.ok(ApiResponse.ok(excelService.importerClients(fichier, imfId)));
    }

    // ── Import agents ─────────────────────────────────────────────────────────

    @Operation(summary = "Importer des agents terrain depuis un fichier Excel (.xlsx)")
    @PostMapping(value = "/agents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultResponse>> importAgents(
            @RequestPart("fichier") MultipartFile fichier,
            @AuthenticationPrincipal User user) throws Exception {

        if (fichier.isEmpty()) return badRequest("Le fichier est vide.");
        return ResponseEntity.ok(ApiResponse.ok(excelService.importerAgents(fichier, user)));
    }

    // ── Import agences ────────────────────────────────────────────────────────

    @Operation(summary = "Importer des agences depuis un fichier Excel (.xlsx)")
    @PostMapping(value = "/agences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultResponse>> importAgences(
            @RequestPart("fichier") MultipartFile fichier,
            @AuthenticationPrincipal User user) throws Exception {

        if (fichier.isEmpty()) return badRequest("Le fichier est vide.");
        return ResponseEntity.ok(ApiResponse.ok(excelService.importerAgences(fichier, user)));
    }

    // ── Import utilisateurs ───────────────────────────────────────────────────

    @Operation(summary = "Importer des utilisateurs (tous rôles) depuis un fichier Excel (.xlsx)")
    @PostMapping(value = "/utilisateurs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultResponse>> importUtilisateurs(
            @RequestPart("fichier") MultipartFile fichier,
            @AuthenticationPrincipal User user) throws Exception {

        if (fichier.isEmpty()) return badRequest("Le fichier est vide.");
        return ResponseEntity.ok(ApiResponse.ok(excelService.importerUtilisateurs(fichier, user)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long requireImfId() {
        Long imfId = TenantContext.currentImfId();
        if (imfId == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Réservé aux utilisateurs d'une IMF.");
        return imfId;
    }

    private ResponseEntity<ApiResponse<ImportResultResponse>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
    }
}
