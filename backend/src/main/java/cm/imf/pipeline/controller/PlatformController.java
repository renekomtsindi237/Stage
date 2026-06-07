package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreateImfAdminRequest;
import cm.imf.pipeline.dto.request.CreateImfRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ImfResponse;
import cm.imf.pipeline.dto.response.PlatformStatsResponse;
import cm.imf.pipeline.service.IImfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme", description = "Gestion des IMF tenants — réservé SUPER_ADMIN")
public class PlatformController {

    private final IImfService imfService;

    @Operation(summary = "Statistiques globales de la plateforme")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(imfService.getStats()));
    }

    @Operation(summary = "Liste de toutes les IMF enregistrées")
    @GetMapping("/imf")
    public ResponseEntity<ApiResponse<List<ImfResponse>>> listImf() {
        return ResponseEntity.ok(ApiResponse.ok(imfService.listAll()));
    }

    @Operation(summary = "Détail d'une IMF par uid")
    @GetMapping("/imf/{uid}")
    public ResponseEntity<ApiResponse<ImfResponse>> getImf(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(imfService.getById(uid)));
    }

    @Operation(summary = "Créer une nouvelle IMF (nouveau tenant)")
    @PostMapping("/imf")
    public ResponseEntity<ApiResponse<ImfResponse>> createImf(
            @Valid @RequestBody CreateImfRequest request) {
        ImfResponse created = imfService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("IMF créée", created));
    }

    @Operation(summary = "Désactiver une IMF — bloque l'accès de tous ses utilisateurs")
    @PatchMapping("/imf/{uid}/deactivate")
    public ResponseEntity<ApiResponse<ImfResponse>> deactivateImf(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok("IMF désactivée", imfService.deactivate(uid)));
    }

    @Operation(summary = "Supprimer définitivement une IMF — irréversible")
    @DeleteMapping("/imf/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteImf(@PathVariable UUID uid) {
        imfService.delete(uid);
        return ResponseEntity.ok(ApiResponse.ok("IMF supprimée définitivement"));
    }

    @Operation(summary = "Réactiver une IMF")
    @PatchMapping("/imf/{uid}/activate")
    public ResponseEntity<ApiResponse<ImfResponse>> activateImf(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok("IMF réactivée", imfService.activate(uid)));
    }

    @Operation(summary = "Créer le compte DSI (administrateur) d'une IMF")
    @PostMapping("/imf/{imfUid}/admin")
    public ResponseEntity<ApiResponse<ImfResponse>> createImfAdmin(
            @PathVariable UUID imfUid,
            @Valid @RequestBody CreateImfAdminRequest request) {
        ImfResponse imf = imfService.createAdmin(imfUid, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Compte DSI créé", imf));
    }
}
