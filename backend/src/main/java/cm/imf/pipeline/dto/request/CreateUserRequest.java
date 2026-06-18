package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50)
        String username,

        @Size(min = 8, max = 100)
        String password,

        @Email @Size(max = 255)
        String email,

        @NotNull
        Role role,

        @Size(max = 100)
        String zoneId,

        @Pattern(regexp = "^(fr|en)$", message = "Langue supportée : fr ou en")
        String prefLangue,

        Double latitude,

        Double longitude
) {}
