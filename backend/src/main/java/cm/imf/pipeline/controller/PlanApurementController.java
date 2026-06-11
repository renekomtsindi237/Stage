package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreerPlanApurementRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.PlanApurementResponse;
import cm.imf.pipeline.entity.PlanApurement;
import cm.imf.pipeline.entity.RecouvrementDossier;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.PlanApurementRepository;
import cm.imf.pipeline.repository.RecouvrementDossierRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans-apurement")
@RequiredArgsConstructor
@Tag(name = "Plans d'Apurement", description = "Moratoires de remboursement — recouvrement amiable")
public class PlanApurementController {

    private final PlanApurementRepository planRepo;
    private final RecouvrementDossierRepository dossierRepo;

    @Operation(summary = "Créer un plan d'apurement pour un dossier de recouvrement")
    @PostMapping("/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<PlanApurementResponse>> creer(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody CreerPlanApurementRequest request,
            @AuthenticationPrincipal User user) {
        RecouvrementDossier dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier de recouvrement introuvable."));
        PlanApurement plan = PlanApurement.builder()
                .dossierId(dossier.getId())
                .nbEcheances(request.nbEcheances())
                .montantParEcheance(request.montantParEcheance())
                .dateDebut(request.dateDebut())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Plan d'apurement créé.",
                        PlanApurementResponse.from(planRepo.save(plan))));
    }

    @Operation(summary = "Liste des plans d'apurement d'un dossier de recouvrement")
    @GetMapping("/dossier/{dossierUid}")
    public ResponseEntity<ApiResponse<List<PlanApurementResponse>>> list(@PathVariable UUID dossierUid) {
        RecouvrementDossier dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier de recouvrement introuvable."));
        List<PlanApurementResponse> plans = planRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream().map(PlanApurementResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(plans));
    }

    @Operation(summary = "Valider la signature client sur un plan")
    @PatchMapping("/{planUid}/signer")
    public ResponseEntity<ApiResponse<PlanApurementResponse>> signer(
            @PathVariable UUID planUid,
            @AuthenticationPrincipal User user) {
        PlanApurement plan = planRepo.findByUid(planUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan d'apurement introuvable."));
        plan.setSigneClient(true);
        return ResponseEntity.ok(ApiResponse.ok("Plan signé par le client.",
                PlanApurementResponse.from(planRepo.save(plan))));
    }
}
