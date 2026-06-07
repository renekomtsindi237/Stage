package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.TypeGarantie;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OuvrirDossierRequest(
        @NotBlank String idPret,
        String nomClient,
        @NotNull @DecimalMin("0.01") BigDecimal montantImpaye,
        @NotNull @Min(1) Integer joursRetard,

        /** Date du premier impayé — base COBAC pour le calcul de la catégorie */
        LocalDate datePremiereEcheanceImpayee,

        UUID agentResponsableUid,

        /** Nom du cautionnaire ou du groupe de caution solidaire */
        String nomCaution,
        String telephoneCaution,
        TypeGarantie typeGarantie
) {}
