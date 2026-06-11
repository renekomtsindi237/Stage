package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GenererContratRequest(
        @NotNull @DecimalMin("1.00") BigDecimal montantFinal,
        @NotNull @DecimalMin("0.001") BigDecimal tauxInteret,
        BigDecimal fraisDossier,
        @NotNull @Min(1) Integer nbEcheances,
        /** MENSUEL | HEBDOMADAIRE | QUOTIDIEN */
        @NotBlank String periodicite,
        LocalDate dateSignature
) {}
