package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

public record ViolationDonneesRequest(

        @NotNull
        OffsetDateTime dateDecouverte,

        @NotBlank
        @Pattern(regexp = "ACCES_NON_AUTORISE|DIVULGATION_ACCIDENTELLE|PERTE_DONNEES|MODIFICATION_NON_AUTORISEE|RANSOMWARE|EXFILTRATION|AUTRE")
        String typeViolation,

        @NotBlank
        String description,

        @NotBlank
        String categoriesDonnees,

        @Min(0)
        Integer nbPersonnesEstimees,

        String entitesConcernees,

        @NotBlank
        @Pattern(regexp = "FAIBLE|MODERE|ELEVE|CRITIQUE")
        String severite,

        String mesuresImmediates,

        boolean notifAutoriteRequise,

        boolean notifPersonnesRequise
) {}
