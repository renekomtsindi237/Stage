package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse complète d'une session de synchronisation.
 * Retournée après POST /api/sync/collectes.
 *
 * Fournit un résumé global + le détail item par item
 * pour que l'app mobile puisse mettre à jour son état local.
 */
public record SyncResponse(

        /** UUID de la session de sync (tel que soumis par le client). */
        String syncId,

        /** Horodatage serveur de fin de traitement. */
        OffsetDateTime processedAt,

        /** Statut global de la synchronisation. */
        StatutGlobal statutGlobal,

        /** Message récapitulatif clair pour l'utilisateur. */
        String messageResume,

        /** Statistiques du batch. */
        SyncStats stats,

        /** Résultats détaillés par collecte. */
        List<SyncItemResult> resultats
) {

    /**
     * Statut global de la session de synchronisation.
     * Permet à l'app mobile de savoir si elle doit réessayer ou non.
     */
    public enum StatutGlobal {
        /** Tous les items traités avec succès (y compris les doublons détectés). */
        COMPLETE,
        /** Certains items ont des conflits ou des erreurs — nécessite attention. */
        PARTIELLE,
        /** Tous les items ont échoué — réessayez plus tard. */
        ECHEC
    }

    /**
     * Statistiques agrégées du batch de synchronisation.
     */
    public record SyncStats(
            int total,
            int succes,
            int doublons,
            int conflits,
            int erreurs,
            int enAttente
    ) {
        public static SyncStats compute(List<SyncItemResult> resultats) {
            int succes    = 0, doublons = 0, conflits = 0, erreurs = 0, enAttente = 0;
            for (SyncItemResult r : resultats) {
                switch (r.code()) {
                    case SyncItemResult.CODE_SUCCESS    -> succes++;
                    case SyncItemResult.CODE_DOUBLON    -> doublons++;
                    case SyncItemResult.CODE_CONFLIT    -> conflits++;
                    case SyncItemResult.CODE_ERREUR     -> erreurs++;
                    case SyncItemResult.CODE_EN_ATTENTE -> enAttente++;
                }
            }
            return new SyncStats(resultats.size(), succes, doublons, conflits, erreurs, enAttente);
        }
    }

    public static SyncResponse of(String syncId, List<SyncItemResult> resultats,
                                   String messageResume) {
        SyncStats stats = SyncStats.compute(resultats);
        StatutGlobal statut = computeStatut(stats);
        return new SyncResponse(syncId, OffsetDateTime.now(), statut, messageResume, stats, resultats);
    }

    private static StatutGlobal computeStatut(SyncStats s) {
        int echecs = s.conflits() + s.erreurs();
        if (echecs == 0) return StatutGlobal.COMPLETE;
        if (echecs == s.total()) return StatutGlobal.ECHEC;
        return StatutGlobal.PARTIELLE;
    }
}
