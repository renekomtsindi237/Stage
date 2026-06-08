package cm.imf.pipeline.event;

import cm.imf.pipeline.service.IRealtimeScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Écoute les événements de synchronisation mobile et déclenche le scoring
 * MCRS temps réel pour les clients affectés.
 *
 * @TransactionalEventListener(AFTER_COMMIT) garantit que les collectes sont
 * physiquement persistées en base avant que le scoring ne soit lancé.
 *
 * @Async exécute le scoring dans le pool "asyncExecutor" pour ne pas bloquer
 * le thread HTTP de la réponse de synchronisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncEventListener {

    private final IRealtimeScoringService realtimeScoringService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("asyncExecutor")
    public void onSyncCompleted(SyncCompletedEvent event) {
        if (event.getClientIds().isEmpty()) {
            log.debug("SyncCompletedEvent reçu sans clientIds — scoring ignoré (agent: {})",
                    event.getAgentUsername());
            return;
        }
        log.info("Déclenchement scoring temps réel — agent: {}, {} client(s), imfId: {}",
                event.getAgentUsername(), event.getClientIds().size(), event.getImfId());

        realtimeScoringService.scorerClientsApresSync(
                event.getClientIds(),
                event.getImfId(),
                event.getAgentUsername()
        );
    }
}
