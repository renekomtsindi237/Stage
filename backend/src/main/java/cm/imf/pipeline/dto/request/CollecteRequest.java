package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.CanalPaiement;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CollecteRequest(
        @NotBlank String idCollecteMobile,
        @NotBlank String clientId,
        String pretId,
        @NotNull LocalDate dateCollecte,
        @NotNull @DecimalMin("0.01") BigDecimal montantCollecte,
        @NotNull CanalPaiement canalPaiement,
        String referenceTransaction,
        String observation,
        BigDecimal latitude,
        BigDecimal longitude
) {}
