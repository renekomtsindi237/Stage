package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreerDossierCreditRequest;
import cm.imf.pipeline.dto.request.ReassignerDossierRequest;
import cm.imf.pipeline.dto.request.ValidationChefRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.DelegationResponse;
import cm.imf.pipeline.dto.response.DossierCreditResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ICreditService;
import cm.imf.pipeline.service.IDelegationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/dossiers-credit")
@RequiredArgsConstructor
@Tag(name = "Dossiers Crédit", description = "Workflow d'octroi de crédit — instruction, comité, approbation")
public class DossierCreditController {

    private final ICreditService creditService;
    private final IDelegationService delegationService;

    @Operation(summary = "Créer un dossier de crédit (AGENT_CREDIT)")
    @PostMapping
    public ResponseEntity<ApiResponse<DossierCreditResponse>> creer(
            @Valid @RequestBody CreerDossierCreditRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Dossier créé.", creditService.creerDossier(request, user)));
    }

    @Operation(summary = "Liste paginée des dossiers (filtre optionnel : statut)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DossierCreditResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.listDossiers(user, statut, page, size)));
    }

    @Operation(summary = "Détail d'un dossier")
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<DossierCreditResponse>> get(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.getDossier(uid)));
    }

    @Operation(summary = "Soumettre le dossier au Chef d'Agence (INSTRUCTION → EN_COMITE)")
    @PatchMapping("/{uid}/soumettre")
    public ResponseEntity<ApiResponse<DossierCreditResponse>> soumettre(
            @PathVariable UUID uid,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Dossier soumis.", creditService.soumettre(uid, user)));
    }

    @Operation(summary = "Validation ou rejet par le Chef d'Agence")
    @PatchMapping("/{uid}/valider-chef")
    public ResponseEntity<ApiResponse<DossierCreditResponse>> validerChef(
            @PathVariable UUID uid,
            @Valid @RequestBody ValidationChefRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.validerChef(uid, request, user)));
    }

    @Operation(summary = "Clôturer l'instruction (ANALYSTE_ENGAGEMENTS) — ajouter note d'analyse")
    @PatchMapping("/{uid}/instruction-complete")
    public ResponseEntity<ApiResponse<DossierCreditResponse>> clotureInstruction(
            @PathVariable UUID uid,
            @RequestParam(required = false) String noteAnalyse,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.clotureInstruction(uid, noteAnalyse, user)));
    }

    @Operation(summary = "Réassigner le dossier à un autre AGENT_CREDIT (CHEF_AGENCE / DIRECTEUR)",
               description = "Transfère la responsabilité du dossier. " +
                             "Crée un enregistrement d'audit dans le journal des délégations.")
    @PatchMapping("/{uid}/reassigner")
    @PreAuthorize("hasAnyRole('CHEF_AGENCE','DIRECTEUR','DSI','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegationResponse>> reassigner(
            @PathVariable UUID uid,
            @Valid @RequestBody ReassignerDossierRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Dossier réassigné.",
                delegationService.reassignerDossier(uid, request, user)));
    }
}
