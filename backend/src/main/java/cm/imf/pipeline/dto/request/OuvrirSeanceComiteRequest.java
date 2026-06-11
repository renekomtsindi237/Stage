package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record OuvrirSeanceComiteRequest(
        /** AGENCE | SIEGE | GRAND_COMITE */
        @NotBlank String typeComite,
        @NotNull OffsetDateTime dateSeance
) {}
