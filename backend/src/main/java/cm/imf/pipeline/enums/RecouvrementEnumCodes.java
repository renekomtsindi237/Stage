package cm.imf.pipeline.enums;

import java.util.Locale;

/**
 * Normalise les libellés envoyés par l'UI ou stockés dans d'anciens seeds
 * vers les constantes Java actuelles.
 */
final class RecouvrementEnumCodes {

    private RecouvrementEnumCodes() {}

    static String norm(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    static TypeActionRecouvrement typeAction(String raw) {
        String v = norm(raw);
        if (v == null) {
            return null;
        }
        try {
            return TypeActionRecouvrement.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return switch (v) {
            case "MISE_EN_DEMEURE", "LETTRE_MISE_EN_DEMEURE" -> TypeActionRecouvrement.MISE_EN_DEMEURE_LETTRE;
            case "HUISSIER" -> TypeActionRecouvrement.INTERVENTION_HUISSIER;
            default -> throw new IllegalArgumentException("Type d'action inconnu : " + raw);
        };
    }

    static ResultatActionRecouvrement resultat(String raw) {
        String v = norm(raw);
        if (v == null) {
            return null;
        }
        try {
            return ResultatActionRecouvrement.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return switch (v) {
            case "CLIENT_CONTACTE", "CONTACTE" -> ResultatActionRecouvrement.CONTACT_ETABLI;
            case "PROMESSE_DE_PAIEMENT" -> ResultatActionRecouvrement.PROMESSE_PAIEMENT;
            case "SANS_SUITE", "CLIENT_ABSENT" -> ResultatActionRecouvrement.SANS_REPONSE;
            case "CLIENT_REFUSE" -> ResultatActionRecouvrement.REFUSE;
            case "PAIEMENT_TOTAL" -> ResultatActionRecouvrement.PAIEMENT_EFFECTUE;
            case "PAIEMENT_PARTIEL" -> ResultatActionRecouvrement.PAIEMENT_PARTIEL;
            case "LETTRE_ENVOYEE", "COURRIER_ENVOYE" -> ResultatActionRecouvrement.EN_ATTENTE;
            default -> throw new IllegalArgumentException("Résultat d'action inconnu : " + raw);
        };
    }

    static CanalPaiement canal(String raw) {
        String v = norm(raw);
        if (v == null) {
            return null;
        }
        try {
            return CanalPaiement.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return switch (v) {
            case "MTN_MOBILE_MONEY", "MOMO", "MTN_MOMO" -> CanalPaiement.MTN;
            case "ORANGE_MONEY", "OM" -> CanalPaiement.ORANGE;
            case "CASH", "LIQUIDE" -> CanalPaiement.ESPECES;
            case "TELEPHONE", "TERRAIN", "COURRIER" -> null;
            default -> throw new IllegalArgumentException("Canal de paiement inconnu : " + raw);
        };
    }
}
