package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiClientRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        /** UID de l'IMF cible (obligatoire pour SUPER_ADMIN, ignoré pour SUPPORT qui utilise son IMF). */
        String imfUid
) {}
