package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload SUPER_ADMIN pour modifier un utilisateur d'une IMF via la supervision.
 * Le rôle SUPER_ADMIN est interdit — un SUPER_ADMIN ne peut pas appartenir à une IMF.
 */
public record UpdateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Email String email,
        @NotNull Role role,
        String zoneId
) {}
