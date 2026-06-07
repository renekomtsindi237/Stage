package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.AuditTrailResponse;
import cm.imf.pipeline.entity.JournalAudit;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IAuditService;
import cm.imf.pipeline.service.IAuditTrailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
@Tag(name = "Audit", description = "Piste d'audit immuable — art. 27 Loi 2024/017 Cameroun")
public class AuditController {

    private final IAuditService      auditService;
    private final IAuditTrailService auditTrailService;

    // ── Journal existant (app.journal_audit) ──────────────────────────────────

    @Operation(summary = "Historique d'audit d'un utilisateur (journal simple)")
    @GetMapping("/users/{username}")
    public ResponseEntity<ApiResponse<Page<JournalAudit>>> getHistoriqueUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getHistorique(username, page, size)));
    }

    @Operation(summary = "Entrées du journal par type d'action")
    @GetMapping("/actions/{action}")
    public ResponseEntity<ApiResponse<Page<JournalAudit>>> getByAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getByAction(action, page, size)));
    }

    // ── Piste d'audit immuable (app.audit_trail) ──────────────────────────────

    @Operation(summary = "Recherche filtrée dans la piste d'audit immuable (art. 27)",
               description = "Tous les paramètres sont optionnels. PII masquées selon le rôle.")
    @GetMapping("/trail")
    public ResponseEntity<ApiResponse<Page<AuditTrailResponse>>> rechercherTrail(
            @Parameter(description = "Type d'entité : DOSSIER, CLIENT, ALERTE, COLLECTE…")
            @RequestParam(required = false) String entiteType,
            @Parameter(description = "Identifiant de l'entité")
            @RequestParam(required = false) String entiteId,
            @Parameter(description = "Code action : CREATION, MODIFICATION, CHANGEMENT_STATUT…")
            @RequestParam(required = false) String action,
            @Parameter(description = "Nom d'utilisateur de l'acteur")
            @RequestParam(required = false) String username,
            @Parameter(description = "Date de début (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime debut,
            @Parameter(description = "Date de fin (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Long imfId = TenantContext.currentImfId();
        return ResponseEntity.ok(ApiResponse.ok(
                auditTrailService.rechercher(imfId, entiteType, entiteId,
                        action, username, debut, fin, page, size)));
    }

    @Operation(summary = "Piste d'audit d'un dossier spécifique",
               description = "Toutes les actions sur ce dossier : accès, modifications, changements de statut.")
    @GetMapping("/trail/dossiers/{dossierRef}")
    public ResponseEntity<ApiResponse<Page<AuditTrailResponse>>> auditDossier(
            @PathVariable String dossierRef,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Long imfId = TenantContext.currentImfId();
        return ResponseEntity.ok(ApiResponse.ok(
                auditTrailService.historiqueEntite(imfId, "DOSSIER", dossierRef, page, size)));
    }

    @Operation(summary = "Piste d'audit d'un client",
               description = "Tous les accès et modifications sur les données d'un client.")
    @GetMapping("/trail/clients/{clientId}")
    public ResponseEntity<ApiResponse<Page<AuditTrailResponse>>> auditClient(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Long imfId = TenantContext.currentImfId();
        return ResponseEntity.ok(ApiResponse.ok(
                auditTrailService.historiqueEntite(imfId, "CLIENT", clientId, page, size)));
    }

    @Operation(summary = "Piste d'audit d'une alerte",
               description = "Historique complet d'une alerte impayé : création, modifications, clôture.")
    @GetMapping("/trail/alertes/{alerteId}")
    public ResponseEntity<ApiResponse<Page<AuditTrailResponse>>> auditAlerte(
            @PathVariable String alerteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Long imfId = TenantContext.currentImfId();
        return ResponseEntity.ok(ApiResponse.ok(
                auditTrailService.historiqueEntite(imfId, "ALERTE", alerteId, page, size)));
    }
}
