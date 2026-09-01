package cm.imf.pipeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Types d'actions enregistrables dans un dossier de recouvrement.
 * Couvre les canaux amiables, informels, formels et judiciaires utilisés au Cameroun.
 */
public enum TypeActionRecouvrement {

    // ── Relances amiables ─────────────────────────────────────────────────────
    APPEL_TELEPHONIQUE,
    SMS_RELANCE,
    EMAIL_RELANCE,
    VISITE_TERRAIN,

    // ── Médiation informelle (spécifique contexte camerounais) ────────────────
    /** Intervention du chef de quartier ou du chef de village comme médiateur */
    MEDIATION_CHEF_QUARTIER,
    /** Médiation par le chef de famille ou un leader communautaire */
    MEDIATION_FAMILLE,

    // ── Contact caution / garantie ────────────────────────────────────────────
    /** Contact du cautionnaire ou du groupe de caution solidaire */
    CONTACT_CAUTION,
    /** Activation ou mise en jeu de la garantie (nantissement, hypothèque) */
    SAISIE_GARANTIE,

    // ── Procédures formelles (OHADA) ──────────────────────────────────────────
    /** Envoi de la lettre de mise en demeure via huissier de justice */
    MISE_EN_DEMEURE_LETTRE,
    /** Intervention d'un huissier pour signification ou saisie */
    INTERVENTION_HUISSIER,
    /** Passage en comité de recouvrement interne EMF */
    COMITE_RECOUVREMENT,
    /** Assignation devant le tribunal de commerce (OHADA) */
    ASSIGNATION_TRIBUNAL,

    // ── Encaissements ─────────────────────────────────────────────────────────
    ENCAISSEMENT_PARTIEL,
    ENCAISSEMENT_TOTAL,

    // ── Résolution ───────────────────────────────────────────────────────────
    ACCORD_REECHELONNEMENT,
    /** Cession de la créance à une société de recouvrement (SRC) */
    CESSION_CREANCE,
    RADIATION;

    @JsonCreator
    public static TypeActionRecouvrement fromJson(String raw) {
        return RecouvrementEnumCodes.typeAction(raw);
    }
}
