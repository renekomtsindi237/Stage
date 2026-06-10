package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête pour créer le compte DSI initial d'une IMF.
 * Aucun mot de passe requis : le DSI s'authentifie exclusivement via OTP email.
 */
public record CreateImfAdminRequest(
        @NotBlank @Size(min = 3, max = 50)
        String username,

        @NotBlank @Email
        String email
) {}
