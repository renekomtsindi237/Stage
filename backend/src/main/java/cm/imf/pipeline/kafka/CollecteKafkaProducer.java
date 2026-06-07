package cm.imf.pipeline.kafka;

import cm.imf.pipeline.events.CollecteEvent;
import cm.imf.pipeline.events.CanalPaiement;
import cm.imf.pipeline.events.StatutCollecte;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Producteur Kafka — Événements collecte confirmée.
 *
 * Appelé par CollecteService lorsqu'une collecte terrain est validée.
 * Sérialise en Avro (schéma CollecteEvent.avsc) et publie sur
 * imf.collectes.confirmed (partitionné par client_id_externe).
 *
 * Le message déclenche côté Python :
 *   - dag_collectes.py (batch Airflow)
 *   - Flink kpi_realtime_job.py (streaming temps-réel)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollecteKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Émet un événement CollecteEvent sur le topic Kafka.
     *
     * @param collecteId      ID externe de la collecte
     * @param clientId        ID externe du client (clé de partition)
     * @param agentId         ID de l'agent terrain
     * @param agenceId        ID de l'agence
     * @param imfId           ID de l'IMF (multi-tenant)
     * @param regionId        Région camerounaise (REG01-REG10)
     * @param montant         Montant en FCFA
     * @param canal           Canal de paiement
     * @param referenceMomo   Référence mobile money (nullable)
     */
    public CompletableFuture<SendResult<String, Object>> sendCollecteConfirmee(
            String collecteId, String clientId, String agentId,
            String agenceId, int imfId, String regionId,
            double montant, CanalPaiement canal, String referenceMomo) {

        CollecteEvent event = CollecteEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCollecteId(collecteId)
                .setClientIdExterne(clientId)
                .setAgentId(agentId)
                .setAgenceId(agenceId)
                .setImfId(imfId)
                .setRegionId(regionId)
                .setMontant(montant)
                .setCanal(canal)
                .setStatut(StatutCollecte.CONFIRMEE)
                .setReferenceMomo(referenceMomo)
                .setTimestampMs(Instant.now())
                .setDateCollecte(LocalDate.now())
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.COLLECTES_CONFIRMED, clientId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Erreur envoi CollecteEvent [client={}] : {}", clientId, ex.getMessage());
            } else {
                log.debug("CollecteEvent envoyé [client={}] partition={} offset={}",
                        clientId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
        return future;
    }
}
