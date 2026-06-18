package cm.imf.pipeline.kafka;

import cm.imf.pipeline.entity.ClientScore;
import cm.imf.pipeline.events.ScoringResult;
import cm.imf.pipeline.repository.ClientScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Consommateur Kafka — Résultats de scoring MCRS.
 *
 * Écoute le topic imf.ml.scoring.results (produit par le service FastAPI ML Python).
 * Persiste/met à jour le score dans ml.client_scores via upsert JPA.
 * Commit manuel (MANUAL_IMMEDIATE) pour garantir at-least-once.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class ScoringResultConsumer {

    private final ClientScoreRepository clientScoreRepository;

    @KafkaListener(
        topics = KafkaTopics.SCORING_RESULTS,
        groupId = "${kafka.consumer.group-id:imf-backend-spring}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onScoringResult(ConsumerRecord<String, ScoringResult> record, Acknowledgment ack) {
        ScoringResult result = record.value();
        if (result == null) {
            ack.acknowledge();
            return;
        }

        try {
            upsertScore(result);
            ack.acknowledge();
            log.debug("Score MCRS persiste [client={}] mcrs={} niveau={}",
                    result.getClientIdExterne(),
                    result.getScoreMcrs(),
                    result.getNiveauRisque());
        } catch (Exception e) {
            log.error("Erreur persistance ScoringResult [client={}] : {}",
                    result.getClientIdExterne(), e.getMessage(), e);
            // Ne pas acquitter — le DefaultErrorHandler retentera (3x, 2s backoff)
            throw e;
        }
    }

    private void upsertScore(ScoringResult r) {
        List<String> alertes = r.getAlertes() != null
                ? r.getAlertes().stream().map(Object::toString).toList()
                : List.of();

        ClientScore score = clientScoreRepository
                .findByClientIdExterneAndImfId(r.getClientIdExterne().toString(), (long) r.getImfId())
                .orElseGet(() -> ClientScore.builder()
                        .clientIdExterne(r.getClientIdExterne().toString())
                        .imfId((long) r.getImfId())
                        .build());

        score.setScoreMcrs(r.getScoreMcrs());
        score.setScoreCrs(r.getScoreCrs());
        score.setScoreRps(r.getScoreRps());
        score.setScoreCsi(r.getScoreCsi());
        score.setNiveauRisque(r.getNiveauRisque().toString());
        score.setCobacClasse(r.getCobacClasse().toString());
        score.setCobacProvisionTaux(r.getCobacProvisionTaux());
        score.setAlertes(alertes);
        score.setModelVersion(r.getModelVersion() != null ? r.getModelVersion().toString() : "1.0.0");
        score.setScoredAt(r.getTimestampMs());

        clientScoreRepository.save(score);
    }
}
