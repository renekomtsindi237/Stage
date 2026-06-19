package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.response.AgenceResponse;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.service.IAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des utilisateurs et agences accessible au DIRECTEUR d'IMF.
 * Délègue à AdminService (même périmètre IMF que le DSI, droits légèrement réduits).
 */
@RestController
@RequestMapping("/directeur")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DIRECTEUR', 'DSI', 'SUPER_ADMIN')")
@Tag(name = "Directeur", description = "Gestion des utilisateurs et agences par le DIRECTEUR")
public class DirecteurController {

    private final IAdminService adminService;

    // ── Utilisateurs ─────────────────────────────────────────────────────────

    @Operation(summary = "Liste paginée des utilisateurs de l'IMF")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(page, size)));
    }

    @Operation(summary = "Créer un utilisateur dans l'IMF")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserDirecteurRequest req) {
        CreateUserRequest request = new CreateUserRequest(
                req.username() != null ? req.username() : buildUsername(req),
                null,
                req.email(),
                req.role() != null ? Role.valueOf(req.role()) : Role.AGENT,
                null,
                null,
                null,
                null
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminService.createUser(request)));
    }

    @Operation(summary = "Désactiver (soft-delete) un utilisateur")
    @DeleteMapping("/users/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID uid) {
        adminService.deleteUser(uid);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "Réactiver un utilisateur")
    @PatchMapping("/users/{uid}/activate")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.activate(uid)));
    }

    // ── Agences ───────────────────────────────────────────────────────────────

    @Operation(summary = "Liste des agences de l'IMF")
    @GetMapping("/agences")
    public ResponseEntity<ApiResponse<List<AgenceResponse>>> agences() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAgences()));
    }

    // ── DTO inline ────────────────────────────────────────────────────────────

    record CreateUserDirecteurRequest(
            String prenom,
            String nom,
            String email,
            String role,
            String username
    ) {}

    private String buildUsername(CreateUserDirecteurRequest req) {
        String base = "";
        if (req.prenom() != null && !req.prenom().isBlank())
            base += req.prenom().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (req.nom() != null && !req.nom().isBlank())
            base += "_" + req.nom().toLowerCase().replaceAll("[^a-z0-9]", "");
        return base.isBlank() ? "user_" + System.currentTimeMillis() % 100000 : base;
    }
}
