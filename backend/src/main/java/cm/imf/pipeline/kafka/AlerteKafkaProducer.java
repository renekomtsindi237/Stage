package cm.imf.pipeline.kafka;

import cm.imf.pipeline.events.ActionRecouvrement;
import cm.imf.pipeline.events.AlerteRisque;
import cm.imf.pipeline.events.ClasseCOBAC;
import cm.imf.pipeline.events.NiveauRisque;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Producteur Kafka — Alertes risque MCRS.
 *
 * Appelé par KpiService ou le job Flink lorsque le score MCRS d'un client
 * franchit un seuil critique. Publie sur imf.alertes.risque.
 * Partitionné par client_id_externe (même clé que le scoring).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlerteKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Émet une AlerteRisque Avro sur le topic Kafka.
     *
     * @param clientId          ID externe du client (clé de partition)
     * @param imfId             ID de l'IMF (multi-tenant)
     * @param agenceId          ID de l'agence du client
     * @param regionId          Région camerounaise (REG01-REG10)
     * @param scoreMcrs         Score MCRS composite [0,1]
     * @param scoreCrs          Collection Reliability Score
     * @param scoreRps          Recovery Prediction Score
     * @param scoreCsi          Client Solvency Index
     * @param niveauRisque      Niveau de risque FAIBLE|MODERE|ELEVE|CRITIQUE
     * @param cobacClasse       Classe COBAC A|B|C|D|E
     * @param cobacProvisionTaux Taux de provision COBAC (0.0-1.0)
     * @param typesAlertes      Types d'alertes déclenchées
     * @param joursRetard       Nombre de jours de retard
     * @param action            Action de recouvrement recommandée
     * @param source            Source de l'alerte (dag_ml_scoring | api_ml | stream_flink)
     */
    public CompletableFuture<SendResult<String, Object>> sendAlerteRisque(
            String clientId, int imfId, String agenceId, String regionId,
            double scoreMcrs, double scoreCrs, double scoreRps, double scoreCsi,
            NiveauRisque niveauRisque, ClasseCOBAC cobacClasse, double cobacProvisionTaux,
            List<String> typesAlertes, int joursRetard,
            ActionRecouvrement action, String source) {

        AlerteRisque alerte = AlerteRisque.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAlerteId(UUID.randomUUID().toString())
                .setClientIdExterne(clientId)
                .setImfId(imfId)
                .setAgenceId(agenceId)
                .setRegionId(regionId)
                .setScoreMcrs(scoreMcrs)
                .setScoreCrs(scoreCrs)
                .setScoreRps(scoreRps)
                .setScoreCsi(scoreCsi)
                .setNiveauRisque(niveauRisque)
                .setCobacClasse(cobacClasse)
                .setCobacProvisionTaux(cobacProvisionTaux)
                .setTypesAlertes(typesAlertes)
                .setJoursRetard(joursRetard)
                .setActionRecommandee(action)
                .setTimestampMs(Instant.now())
                .setSource(source)
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.ALERTES_RISQUE, clientId, alerte);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Erreur envoi AlerteRisque [client={}] : {}", clientId, ex.getMessage());
            } else {
                log.debug("AlerteRisque envoyee [client={}] mcrs={} niveau={} partition={} offset={}",
                        clientId, scoreMcrs, niveauRisque,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
        return future;
    }

    /**
     * Surcharge simplifiée avec les valeurs par défaut adaptées au contexte camerounais.
     * Détermine automatiquement la ClasseCOBAC et l'action de recouvrement selon le retard.
     */
    public CompletableFuture<SendResult<String, Object>> sendAlerteRisque(
            String clientId, int imfId, String agenceId, String regionId,
            double scoreMcrs, double scoreCrs, double scoreRps, double scoreCsi,
            String niveauRisqueStr, int joursRetard, List<String> typesAlertes, String source) {

        NiveauRisque niveau = NiveauRisque.valueOf(niveauRisqueStr);
        ClasseCOBAC cobac = classifyCobac(joursRetard);
        double provisionTaux = cobacProvision(cobac);
        ActionRecouvrement action = recommendAction(joursRetard, scoreMcrs);

        return sendAlerteRisque(
                clientId, imfId, agenceId, regionId,
                scoreMcrs, scoreCrs, scoreRps, scoreCsi,
                niveau, cobac, provisionTaux,
                typesAlertes, joursRetard, action, source
        );
    }

    private ClasseCOBAC classifyCobac(int joursRetard) {
        if (joursRetard < 30)  return ClasseCOBAC.A;
        if (joursRetard < 90)  return ClasseCOBAC.B;
        if (joursRetard < 180) return ClasseCOBAC.C;
        if (joursRetard < 360) return ClasseCOBAC.D;
        return ClasseCOBAC.E;
    }

    private double cobacProvision(ClasseCOBAC cobac) {
        return switch (cobac) {
            case A -> 0.00;
            case B -> 0.20;
            case C -> 0.50;
            case D -> 0.80;
            case E -> 1.00;
        };
    }

    private ActionRecouvrement recommendAction(int joursRetard, double scoreMcrs) {
        if (joursRetard == 0 && scoreMcrs < 0.4) return ActionRecouvrement.AUCUNE;
        if (joursRetard < 30 || scoreMcrs < 0.5) return ActionRecouvrement.RELANCE_PREVENTIVE;
        if (joursRetard < 90)                     return ActionRecouvrement.VISITE_TERRAIN;
        return ActionRecouvrement.MISE_EN_DEMEURE;
    }
}
