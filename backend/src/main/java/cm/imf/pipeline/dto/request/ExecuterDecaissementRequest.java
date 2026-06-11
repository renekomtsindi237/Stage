package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecuterDecaissementRequest(
        @NotNull UUID contratUid,
        @NotNull @DecimalMin("1.00") BigDecimal montantNet,
        /** ESPECES | MOBILE_MONEY | VIREMENT | CHEQUE */
        @NotBlank String mode,
        String referencePaiement
) {}
