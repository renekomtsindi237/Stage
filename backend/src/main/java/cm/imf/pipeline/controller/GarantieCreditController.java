package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.AjouterGarantieRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.GarantieCreditResponse;
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
@RequestMapping("/api/v1/garanties")
@RequiredArgsConstructor
@Tag(name = "Garanties Crédit", description = "Gestion des garanties liées aux dossiers de crédit")
public class GarantieCreditController {

    private final ICreditService creditService;

    @Operation(summary = "Ajouter une garantie à un dossier")
    @PostMapping("/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<GarantieCreditResponse>> ajouter(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody AjouterGarantieRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Garantie enregistrée.",
                        creditService.ajouterGarantie(dossierUid, request, user)));
    }

    @Operation(summary = "Liste des garanties d'un dossier")
    @GetMapping("/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<List<GarantieCreditResponse>>> list(@PathVariable UUID dossierUid) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.listGaranties(dossierUid)));
    }
}
