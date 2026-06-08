package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

/**
 * Enveloppe d'un événement Server-Sent Events (SSE).
 * Sérialisé en JSON et poussé aux clients connectés (Angular web, Flutter).
 *
 * Types d'événements :
 *   ALERTE_CREATED    — nouvelle alerte impayé détectée par le pipeline
 *   ALERTE_UPDATED    — statut d'une alerte modifié (clôture, escalade)
 *   COLLECTE_CONFIRMED — collecte terrain confirmée après sync
 *   KPI_UPDATED       — tableau de bord rafraîchi après exécution pipeline
 *   PIPELINE_STATUS   — statut d'un DAG Airflow (SUCCESS/FAILED)
 *   SYNC_COMPLETED    — synchronisation mobile terminée
 *   HEARTBEAT         — maintien de connexion (toutes les 30s)
 */
public record SseEventDto(
        String type,
        String targetRole,   // null = broadcast à tous, sinon rôle ciblé
        String message,      // message lisible par l'utilisateur
        Object payload,      // données structurées (null pour heartbeat)
        OffsetDateTime timestamp
) {
    // ── Types d'événements ────────────────────────────────────────────────────

    public static final String TYPE_ALERTE_CREATED          = "ALERTE_CREATED";
    public static final String TYPE_ALERTE_UPDATED          = "ALERTE_UPDATED";
    public static final String TYPE_COLLECTE_CONFIRMED      = "COLLECTE_CONFIRMED";
    public static final String TYPE_KPI_UPDATED             = "KPI_UPDATED";
    public static final String TYPE_PIPELINE_STATUS         = "PIPELINE_STATUS";
    public static final String TYPE_SYNC_COMPLETED          = "SYNC_COMPLETED";
    public static final String TYPE_HEARTBEAT               = "HEARTBEAT";
    public static final String TYPE_AGENT_POSITION_UPDATED  = "AGENT_POSITION_UPDATED";
    public static final String TYPE_SCORING_UPDATE          = "SCORING_UPDATE";

    // ── Factory methods ───────────────────────────────────────────────────────

    public static SseEventDto heartbeat() {
        return new SseEventDto(TYPE_HEARTBEAT, null, "ping", null, OffsetDateTime.now());
    }

    public static SseEventDto alerteCreated(AlerteResponse alerte, String message) {
        return new SseEventDto(TYPE_ALERTE_CREATED, "RESPONSABLE_RECOUVREMENT",
                message, alerte, OffsetDateTime.now());
    }

    public static SseEventDto alerteUpdated(AlerteResponse alerte, String message) {
        return new SseEventDto(TYPE_ALERTE_UPDATED, null, message, alerte, OffsetDateTime.now());
    }

    public static SseEventDto collecteConfirmed(CollecteResponse collecte, String agentUsername) {
        return new SseEventDto(TYPE_COLLECTE_CONFIRMED, null,
                "Collecte confirmée par " + agentUsername, collecte, OffsetDateTime.now());
    }

    public static SseEventDto kpiUpdated(String message) {
        return new SseEventDto(TYPE_KPI_UPDATED, null, message, null, OffsetDateTime.now());
    }

    public static SseEventDto pipelineStatus(String dagId, boolean success, String message) {
        return new SseEventDto(TYPE_PIPELINE_STATUS, "DSI", message,
                java.util.Map.of("dagId", dagId, "success", success), OffsetDateTime.now());
    }

    public static SseEventDto syncCompleted(SyncResponse sync, String agentUsername) {
        return new SseEventDto(TYPE_SYNC_COMPLETED, null,
                "Sync de " + agentUsername + " terminée : " + sync.stats().succes() +
                "/" + sync.stats().total() + " collecte(s)",
                sync.stats(), OffsetDateTime.now());
    }

    /**
     * Mise à jour des scores MCRS après synchronisation mobile.
     * Envoyé à l'agent qui vient de syncer ET aux RESPONSABLE_RECOUVREMENT.
     *
     * @param agentUsername  username de l'agent qui a déclenché la sync
     * @param nbClients      nombre de clients scorés
     * @param scoresResume   liste réduite [{clientId, scoreMcrs, classeRisque}]
     */
    public static SseEventDto scoringUpdate(
            String agentUsername, int nbClients, Object scoresResume) {
        String msg = nbClients == 0
                ? "Aucun client à scorer après la sync de " + agentUsername
                : nbClients + " client(s) rescorés en temps réel après sync de " + agentUsername;
        return new SseEventDto(TYPE_SCORING_UPDATE, null, msg, scoresResume, OffsetDateTime.now());
    }

    /**
     * Position GPS d'un agent mise à jour.
     * Ciblé aux RESPONSABLE_RECOUVREMENT et DIRECTEUR (appelé deux fois depuis PositionServiceImpl).
     */
    public static SseEventDto agentPositionUpdated(
            cm.imf.pipeline.dto.response.AgentPositionResponse position) {
        return new SseEventDto(
                TYPE_AGENT_POSITION_UPDATED,
                null,   // le service appelant choisit la cible (broadcastToRole)
                "Position de " + position.nomComplet() + " mise à jour",
                position,
                OffsetDateTime.now());
    }
}
