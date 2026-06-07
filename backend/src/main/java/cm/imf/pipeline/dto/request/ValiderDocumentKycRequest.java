package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotNull;

public record ValiderDocumentKycRequest(
        @NotNull Boolean valide,
        String motifRejet
) {}
