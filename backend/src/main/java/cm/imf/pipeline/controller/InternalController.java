package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.AlerteImpaye;
import cm.imf.pipeline.enums.StatutAlerte;
import cm.imf.pipeline.repository.AlerteRepository;
import cm.imf.pipeline.service.INotificationService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API interne consommée par le pipeline Python (DAG alertes_impayes).
 * Protégée par une clé API dans le header X-Internal-Api-Key.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Tag(name = "Internal", description = "Endpoints internes pipeline → Spring Boot")
public class InternalController {

    private final AlerteRepository     alerteRepository;
    private final INotificationService notificationService;
    private final SseEmitterRegistry   sseRegistry;

    @Value("${internal.api-key}")
    private String expectedApiKey;

    @Operation(summary = "Créer une alerte impayé depuis le pipeline Python")
    @PostMapping("/alertes")
    public ResponseEntity<Map<String, Object>> creerAlerte(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestBody Map<String, Object> payload) {

        validateApiKey(apiKey);

        String idPret = (String) payload.get("id_pret");
        int joursRetard = ((Number) payload.get("jours_retard")).intValue();
        BigDecimal montant = new BigDecimal(payload.get("montant_en_retard").toString());

        // Éviter les doublons : une seule alerte ACTIVE par prêt
        if (alerteRepository.findByIdPretAndStatutAlerte(idPret, StatutAlerte.ACTIVE).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Alerte ACTIVE déjà existante pour ce prêt", "id_pret", idPret));
        }

        AlerteImpaye alerte = AlerteImpaye.builder()
                .idPret(idPret)
                .dateGeneration(OffsetDateTime.now())
                .joursRetard(joursRetard)
                .montantEnRetard(montant)
                .statutAlerte(StatutAlerte.ACTIVE)
                .build();

        AlerteImpaye saved = alerteRepository.save(alerte);
        notificationService.notifierAlerteImpaye(saved.getId());

        log.info("Alerte créée via pipeline — prêt: {}, jours: {}", idPret, joursRetard);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("uid", saved.getUid() != null ? saved.getUid().toString() : null);
        body.put("id_pret", idPret);
        body.put("statut", "ACTIVE");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Pousser un événement SSE depuis le pipeline Python vers les clients connectés",
               description = "Payload attendu : {\"event\": \"KPI_UPDATED\", \"role\": \"RESPONSABLE_RECOUVREMENT\", "
                           + "\"data\": {...}, \"message\": \"...\"}. "
                           + "Si 'role' est absent, l'événement est diffusé à tous les rôles superviseurs.")
    @PostMapping("/sse/push")
    public ResponseEntity<Void> pushSseEvent(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestBody Map<String, Object> payload) {

        validateApiKey(apiKey);

        String event   = (String) payload.get("event");
        String role    = (String) payload.get("role");
        Object data    = payload.get("data");
        String message = payload.containsKey("message")
                ? (String) payload.get("message")
                : "Pipeline: " + event;

        if (role != null) {
            sseRegistry.broadcastToRole(role, new SseEventDto(event, role, message, data, OffsetDateTime.now()));
        } else {
            // Broadcast aux rôles superviseurs (pas aux agents terrain)
            for (String r : java.util.List.of(
                    "RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "ANALYSTE", "SUPER_ADMIN")) {
                sseRegistry.broadcastToRole(r, new SseEventDto(event, r, message, data, OffsetDateTime.now()));
            }
        }

        log.debug("SSE push depuis pipeline — event: {}, role: {}", event, role != null ? role : "all");
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Enregistrer un FCM token pour un utilisateur")
    @PostMapping("/fcm-token")
    public ResponseEntity<Void> registerFcmToken(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestBody Map<String, Object> payload) {

        validateApiKey(apiKey);
        Long userId = ((Number) payload.get("user_id")).longValue();
        String token = (String) payload.get("fcm_token");
        notificationService.registerFcmToken(userId, token);
        return ResponseEntity.noContent().build();
    }

    private void validateApiKey(String apiKey) {
        if (!expectedApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clé API interne invalide");
        }
    }
}
