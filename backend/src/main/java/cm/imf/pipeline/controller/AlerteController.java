package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.AlerteUpdateRequest;
import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.enums.StatutAlerte;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.service.IAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/alertes")
@RequiredArgsConstructor
@Tag(name = "Alertes", description = "Gestion des alertes impayés")
public class AlerteController {

    private final IAlertService alerteService;

    @Operation(summary = "Liste paginée des alertes, filtrée par statut optionnel")
    @GetMapping
    public ResponseEntity<PageResponse<AlerteResponse>> getAlertes(
            @RequestParam(required = false) StatutAlerte statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alerteService.getAlertes(statut, page, size));
    }

    @Operation(summary = "Détail d'une alerte par UID public")
    @GetMapping("/{uid}")
    public ResponseEntity<AlerteResponse> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(alerteService.getById(uid));
    }

    @Operation(summary = "Mettre à jour le statut d'une alerte (clôturer / escalader)")
    @PutMapping("/{uid}")
    @Auditable(
        action                   = AuditTrail.ACTION_CHANGEMENT_STATUT,
        entiteType               = AuditTrail.ENTITE_ALERTE,
        entiteIdExpression       = "#uid.toString()",
        motifExpression          = "#request.statut.name()",
        ancienneValeurExpression = "@alerteRepository.findByUid(#uid).orElse(null)",
        captureResult            = true
    )
    public ResponseEntity<AlerteResponse> updateStatut(
            @PathVariable UUID uid,
            @Valid @RequestBody AlerteUpdateRequest request) {
        return ResponseEntity.ok(alerteService.updateStatut(uid, request));
    }
}
