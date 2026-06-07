package cm.imf.pipeline.event;

import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.enums.StatutAlerte;
import org.springframework.context.ApplicationEvent;

/**
 * Événement Spring publié lors de la création ou d'un changement
 * de statut d'une AlerteImpaye.
 * Consommé par SseEventListener pour pousser une notification SSE.
 */
public class AlerteChangedEvent extends ApplicationEvent {

    public enum ChangeType { CREATED, UPDATED }

    private final AlerteResponse alerte;
    private final ChangeType changeType;
    private final StatutAlerte ancienStatut; // null si CREATED

    public AlerteChangedEvent(Object source, AlerteResponse alerte,
                               ChangeType changeType, StatutAlerte ancienStatut) {
        super(source);
        this.alerte       = alerte;
        this.changeType   = changeType;
        this.ancienStatut = ancienStatut;
    }

    public AlerteResponse  getAlerte()       { return alerte; }
    public ChangeType      getChangeType()   { return changeType; }
    public StatutAlerte    getAncienStatut() { return ancienStatut; }
    public boolean         isCreation()      { return changeType == ChangeType.CREATED; }
}
