package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ValidationChefRequest(
        /** VALIDER (passe EN_COMITE) ou REJETER */
        @NotBlank String action,
        String motif
) {}
