package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccordReechelonnementRequest(
        @NotNull @DecimalMin("1.00") BigDecimal nouveauMontantMensuel,
        @NotNull @Min(1) Integer nombreNouvellesEcheances,
        @NotNull LocalDate dateDebutNouvelEcheancier,
        BigDecimal tauxInteretAnnuel,
        UUID approuveParUid,
        LocalDate dateSignature,
        String observations
) {}
