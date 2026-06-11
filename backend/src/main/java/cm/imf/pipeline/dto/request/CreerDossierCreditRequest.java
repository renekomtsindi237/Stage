package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreerDossierCreditRequest(
        @NotBlank String clientId,
        String clientNom,
        @NotNull @DecimalMin("1.00") BigDecimal montantDemande,
        @NotNull @Min(1) Integer dureeMois,
        @NotBlank String objetFinancement,
        String secteurActivite,
        BigDecimal revenuEstime,
        BigDecimal chargesMensuelles,
        Long agenceId
) {}
