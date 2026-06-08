package cm.imf.pipeline.dto.response;

/**
 * Réponse à la vérification OTP.
 *
 * status = "AUTHENTICATED"      → tokens directement (compte déjà activé)
 * status = "MUST_SET_PASSWORD"  → resetToken requis pour définir le mot de passe (première connexion)
 */
public record OtpVerifyResponse(

        String status,

        // ── Cas AUTHENTICATED ──────────────────────────────────────────────
        String  accessToken,
        String  refreshToken,
        String  role,
        String  username,
        String  imfUid,
        String  imfCode,
        String  imfNom,
        Long    expiresIn,

        // ── Cas MUST_SET_PASSWORD ──────────────────────────────────────────
        String  resetToken,
        Long    resetExpiresIn

) {
    public static final String AUTHENTICATED     = "AUTHENTICATED";
    public static final String MUST_SET_PASSWORD = "MUST_SET_PASSWORD";

    /** Construit une réponse pour un compte déjà activé. */
    public static OtpVerifyResponse authenticated(AuthResponse auth) {
        return new OtpVerifyResponse(
                AUTHENTICATED,
                auth.accessToken(), auth.refreshToken(),
                auth.role(), auth.username(),
                auth.imfUid(), auth.imfCode(), auth.imfNom(),
                auth.expiresIn(),
                null, null
        );
    }

    /** Construit une réponse pour un compte en attente d'activation (mot de passe à définir). */
    public static OtpVerifyResponse mustSetPassword(String resetToken, long resetExpiresIn) {
        return new OtpVerifyResponse(
                MUST_SET_PASSWORD,
                null, null, null, null, null, null, null, null,
                resetToken, resetExpiresIn
        );
    }
}
