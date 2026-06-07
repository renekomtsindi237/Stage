package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.enums.StatutCollecte;

/**
 * Résultat du traitement d'une collecte individuelle lors d'une synchronisation.
 * Inclut un message clair et actionnable pour l'utilisateur final.
 */
public record SyncItemResult(

        /** Identifiant mobile de la collecte (UUID Flutter). */
        String idCollecteMobile,

        /** Statut de traitement. */
        StatutCollecte statut,

        /**
         * Code court lisible par machine pour l'app mobile.
         * Valeurs possibles : SUCCESS, DOUBLON, CONFLIT, ERREUR, EN_ATTENTE
         */
        String code,

        /**
         * Message complet en français pour affichage utilisateur.
         * Doit être suffisamment explicite pour que l'agent comprenne
         * ce qui s'est passé et quelle action entreprendre si nécessaire.
         */
        String messageUtilisateur,

        /**
         * ID serveur de la collecte si elle a été enregistrée.
         * Null si rejetée ou en erreur.
         */
        Long idServeur
) {
    // ── Codes standardisés ────────────────────────────────────────────────────

    public static final String CODE_SUCCESS       = "SUCCESS";
    public static final String CODE_DOUBLON       = "DOUBLON";
    public static final String CODE_CONFLIT       = "CONFLIT";
    public static final String CODE_ERREUR        = "ERREUR";
    public static final String CODE_EN_ATTENTE    = "EN_ATTENTE";

    // ── Factory methods ───────────────────────────────────────────────────────

    public static SyncItemResult succes(String idMobile, Long idServeur, String message) {
        return new SyncItemResult(idMobile, StatutCollecte.CONFIRMEE,
                CODE_SUCCESS, message, idServeur);
    }

    public static SyncItemResult doublon(String idMobile, Long idServeur, String message) {
        return new SyncItemResult(idMobile, StatutCollecte.DOUBLON,
                CODE_DOUBLON, message, idServeur);
    }

    public static SyncItemResult conflit(String idMobile, String message) {
        return new SyncItemResult(idMobile, StatutCollecte.REJETEE,
                CODE_CONFLIT, message, null);
    }

    public static SyncItemResult enAttente(String idMobile, Long idServeur, String message) {
        return new SyncItemResult(idMobile, StatutCollecte.SOUMISE,
                CODE_EN_ATTENTE, message, idServeur);
    }

    public static SyncItemResult erreur(String idMobile, String message) {
        return new SyncItemResult(idMobile, StatutCollecte.REJETEE,
                CODE_ERREUR, message, null);
    }

    public boolean isSuccess() {
        return CODE_SUCCESS.equals(code) || CODE_DOUBLON.equals(code);
    }
}
