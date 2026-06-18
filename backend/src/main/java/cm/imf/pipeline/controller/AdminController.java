package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreateAgenceRequest;
import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.request.ResetPasswordRequest;
import cm.imf.pipeline.dto.response.AgenceResponse;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ImfResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.service.IAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administration IMF — réservé au rôle DSI.
 * Domaine : gestion des utilisateurs de son propre tenant (IMF).
 * Toutes les opérations sont scopées à l'IMF du DSI connecté.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
@Tag(name = "Administration IMF", description = "Gestion des utilisateurs — réservé DSI / SUPER_ADMIN")
public class AdminController {

    private final IAdminService adminService;

    // ── Contexte IMF ──────────────────────────────────────────────────────────

    @Operation(summary = "Informations de l'IMF du DSI connecté (lecture seule)")
    @GetMapping("/imf")
    public ResponseEntity<ApiResponse<ImfResponse>> getImfInfo() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getImfInfo()));
    }

    // ── Agences ──────────────────────────────────────────────────────────────

    @Operation(summary = "Liste des agences de l'IMF (CRUD DSI)")
    @GetMapping("/agences")
    public ResponseEntity<ApiResponse<List<AgenceResponse>>> listAgences() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAgences()));
    }

    @Operation(summary = "Noms des agences actives (autocomplete création utilisateur)")
    @GetMapping("/agences/noms")
    public ResponseEntity<ApiResponse<List<String>>> listAgenceNoms() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAgenceNoms()));
    }

    @Operation(summary = "Créer une agence dans l'IMF du DSI")
    @PostMapping("/agences")
    public ResponseEntity<ApiResponse<AgenceResponse>> createAgence(
            @Valid @RequestBody CreateAgenceRequest request) {
        AgenceResponse created = adminService.createAgence(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Agence créée", created));
    }

    @Operation(summary = "Activer / désactiver une agence")
    @PatchMapping("/agences/{uid}/toggle")
    public ResponseEntity<ApiResponse<AgenceResponse>> toggleAgence(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.toggleAgence(uid)));
    }

    @Operation(summary = "Supprimer une agence (seulement si aucun utilisateur ne lui est affecté)")
    @DeleteMapping("/agences/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteAgence(@PathVariable UUID uid) {
        adminService.deleteAgence(uid);
        return ResponseEntity.ok(ApiResponse.ok("Agence supprimée"));
    }

    // ── Utilisateurs ─────────────────────────────────────────────────────────

    @Operation(summary = "Liste paginée des utilisateurs de l'IMF")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(page, size)));
    }

    @Operation(summary = "Détail d'un utilisateur de l'IMF")
    @GetMapping("/users/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getById(uid)));
    }

    @Operation(summary = "Créer un utilisateur dans l'IMF — rôles : DIRECTEUR, RESP_RECOUVREMENT, ANALYSTE, AGENT")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = adminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Utilisateur créé", created));
    }

    @Operation(summary = "Désactiver un compte utilisateur de l'IMF")
    @DeleteMapping("/users/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok("Compte désactivé", adminService.deactivate(uid)));
    }

    @Operation(summary = "Réactiver un compte utilisateur de l'IMF")
    @PatchMapping("/users/{uid}/activate")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok("Compte réactivé", adminService.activate(uid)));
    }

    @Operation(summary = "Réinitialiser le mot de passe d'un utilisateur avec le mot de passe fourni")
    @PatchMapping("/users/{uid}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID uid,
            @Valid @RequestBody ResetPasswordRequest request) {
        adminService.resetPassword(uid, request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("Mot de passe réinitialisé"));
    }

    @Operation(summary = "Upload ou remplacement de l'avatar d'un utilisateur de l'IMF (DSI)")
    @PostMapping(value = "/users/{uid}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadUserAvatar(
            @PathVariable UUID uid,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.uploadUserAvatar(uid, file)));
    }

    @Operation(summary = "Supprimer l'avatar d'un utilisateur de l'IMF (DSI)")
    @DeleteMapping("/users/{uid}/avatar")
    public ResponseEntity<ApiResponse<UserResponse>> removeUserAvatar(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.removeUserAvatar(uid)));
    }

    @Operation(summary = "Upload ou remplacement du logo de l'IMF (DSI)")
    @PostMapping(value = "/imf/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImfResponse>> uploadImfLogo(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.uploadImfLogo(file)));
    }

}
