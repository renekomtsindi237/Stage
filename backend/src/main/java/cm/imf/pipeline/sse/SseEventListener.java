package cm.imf.pipeline.sse;

import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.event.*;
import cm.imf.pipeline.i18n.SyncMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Écouteur des événements Spring du domaine.
 * Traduit chaque événement métier en notification SSE pour les clients connectés.
 *
 * Toutes les méthodes sont @Async pour ne pas bloquer les transactions métier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventListener {

    private final SseEmitterRegistry registry;

    @Async
    @EventListener
    public void onAlerteChanged(AlerteChangedEvent event) {
        SseEventDto dto;
        if (event.isCreation()) {
            String msg = SyncMessages.nouvelleAlerte(
                    event.getAlerte().idPret(),
                    event.getAlerte().joursRetard());
            dto = SseEventDto.alerteCreated(event.getAlerte(), msg);
        } else {
            String msg = switch (event.getAlerte().statutAlerte()) {
                case CLOTUREE  -> SyncMessages.alerteCloturee(event.getAlerte().idPret());
                case ESCALADEE -> SyncMessages.alerteEscaladee(event.getAlerte().idPret());
                default        -> "Alerte " + event.getAlerte().idPret() + " mise à jour.";
            };
            dto = SseEventDto.alerteUpdated(event.getAlerte(), msg);
        }
        registry.broadcast(dto);
        log.debug("SSE broadcast AlerteChanged — type: {}, idPret: {}",
                event.getChangeType(), event.getAlerte().idPret());
    }

    @Async
    @EventListener
    public void onCollecteConfirmed(CollecteConfirmedEvent event) {
        SseEventDto dto = SseEventDto.collecteConfirmed(
                event.getCollecte(), event.getAgentUsername());
        // Visible uniquement par les DIRECTEUR et RESPONSABLE_RECOUVREMENT (broadcast global)
        registry.broadcastAll(dto);
        log.debug("SSE broadcast CollecteConfirmed — agent: {}", event.getAgentUsername());
    }

    @Async
    @EventListener
    public void onPipelineStatus(PipelineStatusEvent event) {
        String msg = event.isSuccess()
                ? SyncMessages.pipelineTermine(event.getDagId())
                : SyncMessages.pipelineEchec(event.getDagId());
        SseEventDto dto = SseEventDto.pipelineStatus(event.getDagId(), event.isSuccess(), msg);
        // KPI updated → broadcast à tous
        if (event.isSuccess()) {
            registry.broadcastAll(SseEventDto.kpiUpdated(SyncMessages.KPI_UPDATED));
        }
        // Statut pipeline → DSI uniquement
        registry.broadcast(dto);
        log.info("SSE broadcast PipelineStatus — dag: {}, success: {}",
                event.getDagId(), event.isSuccess());
    }

    @Async
    @EventListener
    public void onSyncCompleted(SyncCompletedEvent event) {
        try {
            if (event.getSyncResponse() == null) return;
            SseEventDto dto = SseEventDto.syncCompleted(
                    event.getSyncResponse(), event.getAgentUsername());
            registry.broadcastAll(dto);
            log.debug("SSE broadcast SyncCompleted — agent: {}, syncId: {}",
                    event.getAgentUsername(), event.getSyncResponse().syncId());
        } catch (Exception e) {
            log.warn("SSE SyncCompleted ignoré : {}", e.getMessage());
        }
    }
}
