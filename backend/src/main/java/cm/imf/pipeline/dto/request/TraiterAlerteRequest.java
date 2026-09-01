package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TraiterAlerteRequest(
        @NotBlank String statut,
        @Size(max = 2000) String note
) {}
