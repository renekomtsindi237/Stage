package cm.imf.pipeline.event;

import cm.imf.pipeline.dto.response.SyncResponse;
import org.springframework.context.ApplicationEvent;

/**
 * Événement Spring publié après la fin d'une synchronisation mobile.
 * Permet de notifier le tableau de bord web en temps réel.
 */
public class SyncCompletedEvent extends ApplicationEvent {

    private final SyncResponse syncResponse;
    private final String agentUsername;

    public SyncCompletedEvent(Object source, SyncResponse syncResponse, String agentUsername) {
        super(source);
        this.syncResponse  = syncResponse;
        this.agentUsername = agentUsername;
    }

    public SyncResponse getSyncResponse()  { return syncResponse; }
    public String       getAgentUsername() { return agentUsername; }
}
