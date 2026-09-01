package cm.imf.pipeline.sse;

import cm.imf.pipeline.dto.response.SseEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre thread-safe des connexions SSE actives.
 *
 * Chaque utilisateur connecté dispose d'un SseEmitter identifié par son username.
 * Le registre gère :
 *   - l'enregistrement et la suppression d'emitters
 *   - le broadcast ciblé par rôle ou global
 *   - le heartbeat périodique pour maintenir les connexions HTTP
 *   - le nettoyage automatique des connexions mortes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    private final ObjectMapper objectMapper;

    /** Map username → SseEmitter. ConcurrentHashMap pour la thread-safety. */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** Map username → rôle (pour le broadcast ciblé). */
    private final Map<String, String> userRoles = new ConcurrentHashMap<>();

    /** Timeout SSE = 30 min. Heartbeat 10 s — sous les idle timeout HTTP/2 et QUIC Cloudflare. */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    // ── Enregistrement ────────────────────────────────────────────────────────

    /**
     * Enregistre un nouveau client SSE. Remplace l'ancien emitter si l'utilisateur
     * se reconnecte (ex: refresh de page Angular).
     */
    public SseEmitter register(String username, String role) {
        // Ferme proprement l'ancien emitter si existant
        SseEmitter existing = emitters.get(username);
        if (existing != null) {
            try { existing.complete(); } catch (Exception ignored) {}
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> {
            emitters.remove(username);
            userRoles.remove(username);
            log.debug("SSE déconnecté : {}", username);
        });

        emitter.onTimeout(() -> {
            emitters.remove(username);
            userRoles.remove(username);
            log.debug("SSE timeout : {}", username);
        });

        emitter.onError(e -> {
            emitters.remove(username);
            userRoles.remove(username);
            log.debug("SSE erreur pour {} : {}", username, e.getMessage());
        });

        emitters.put(username, emitter);
        userRoles.put(username, role);

        log.info("SSE connecté : {} (rôle: {}) — {} client(s) actif(s)",
                username, role, emitters.size());

        return emitter;
    }

    // ── Diffusion ─────────────────────────────────────────────────────────────

    /**
     * Broadcast à tous les clients connectés.
     */
    public void broadcastAll(SseEventDto event) {
        emitters.forEach((username, emitter) -> sendToEmitter(username, emitter, event));
    }

    /**
     * Broadcast ciblé aux utilisateurs ayant un rôle précis.
     * Ex: broadcastToRole("RESPONSABLE_RECOUVREMENT", alerteEvent)
     */
    public void broadcastToRole(String role, SseEventDto event) {
        emitters.forEach((username, emitter) -> {
            if (role.equals(userRoles.get(username))) {
                sendToEmitter(username, emitter, event);
            }
        });
    }

    /**
     * Envoie un événement à un utilisateur spécifique.
     */
    public void sendToUser(String username, SseEventDto event) {
        SseEmitter emitter = emitters.get(username);
        if (emitter != null) {
            sendToEmitter(username, emitter, event);
        }
    }

    /**
     * Broadcast intelligent : si targetRole est renseigné dans l'événement,
     * envoie uniquement aux utilisateurs de ce rôle ; sinon broadcast global.
     */
    public void broadcast(SseEventDto event) {
        if (event.targetRole() != null) {
            broadcastToRole(event.targetRole(), event);
        } else {
            broadcastAll(event);
        }
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    /**
     * Heartbeat toutes les 15 s — sous le plafond Cloudflare (~100 s).
     */
    @Scheduled(fixedDelay = 10_000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        SseEventDto heartbeat = SseEventDto.heartbeat();
        emitters.forEach((username, emitter) -> sendToEmitter(username, emitter, heartbeat));
        log.trace("Heartbeat SSE envoyé à {} client(s)", emitters.size());
    }

    // ── État ──────────────────────────────────────────────────────────────────

    public int getConnectedCount() {
        return emitters.size();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void sendToEmitter(String username, SseEmitter emitter, SseEventDto event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(json));
        } catch (IOException e) {
            log.debug("SSE send échoué pour {} — connexion probablement fermée : {}",
                    username, e.getMessage());
            emitter.completeWithError(e);
            emitters.remove(username);
            userRoles.remove(username);
        }
    }
}
