package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.GenererContratRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ContratCreditResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.IBackOfficeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/back-office")
@RequiredArgsConstructor
@Tag(name = "Back-Office Crédit", description = "Contrats, signatures — AGENT_SAISIE")
public class BackOfficeCreditController {

    private final IBackOfficeService backOfficeService;

    @Operation(summary = "Générer le contrat de crédit à partir d'un dossier APPROUVE")
    @PostMapping("/contrats/dossier/{dossierUid}/generer")
    public ResponseEntity<ApiResponse<ContratCreditResponse>> generer(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody GenererContratRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Contrat généré.",
                        backOfficeService.genererContrat(dossierUid, request, user)));
    }

    @Operation(summary = "Valider les signatures du contrat (SIGNE)")
    @PatchMapping("/contrats/{contratUid}/valider-signatures")
    public ResponseEntity<ApiResponse<ContratCreditResponse>> validerSignatures(
            @PathVariable UUID contratUid,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Signatures validées.",
                backOfficeService.validerSignatures(contratUid, user)));
    }

    @Operation(summary = "Détail d'un contrat par son UID")
    @GetMapping("/contrats/{contratUid}")
    public ResponseEntity<ApiResponse<ContratCreditResponse>> getContrat(@PathVariable UUID contratUid) {
        return ResponseEntity.ok(ApiResponse.ok(backOfficeService.getContrat(contratUid)));
    }

    @Operation(summary = "Contrat lié à un dossier")
    @GetMapping("/contrats/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<ContratCreditResponse>> getParDossier(@PathVariable UUID dossierUid) {
        return ResponseEntity.ok(ApiResponse.ok(backOfficeService.getContratParDossier(dossierUid)));
    }
}
