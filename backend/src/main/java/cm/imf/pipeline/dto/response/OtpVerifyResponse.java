package cm.imf.pipeline.dto.response;

/**
 * Réponse à la vérification OTP.
 * L'OTP est le seul facteur d'authentification pour tous les rôles sauf SUPER_ADMIN
 * (1ère connexion ou Nième — même flux, toujours AUTHENTICATED).
 */
public record OtpVerifyResponse(

        String status,
        String accessToken,
        String refreshToken,
        String role,
        String username,
        String imfUid,
        String imfCode,
        String imfNom,
        Long   expiresIn

) {
    public static final String AUTHENTICATED = "AUTHENTICATED";

    public static OtpVerifyResponse authenticated(AuthResponse auth) {
        return new OtpVerifyResponse(
                AUTHENTICATED,
                auth.accessToken(), auth.refreshToken(),
                auth.role(), auth.username(),
                auth.imfUid(), auth.imfCode(), auth.imfNom(),
                auth.expiresIn()
        );
    }
}
