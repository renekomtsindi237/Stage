package cm.imf.pipeline.enums;

/**
 * Statut de vérification d'un paiement Mobile Money (MTN MoMo / Orange Money).
 * Prévient les fraudes par capture d'écran falsifiée — pratique répandue au Cameroun.
 */
public enum StatutVerifMomo {

    /** Référence soumise, vérification auprès de l'opérateur en attente */
    EN_ATTENTE,

    /** Transaction confirmée via l'API opérateur ou par l'agent MoMo */
    VERIFIE,

    /** Référence invalide ou transaction non trouvée — paiement rejeté */
    REJETE
}
