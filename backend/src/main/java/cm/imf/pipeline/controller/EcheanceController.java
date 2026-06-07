package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.EcheanceUpdateRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.EcheanceResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.service.IEcheanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/echeances")
@RequiredArgsConstructor
@Tag(name = "Échéances", description = "Gestion des échéances de remboursement")
public class EcheanceController {

    private final IEcheanceService echeanceService;

    @Operation(summary = "Toutes les échéances d'un prêt, ordonnées par numéro")
    @GetMapping("/pret/{idPret}")
    public ResponseEntity<ApiResponse<List<EcheanceResponse>>> getByPret(
            @PathVariable String idPret) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.getByPret(idPret)));
    }

    @Operation(summary = "Détail d'une échéance par UID public")
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<EcheanceResponse>> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.getById(uid)));
    }

    @Operation(summary = "Mettre à jour le statut / montant payé d'une échéance")
    @PutMapping("/{uid}")
    @Auditable(
        action                   = AuditTrail.ACTION_MODIFICATION,
        entiteType               = AuditTrail.ENTITE_ECHEANCE,
        entiteIdExpression       = "#uid.toString()",
        motifExpression          = "#request.observation",
        ancienneValeurExpression = "@echeanceAppRepository.findByUid(#uid).orElse(null)",
        captureResult            = true
    )
    public ResponseEntity<ApiResponse<EcheanceResponse>> updateStatut(
            @PathVariable UUID uid,
            @Valid @RequestBody EcheanceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.updateStatut(uid, request)));
    }

    @Operation(summary = "Liste paginée des échéances en retard (RR et DSI)")
    @GetMapping("/en-retard")
    public ResponseEntity<ApiResponse<PageResponse<EcheanceResponse>>> getEnRetard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.getEcheancesEnRetard(page, size)));
    }
}
