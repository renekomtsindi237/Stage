package cm.imf.pipeline.util;

import java.security.SecureRandom;

/**
 * Générateur de mots de passe sécurisés pour la création de comptes.
 * Garantit la présence d'au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial.
 */
public final class PasswordGeneratorUtil {

    private static final String UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER   = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS  = "0123456789";
    private static final String SPECIAL = "!@#$%^&*-_";
    private static final String ALL     = UPPER + LOWER + DIGITS + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LENGTH = 12;

    private PasswordGeneratorUtil() {}

    public static String generate() {
        char[] password = new char[LENGTH];
        // Au moins 1 de chaque catégorie
        password[0] = UPPER  .charAt(RANDOM.nextInt(UPPER.length()));
        password[1] = LOWER  .charAt(RANDOM.nextInt(LOWER.length()));
        password[2] = DIGITS .charAt(RANDOM.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length()));
        // Remplir le reste
        for (int i = 4; i < LENGTH; i++) {
            password[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
        }
        // Mélanger (Fisher-Yates)
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }
}
