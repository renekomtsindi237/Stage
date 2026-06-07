package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Le mot de passe actuel est requis")
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est requis")
        @Size(min = 8, max = 100, message = "Le nouveau mot de passe doit faire au moins 8 caractères")
        String newPassword
) {}
