package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreerPlanApurementRequest(
        @NotNull @Min(1) Integer nbEcheances,
        BigDecimal montantParEcheance,
        LocalDate dateDebut
) {}
