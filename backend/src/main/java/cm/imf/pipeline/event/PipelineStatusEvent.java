package cm.imf.pipeline.event;

import org.springframework.context.ApplicationEvent;

/**
 * Événement Spring publié par InternalController lorsque le pipeline
 * Airflow notifie Spring Boot d'un changement de statut DAG.
 * Consommé par SseEventListener pour diffuser aux DSI connectés.
 */
public class PipelineStatusEvent extends ApplicationEvent {

    private final String dagId;
    private final boolean success;
    private final String message;

    public PipelineStatusEvent(Object source, String dagId, boolean success, String message) {
        super(source);
        this.dagId   = dagId;
        this.success = success;
        this.message = message;
    }

    public String  getDagId()   { return dagId; }
    public boolean isSuccess()  { return success; }
    public String  getMessage() { return message; }
}
