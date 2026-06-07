package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoint Server-Sent Events (SSE) pour les notifications temps réel.
 *
 * Clients :
 *   - Angular Web : EventSource('/api/sse/stream?token=...')
 *   - Flutter : http package avec listen() sur le stream SSE
 *
 * Authentification SSE :
 *   Le JWT est passé en query parameter 'token' car EventSource (navigateur)
 *   ne supporte pas les headers personnalisés.
 *   Le JwtAuthenticationFilter reconnaît le paramètre 'token' pour cet endpoint.
 *
 * Reconnexion automatique :
 *   Le protocole SSE gère la reconnexion côté client (retry: 3000ms par défaut).
 *   Le heartbeat toutes les 30s maintient les connexions actives via les proxies.
 */
@Slf4j
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
@Tag(name = "SSE", description = "Notifications temps réel Server-Sent Events")
public class SseController {

    private final SseEmitterRegistry registry;

    @Operation(
            summary = "Ouvrir un flux SSE de notifications temps réel",
            description = """
                    Ouvre une connexion SSE persistante. Le serveur pousse des événements
                    JSON pour : nouvelles alertes, collectes confirmées, mises à jour KPI,
                    statuts pipeline.

                    Le client doit passer le JWT Bearer en paramètre 'token' car
                    EventSource (navigateur) ne supporte pas les headers personnalisés.

                    Exemple Angular : new EventSource('/api/sse/stream?token=' + jwt)
                    """
    )
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal User user) {
        String role = user.getRole().name();
        SseEmitter emitter = registry.register(user.getUsername(), role);

        // Envoie immédiatement un événement de bienvenue avec le statut du serveur
        try {
            emitter.send(SseEmitter.event()
                    .name(SseEventDto.TYPE_HEARTBEAT)
                    .data("{\"statut\":\"CONNECTE\",\"message\":\"Flux SSE établi. Notifications actives.\"}"));
        } catch (Exception e) {
            log.debug("Impossible d'envoyer l'événement de bienvenue SSE : {}", e.getMessage());
        }

        return emitter;
    }

    @Operation(summary = "Nombre de clients SSE connectés (monitoring)")
    @GetMapping("/connected-count")
    public int getConnectedCount() {
        return registry.getConnectedCount();
    }
}
