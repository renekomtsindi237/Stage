package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.SyncRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.SyncResponse;
import cm.imf.pipeline.dto.response.SyncStatusResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ICollecteSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de synchronisation hors-ligne → en ligne pour l'app mobile Flutter.
 *
 * Workflow typique :
 *   1. L'agent saisit des collectes hors-ligne (stockées localement dans Hive/SQLite)
 *   2. Lors du retour en ligne, l'app appelle POST /api/sync/collectes
 *   3. Le serveur traite le batch et retourne un résultat détaillé par item
 *   4. L'app met à jour son état local en fonction des résultats
 *   5. GET /api/sync/status/{deviceId} permet de consulter l'historique de l'appareil
 */
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
@Tag(name = "Synchronisation", description = "Synchronisation hors-ligne → en ligne pour l'app mobile")
public class SyncController {

    private final ICollecteSyncService syncService;

    @Operation(
            summary = "Synchroniser un batch de collectes hors-ligne",
            description = """
                    Envoie toutes les collectes en attente depuis l'appareil mobile.
                    Idempotent : un même syncId peut être renvoyé sans produire de doublons.
                    Chaque item retourne un code et un message explicite pour l'agent.
                    """
    )
    @PostMapping("/collectes")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<SyncResponse>> syncCollectes(
            @Valid @RequestBody SyncRequest request,
            @AuthenticationPrincipal User agent,
            HttpServletRequest httpRequest) {

        String ipClient = extractClientIp(httpRequest);
        SyncResponse result = syncService.processSync(request, agent, ipClient);

        String message = result.messageResume();
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    @Operation(
            summary = "Statut de synchronisation d'un appareil",
            description = """
                    Retourne l'historique de synchronisation d'un appareil Flutter identifié
                    par son deviceId. Inclut le nombre de conflits en attente de résolution.
                    """
    )
    @GetMapping("/status/{deviceId}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<SyncStatusResponse>> getSyncStatus(
            @PathVariable String deviceId) {

        SyncStatusResponse status = syncService.getSyncStatus(deviceId);
        return ResponseEntity.ok(ApiResponse.ok(status.message(), status));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
