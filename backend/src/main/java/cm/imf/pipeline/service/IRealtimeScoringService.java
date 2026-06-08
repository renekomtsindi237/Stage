package cm.imf.pipeline.service;

import java.util.List;

public interface IRealtimeScoringService {

    /**
     * Score les clients affectés par une synchronisation mobile.
     * Appelé de manière asynchrone depuis le SyncEventListener,
     * après que la transaction de sync a été committée en base.
     *
     * @param clientIds    identifiants externes CBS des clients à scorer
     * @param imfId        identifiant du tenant IMF
     * @param agentUsername username de l'agent qui a déclenché la sync (pour SSE ciblé)
     */
    void scorerClientsApresSync(List<String> clientIds, Long imfId, String agentUsername);
}
