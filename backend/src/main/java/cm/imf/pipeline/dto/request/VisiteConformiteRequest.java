package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record VisiteConformiteRequest(
        LocalDate dateVisite,
        @NotNull Boolean conformiteObservee,
        @NotBlank String observations,
        Double latitude,
        Double longitude
) {}
