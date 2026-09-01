package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.ChangePasswordRequest;
import cm.imf.pipeline.dto.request.FcmTokenRequest;
import cm.imf.pipeline.dto.request.UpdatePreferencesRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.service.IUserService;
import cm.imf.pipeline.service.OnlineTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Profil utilisateur connecté et mise à jour FCM")
public class UserController {

    private final IUserService          userService;
    private final OnlineTrackingService onlineTracking;

    @Operation(summary = "Profil de l'utilisateur connecté")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getByUsername(user.getUsername())));
    }

    @Operation(summary = "Enregistrer ou mettre à jour le token FCM (push notifications)")
    @PostMapping("/me/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(user, request.token());
        return ResponseEntity.ok(ApiResponse.ok("Token FCM enregistré"));
    }

    @Operation(
            summary = "Mettre à jour ses préférences personnelles",
            description = """
                    Patch partiel : seuls les champs fournis (non-null) sont mis à jour.
                    Préférences disponibles : thème visuel, langue, notifications par type,
                    nombre d'éléments par page.
                    """
    )
    @PatchMapping("/me/preferences")
    public ResponseEntity<ApiResponse<UserResponse>> updatePreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        UserResponse updated = userService.updatePreferences(user, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @Operation(summary = "Sert l'avatar courant (ou l'image par défaut). Jamais 4xx : PNG de repli si anonyme ou fichier absent.")
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getMyAvatar(@AuthenticationPrincipal User user) {
        try {
            return userService.serveAvatar(user);
        } catch (Exception e) {
            return userService.serveAvatar(null);
        }
    }

    @Operation(summary = "Upload ou remplacement de l'avatar (JPEG/PNG/WEBP/GIF, max 2 Mo)")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.uploadAvatar(user, file)));
    }

    @Operation(summary = "Supprimer l'avatar — revient à l'image par défaut")
    @DeleteMapping("/me/avatar")
    public ResponseEntity<ApiResponse<UserResponse>> removeAvatar(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.removeAvatar(user)));
    }

    @Operation(summary = "Nombre d'utilisateurs actuellement connectés (scopé par rôle)")
    @GetMapping("/online-count")
    public ResponseEntity<ApiResponse<Long>> getOnlineCount(@AuthenticationPrincipal User user) {
        long count;
        if (user.getRole() == Role.SUPER_ADMIN) {
            count = onlineTracking.countOnline();
        } else if (user.getImf() != null) {
            count = onlineTracking.countOnlineByImf(user.getImf().getId());
        } else {
            count = 0L;
        }
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    @Operation(summary = "Changer son propre mot de passe (SUPER_ADMIN exclu)")
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le Super Administrateur ne peut pas modifier son mot de passe via cette interface.");
        }
        userService.changePassword(user, request);
        return ResponseEntity.ok(ApiResponse.ok("Mot de passe mis à jour"));
    }
}
