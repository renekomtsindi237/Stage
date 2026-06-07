package cm.imf.pipeline.security;

import cm.imf.pipeline.enums.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilitaire de masquage des données personnelles (PII) selon le rôle de l'acteur.
 *
 * Conformité art. 9 et 27 — Loi 2024/017 Cameroun :
 * les rôles sans besoin légitime voient les données anonymisées.
 *
 * Règles de masquage :
 *   Nom complet   : "Kouam Ndjomo"  → "K*** N***"
 *   Téléphone     : "697123456"     → "697***56"
 *   Email         : "a@b.com"       → "a***@b.com"
 *   Numéro compte : "CM00123456"    → "CM***56"
 *   Coordonnées   : lat/lon         → null (position masquée)
 *
 * Rôles pouvant voir les données en clair :
 *   RESPONSABLE_RECOUVREMENT, DIRECTEUR, DSI, SUPER_ADMIN
 * Rôles soumis au masquage :
 *   AGENT (ne voit que ses propres clients via /me), ANALYSTE
 */
public final class DataMaskingUtils {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\d{3})(\\d+)(\\d{2})$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^([^@])([^@]*)(@.+)$");

    private DataMaskingUtils() {}

    /**
     * Vérifie si le rôle courant a accès aux données PII non masquées.
     * AGENT voit ses propres données mais pas celles des autres clients.
     */
    public static boolean peutVoirDonneesCompletes(Role role) {
        return role == Role.RESPONSABLE_RECOUVREMENT
                || role == Role.DIRECTEUR
                || role == Role.DSI
                || role == Role.SUPER_ADMIN;
    }

    /**
     * Masque un nom complet : "Kouam Ndjomo" → "K*** N***"
     * Gère les noms composés (plusieurs mots).
     */
    public static String masquerNom(String nomComplet) {
        if (nomComplet == null || nomComplet.isBlank()) return null;
        String[] parts = nomComplet.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i].charAt(0)).append("***");
        }
        return sb.toString();
    }

    /**
     * Masque un numéro de téléphone : "697123456" → "697***56"
     * Conserve les 3 premiers et 2 derniers chiffres.
     */
    public static String masquerTelephone(String telephone) {
        if (telephone == null || telephone.isBlank()) return null;
        String digits = telephone.replaceAll("[^0-9+]", "");
        if (digits.length() < 6) return "***";
        String prefix = digits.substring(0, Math.min(3, digits.length() - 2));
        String suffix = digits.substring(digits.length() - 2);
        return prefix + "***" + suffix;
    }

    /**
     * Masque une adresse email : "alice@imf.cm" → "a***@imf.cm"
     */
    public static String masquerEmail(String email) {
        if (email == null || email.isBlank()) return null;
        var m = EMAIL_PATTERN.matcher(email);
        if (!m.matches()) return "***@***";
        return m.group(1) + "***" + m.group(3);
    }

    /**
     * Masque un numéro de compte/prêt : "CM00123456" → "CM***56"
     */
    public static String masquerNumeroCompte(String numero) {
        if (numero == null || numero.isBlank()) return null;
        if (numero.length() <= 4) return "***";
        String prefix = numero.substring(0, Math.min(2, numero.length() - 2));
        String suffix = numero.substring(numero.length() - 2);
        return prefix + "***" + suffix;
    }

    /**
     * Applique le masquage sur une Map JSONB issue de la piste d'audit.
     * Les champs connus comme PII sont remplacés par leurs versions masquées.
     * Les champs non-PII (montants, dates, statuts) restent en clair.
     *
     * @param data   Map originale (ancienne_valeur ou nouvelle_valeur)
     * @param role   rôle de l'acteur qui consulte l'audit
     * @return nouvelle Map avec PII masqués si nécessaire
     */
    public static Map<String, Object> masquerJsonAudit(Map<String, Object> data, Role role) {
        if (data == null) return null;
        if (peutVoirDonneesCompletes(role)) return data;

        Map<String, Object> masked = new HashMap<>(data);
        for (String champ : PII_CHAMPS) {
            if (masked.containsKey(champ)) {
                Object val = masked.get(champ);
                if (val instanceof String s) {
                    masked.put(champ, masquerChamp(champ, s));
                }
            }
        }
        // Coordonnées GPS : remplacement par null
        masked.remove("latitude");
        masked.remove("longitude");
        masked.remove("precisionMetres");

        return masked;
    }

    private static String masquerChamp(String champ, String valeur) {
        return switch (champ) {
            case "nomComplet", "nom_complet", "nom", "prenom" -> masquerNom(valeur);
            case "telephone"                                   -> masquerTelephone(valeur);
            case "email"                                       -> masquerEmail(valeur);
            case "numeroPret", "numero_pret", "idPret",
                 "id_pret", "numeroCompte", "numero_compte"   -> masquerNumeroCompte(valeur);
            case "cni", "numeroCni"                            -> masquerNumeroCompte(valeur);
            default                                            -> "***";
        };
    }

    private static final java.util.Set<String> PII_CHAMPS = java.util.Set.of(
            "nomComplet", "nom_complet", "nom", "prenom",
            "telephone", "email",
            "numeroPret", "numero_pret", "idPret", "id_pret",
            "numeroCompte", "numero_compte",
            "cni", "numeroCni",
            "adresse", "lieuNaissance"
    );
}
