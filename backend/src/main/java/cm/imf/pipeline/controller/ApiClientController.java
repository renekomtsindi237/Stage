package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreateApiClientRequest;
import cm.imf.pipeline.dto.request.RevealApiKeyRequest;
import cm.imf.pipeline.dto.response.ApiClientCreatedResponse;
import cm.imf.pipeline.dto.response.ApiClientResponse;
import cm.imf.pipeline.dto.response.ApiKeyRevealedResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ApiClientService;
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

/**
 * Gestion des clés API pour intégrations externes (BluCash, CBS, partenaires).
 * Accessible uniquement par SUPPORT et SUPER_ADMIN.
 */
@RestController
@RequestMapping("/support/api-clients")
@RequiredArgsConstructor
@Tag(name = "API Clients", description = "Gestion des clés API pour systèmes externes")
@PreAuthorize("hasAnyRole('SUPPORT','SUPER_ADMIN')")
public class ApiClientController {

    private final ApiClientService apiClientService;

    @Operation(
        summary = "Créer une clé API pour un système externe",
        description = """
            Génère une nouvelle clé API (format mcr_live_...) et l'associe à une IMF.
            **La clé brute est retournée UNE SEULE FOIS** dans le champ `apiKey` — elle ne peut pas être récupérée ensuite.
            Copier et stocker immédiatement dans un gestionnaire de secrets.
            """
    )
    @PostMapping
    public ResponseEntity<ApiClientCreatedResponse> create(
            @Valid @RequestBody CreateApiClientRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiClientService.create(request, currentUser));
    }

    @Operation(
        summary = "Lister les clés API de l'IMF",
        description = "Retourne toutes les clés (actives et révoquées). La clé brute n'est jamais incluse."
    )
    @GetMapping
    public ResponseEntity<List<ApiClientResponse>> list(
            @AuthenticationPrincipal User currentUser) {
        Long imfId = currentUser.getImf() != null ? currentUser.getImf().getId() : null;
        return ResponseEntity.ok(apiClientService.list(imfId));
    }

    @Operation(
        summary = "Révéler une clé API existante",
        description = """
            Déchiffre et retourne la clé brute après vérification du mot de passe du compte SUPPORT.
            Permet de récupérer une clé sans avoir à la régénérer.
            Toutes les tentatives (succès et échecs) sont loggées dans la piste d'audit.
            """
    )
    @PostMapping("/{id}/reveal")
    public ResponseEntity<ApiKeyRevealedResponse> reveal(
            @PathVariable UUID id,
            @Valid @RequestBody RevealApiKeyRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(apiClientService.reveal(id, request.password(), currentUser));
    }

    @Operation(
        summary = "Révoquer une clé API",
        description = """
            Désactive définitivement une clé API. Le client externe utilisant cette clé
            recevra des erreurs 401 immédiatement après révocation.
            Cette action est irréversible — créer une nouvelle clé si nécessaire.
            """
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiClientResponse> revoke(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(apiClientService.revoke(id, currentUser));
    }
}
