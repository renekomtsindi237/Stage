package cm.imf.pipeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Résultats possibles d'une action de recouvrement enregistrée par l'agent.
 */
public enum ResultatActionRecouvrement {
    EN_ATTENTE,
    CONTACT_ETABLI,
    PROMESSE_PAIEMENT,
    SANS_REPONSE,
    REFUSE,
    PAIEMENT_PARTIEL,
    PAIEMENT_EFFECTUE,
    ACCORD_OBTENU;

    @JsonCreator
    public static ResultatActionRecouvrement fromJson(String raw) {
        return RecouvrementEnumCodes.resultat(raw);
    }
}
