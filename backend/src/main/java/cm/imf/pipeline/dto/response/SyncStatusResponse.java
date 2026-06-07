package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

/**
 * État de synchronisation d'un appareil spécifique.
 * Retourné par GET /api/sync/status/{deviceId}
 */
public record SyncStatusResponse(

        String deviceId,

        /** Horodatage de la dernière synchronisation réussie. */
        OffsetDateTime derniereSyncAt,

        /** Nombre total de sessions de sync enregistrées pour cet appareil. */
        int nbSyncTotal,

        /** Nombre de collectes confirmées sur toutes les sessions. */
        int nbCollectesConfirmees,

        /** Nombre de conflits en attente de résolution. */
        int nbConflitsOuverts,

        /** Message de statut lisible par l'utilisateur. */
        String message
) {}
