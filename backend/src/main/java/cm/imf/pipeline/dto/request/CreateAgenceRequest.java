package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAgenceRequest(
        @NotBlank @Size(max = 100)
        String nom,

        @Size(max = 100)
        String ville,

        @Size(max = 100)
        String responsable,

        @Size(max = 20)
        String telephone
) {}
