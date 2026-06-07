package cm.imf.pipeline.event;

import cm.imf.pipeline.dto.response.CollecteResponse;
import org.springframework.context.ApplicationEvent;

/**
 * Événement Spring publié après la confirmation d'une collecte terrain.
 * Consommé par SseEventListener pour pousser une notification SSE
 * aux superviseurs connectés.
 */
public class CollecteConfirmedEvent extends ApplicationEvent {

    private final CollecteResponse collecte;
    private final String agentUsername;

    public CollecteConfirmedEvent(Object source, CollecteResponse collecte, String agentUsername) {
        super(source);
        this.collecte = collecte;
        this.agentUsername = agentUsername;
    }

    public CollecteResponse getCollecte() { return collecte; }
    public String getAgentUsername()       { return agentUsername; }
}
