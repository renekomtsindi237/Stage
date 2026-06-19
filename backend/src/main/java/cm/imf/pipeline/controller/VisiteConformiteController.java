package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.VisiteConformiteRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.VisiteConformiteResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ICreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/visites-conformite")
@RequiredArgsConstructor
@Tag(name = "Visites de Conformité", description = "Visite terrain J+15 post-déblocage — AGENT_CREDIT")
public class VisiteConformiteController {

    private final ICreditService creditService;

    @Operation(summary = "Enregistrer une visite de conformité J+15")
    @PostMapping("/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<VisiteConformiteResponse>> enregistrer(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody VisiteConformiteRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Visite enregistrée.",
                        creditService.enregistrerVisite(dossierUid, request, user)));
    }

    @Operation(summary = "Historique des visites de conformité d'un dossier")
    @GetMapping("/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<List<VisiteConformiteResponse>>> list(@PathVariable UUID dossierUid) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.listVisites(dossierUid)));
    }
}
