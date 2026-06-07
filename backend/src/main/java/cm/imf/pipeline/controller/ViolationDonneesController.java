package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.ViolationDonneesRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IAuditTrailService;
import cm.imf.pipeline.service.INotificationService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des violations de données personnelles.
 * Art. 22 — Loi n° 2024/017 Cameroun.
 *
 * Flux obligatoire :
 *   1. DSI déclare la violation dans les 72h après découverte (art. 22 §1)
 *   2. Notification SSE + FCM + email envoyée automatiquement aux DSI de l'IMF
 *   3. DSI met à jour le statut au fil de l'investigation
 *   4. Clôture avec rapport final
 */
@Slf4j
@RestController
@RequestMapping("/admin/violations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
@Tag(name = "Violations", description = "Registre des violations de données — art. 22 Loi 2024/017")
public class ViolationDonneesController {

    private final JdbcTemplate         jdbc;
    private final IAuditTrailService   auditTrailService;
    private final INotificationService notificationService;
    private final SseEmitterRegistry   sseRegistry;

    @Operation(summary = "Déclarer une violation de données (art. 22 — délai 72h)",
               description = "Déclenche une alerte immédiate SSE + FCM + email vers les DSI. "
                           + "Le délai légal de notification à l'autorité de protection est de 72h.")
    @PostMapping
    @Auditable(action = AuditTrail.ACTION_CREATION, entiteType = AuditTrail.ENTITE_VIOLATION_DONNEES,
               motifExpression = "#req.typeViolation", captureResult = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> declarer(
            @Valid @RequestBody ViolationDonneesRequest req) {

        User moi   = TenantContext.currentUser();
        Long imfId = TenantContext.currentImfId();

        // Persister la violation — récupérer l'uid généré par la BD
        String violationUid = jdbc.queryForObject("""
                INSERT INTO app.violations_donnees
                    (imf_id, declarant_id, declarant_username, date_decouverte,
                     type_violation, description, categories_donnees,
                     nb_personnes_estimees, entites_concernees, severite,
                     mesures_immediates, notif_autorite_requise, notif_personnes_requise)
                VALUES (?, ?, ?, ?,  ?, ?, ?,  ?, ?, ?,  ?, ?, ?)
                RETURNING uid::text
                """,
                String.class,
                imfId, moi.getId(), moi.getUsername(), req.dateDecouverte(),
                req.typeViolation(), req.description(), req.categoriesDonnees(),
                req.nbPersonnesEstimees(), req.entitesConcernees(), req.severite(),
                req.mesuresImmediates(),
                req.notifAutoriteRequise(), req.notifPersonnesRequise());

        // Calculer le délai restant avant la limite légale des 72h
        long heuresDepuisDecouverte = java.time.Duration.between(
                req.dateDecouverte(), OffsetDateTime.now()).toHours();
        long heuresRestantes = 72 - heuresDepuisDecouverte;

        String alerte = heuresRestantes > 0
                ? String.format("⚠️ Violation %s déclarée — %dh restantes pour notifier l'autorité",
                        req.severite(), heuresRestantes)
                : String.format("🚨 DÉLAI LÉGAL DÉPASSÉ — Violation %s déclarée %dh après découverte",
                        req.severite(), -heuresRestantes);

        // Notification SSE immédiate vers tous les DSI connectés
        SseEventDto event = new SseEventDto(
                "VIOLATION_DONNEES",
                "DSI",
                alerte,
                Map.of(
                        "violationUid",   violationUid,
                        "typeViolation",  req.typeViolation(),
                        "severite",       req.severite(),
                        "heuresRestantes", heuresRestantes,
                        "categoriesDonnees", req.categoriesDonnees()
                ),
                OffsetDateTime.now());
        sseRegistry.broadcastToRole("DSI", event);

        // Push FCM + email aux DSI
        String titre = "Violation données " + req.severite() + " — " + req.typeViolation();
        notificationService.sendPushToRole(Role.DSI, titre, alerte);
        // Email avec détails complets pour le DSI responsable
        if (moi.getEmail() != null) {
            notificationService.sendEmail(
                    moi.getEmail(),
                    "[IMF Pipeline] " + titre,
                    construireEmailViolation(violationUid, req, heuresRestantes, moi));
        }

        log.warn("VIOLATION DONNEES déclarée [uid={}] — {} — {} — déclarant: {}",
                violationUid, req.severite(), req.typeViolation(), moi.getUsername());

        Map<String, Object> reponse = Map.of(
                "violationUid",       violationUid,
                "statut",             "DECLAREE",
                "heuresRestantesAutori", Math.max(0, heuresRestantes),
                "deadlineAutorite",   req.dateDecouverte().plusHours(72).toString(),
                "message",            alerte
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reponse));
    }

    @Operation(summary = "Liste des violations déclarées par l'IMF")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> lister(
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long imfId = TenantContext.currentImfId();

        String sql = statut != null
                ? "SELECT * FROM app.violations_donnees WHERE imf_id = ? AND statut = ? ORDER BY date_declaration DESC LIMIT ? OFFSET ?"
                : "SELECT * FROM app.violations_donnees WHERE imf_id = ? ORDER BY date_declaration DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> violations = statut != null
                ? jdbc.queryForList(sql, imfId, statut, size, (long) page * size)
                : jdbc.queryForList(sql, imfId, size, (long) page * size);

        return ResponseEntity.ok(ApiResponse.ok(violations));
    }

    @Operation(summary = "Violations dont le délai de 72h pour notifier l'autorité approche")
    @GetMapping("/sla-autorite")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> slaAutorite() {
        Long imfId = TenantContext.currentImfId();
        List<Map<String, Object>> urgentes = jdbc.queryForList(
                "SELECT * FROM app.v_violations_sla_autorite WHERE imf_id = ? ORDER BY heures_restantes_autorite",
                imfId);
        return ResponseEntity.ok(ApiResponse.ok(urgentes));
    }

    @Operation(summary = "Mettre à jour le statut d'une violation")
    @PutMapping("/{uid}/statut")
    @Auditable(action = AuditTrail.ACTION_CHANGEMENT_STATUT,
               entiteType = AuditTrail.ENTITE_VIOLATION_DONNEES,
               entiteIdExpression = "#uid.toString()", motifExpression = "#statut")
    public ResponseEntity<ApiResponse<Void>> mettreAJourStatut(
            @PathVariable UUID uid,
            @RequestParam String statut,
            @RequestParam(required = false) String mesuresCorrectivesOuRapport) {

        Long imfId = TenantContext.currentImfId();
        User moi   = TenantContext.currentUser();

        if ("CLOTUREE".equals(statut)) {
            jdbc.update("""
                    UPDATE app.violations_donnees
                    SET statut = ?, rapport_final = ?,
                        cloture_par_id = ?, cloture_at = NOW(), updated_at = NOW()
                    WHERE uid = ?::uuid AND imf_id = ?
                    """, statut, mesuresCorrectivesOuRapport, moi.getId(), uid.toString(), imfId);
        } else {
            jdbc.update("""
                    UPDATE app.violations_donnees
                    SET statut = ?, mesures_correctives = ?, updated_at = NOW()
                    WHERE uid = ?::uuid AND imf_id = ?
                    """, statut, mesuresCorrectivesOuRapport, uid.toString(), imfId);
        }

        return ResponseEntity.ok(ApiResponse.ok("Statut violation " + uid + " → " + statut));
    }

    @Operation(summary = "Confirmer l'envoi de la notification à l'autorité de protection")
    @PutMapping("/{uid}/notif-autorite")
    @Auditable(action = AuditTrail.ACTION_MODIFICATION,
               entiteType = AuditTrail.ENTITE_VIOLATION_DONNEES,
               entiteIdExpression = "#uid.toString()")
    public ResponseEntity<ApiResponse<Void>> confirmerNotifAutorite(
            @PathVariable UUID uid,
            @RequestParam(required = false) String referenceAutorite) {

        Long imfId = TenantContext.currentImfId();
        jdbc.update("""
                UPDATE app.violations_donnees
                SET notif_autorite_envoyee = TRUE,
                    notif_autorite_at      = NOW(),
                    notif_autorite_ref     = ?,
                    updated_at             = NOW()
                WHERE uid = ?::uuid AND imf_id = ?
                """, referenceAutorite, uid.toString(), imfId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Notification autorité confirmée pour violation " + uid));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String construireEmailViolation(String uid, ViolationDonneesRequest req,
                                             long heuresRestantes, User declarant) {
        return String.format("""
                ALERTE — VIOLATION DE DONNÉES PERSONNELLES
                Loi n° 2024/017 du 23 décembre 2024 — Art. 22

                Violation %s déclarée par : %s
                Date de découverte : %s
                Type : %s
                Sévérité : %s

                Catégories de données concernées :
                %s

                Nombre de personnes estimées : %s

                Description :
                %s

                Mesures immédiates prises :
                %s

                DÉLAI LÉGAL :
                %s
                Deadline notification autorité : %s

                Action requise : Notifier l'autorité de protection des données
                sous 72h depuis la découverte (art. 22 §1 Loi 2024/017).
                """,
                uid,
                declarant.getUsername(),
                req.dateDecouverte(),
                req.typeViolation(),
                req.severite(),
                req.categoriesDonnees(),
                req.nbPersonnesEstimees() != null ? req.nbPersonnesEstimees().toString() : "Non estimé",
                req.description(),
                req.mesuresImmediates() != null ? req.mesuresImmediates() : "Aucune encore",
                heuresRestantes > 0
                        ? heuresRestantes + "h restantes pour notifier l'autorité"
                        : "DÉLAI DÉPASSÉ de " + (-heuresRestantes) + "h",
                req.dateDecouverte().plusHours(72)
        );
    }
}
