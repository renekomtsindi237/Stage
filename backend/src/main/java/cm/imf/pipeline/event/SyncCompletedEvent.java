package cm.imf.pipeline.event;

import cm.imf.pipeline.dto.response.SyncResponse;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Événement Spring publié après la fin d'une synchronisation mobile.
 * Transporté par le thread de la transaction, consommé après commit
 * (via @TransactionalEventListener) pour déclencher le scoring temps réel.
 */
public class SyncCompletedEvent extends ApplicationEvent {

    private final SyncResponse syncResponse;
    private final String       agentUsername;
    /** IDs externes (CBS) des clients dont les collectes viennent d'être insérées. */
    private final List<String> clientIds;
    /** IMF du tenant courant au moment de la sync. */
    private final Long         imfId;

    public SyncCompletedEvent(Object source, SyncResponse syncResponse,
                               String agentUsername, List<String> clientIds, Long imfId) {
        super(source);
        this.syncResponse  = syncResponse;
        this.agentUsername = agentUsername;
        this.clientIds     = clientIds != null ? List.copyOf(clientIds) : List.of();
        this.imfId         = imfId;
    }

    public SyncResponse getSyncResponse()  { return syncResponse; }
    public String       getAgentUsername() { return agentUsername; }
    public List<String> getClientIds()     { return clientIds; }
    public Long         getImfId()         { return imfId; }
}
