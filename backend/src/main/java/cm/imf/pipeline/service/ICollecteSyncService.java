package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.SyncRequest;
import cm.imf.pipeline.dto.response.SyncResponse;
import cm.imf.pipeline.dto.response.SyncStatusResponse;
import cm.imf.pipeline.entity.User;

/**
 * Contrat du service de synchronisation hors-ligne → en ligne.
 * Traite les batches de collectes reçues depuis l'app Flutter.
 */
public interface ICollecteSyncService {

    /**
     * Traite un batch de collectes en attente de synchronisation.
     * Idempotent : un même syncId ne produit jamais deux insertions.
     *
     * @param request  le batch de collectes + métadonnées de session
     * @param agent    l'agent authentifié
     * @param ipClient l'adresse IP de l'appareil mobile
     */
    SyncResponse processSync(SyncRequest request, User agent, String ipClient);

    /**
     * Retourne l'historique et le statut de synchronisation d'un appareil.
     */
    SyncStatusResponse getSyncStatus(String deviceId);
}
