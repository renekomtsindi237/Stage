package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.DeleguerAutoriteRequest;
import cm.imf.pipeline.dto.request.ReassignerDossierRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.DelegationResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/delegations")
@RequiredArgsConstructor
@Tag(name = "Délégations", description = "Réassignation de dossiers et délégations d'autorité hiérarchiques")
public class DelegationController {

    private final IDelegationService delegationService;

    @Operation(summary = "Réassigner un dossier crédit à un autre AGENT_CREDIT",
               description = "Réservé au CHEF_AGENCE, DIRECTEUR et DSI. " +
                             "L'ancien agent perd l'accès ; un enregistrement d'audit est créé.")
    @PostMapping("/reassigner-dossier/{dossierUid}")
    @PreAuthorize("hasAnyRole('CHEF_AGENCE','DIRECTEUR','DSI','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegationResponse>> reassignerDossier(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody ReassignerDossierRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Dossier réassigné.",
                        delegationService.reassignerDossier(dossierUid, request, user)));
    }

    @Operation(summary = "Créer une délégation d'autorité temporaire",
               description = "Permet à un supérieur hiérarchique de déléguer son pouvoir " +
                             "de validation/signature à un subordonné pour une période donnée.")
    @PostMapping("/deleguer-autorite")
    @PreAuthorize("hasAnyRole('DIRECTEUR','CHEF_AGENCE','RESPONSABLE_RECOUVREMENT','DSI','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegationResponse>> deleguerAutorite(
            @Valid @RequestBody DeleguerAutoriteRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Délégation d'autorité créée.",
                        delegationService.deleguerAutorite(request, user)));
    }

    @Operation(summary = "Révoquer une délégation d'autorité active",
               description = "Seul le délégant, le DIRECTEUR ou le DSI peut révoquer.")
    @DeleteMapping("/{uid}/revoquer")
    @PreAuthorize("hasAnyRole('DIRECTEUR','CHEF_AGENCE','RESPONSABLE_RECOUVREMENT','DSI','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revoquer(
            @PathVariable UUID uid,
            @AuthenticationPrincipal User user) {
        delegationService.revoquerDelegation(uid, user);
        return ResponseEntity.ok(ApiResponse.ok("Délégation révoquée."));
    }

    @Operation(summary = "Lister toutes les délégations de l'IMF (DIRECTEUR / DSI)")
    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTEUR','DSI','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<DelegationResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                delegationService.listDelegationsImf(user, page, size)));
    }

    @Operation(summary = "Délégations d'autorité actives reçues par l'utilisateur connecté")
    @GetMapping("/mes-delegations")
    public ResponseEntity<ApiResponse<List<DelegationResponse>>> mesDelegations(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(delegationService.mesDelegations(user)));
    }

    @Operation(summary = "Liste des AGENT_CREDIT actifs de l'IMF",
               description = "Utilisé par les selects de réassignation dans les interfaces. " +
                             "Accessible aux rôles managériaux.")
    @GetMapping("/agents-credit")
    @PreAuthorize("hasAnyRole('CHEF_AGENCE','DIRECTEUR','DSI','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAgentsCredit(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(delegationService.getAgentsCredit(user)));
    }
}
