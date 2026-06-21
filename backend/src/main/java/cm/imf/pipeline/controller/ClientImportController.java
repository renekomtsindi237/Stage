package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ImportResultResponse;
import cm.imf.pipeline.service.IClientImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Tag(name = "Import Clients", description = "Import/export CSV de la liste des clients par agent")
public class ClientImportController {

    private final IClientImportService importService;

    @Operation(summary = "Télécharger le modèle CSV vide à remplir pour l'import")
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        String csv = importService.genererTemplateCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"modele_import_clients.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "Importer une liste de clients depuis un fichier CSV")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultResponse>> importerCsv(
            @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(required = false, defaultValue = "") String imfCode) {

        if (fichier.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Le fichier CSV est vide"));
        }
        ImportResultResponse result = importService.importerDepuisCsv(fichier, imfCode);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Exporter la liste des clients d'un agent au format CSV (avec KPI N-1)")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exporterClients(
            @RequestParam String agentEmail,
            @RequestParam(required = false, defaultValue = "") String imfCode) {

        String csv = importService.exporterClientsAgent(agentEmail, imfCode);
        String filename = "clients_" + agentEmail.replace("@", "_at_")
                + "_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
