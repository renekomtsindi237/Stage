package cm.imf.pipeline.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;

public record ViolationDonneesRequest(

        @NotNull
        @JsonAlias("date_decouverte")
        OffsetDateTime dateDecouverte,

        @NotBlank
        @Pattern(regexp = "ACCES_NON_AUTORISE|DIVULGATION_ACCIDENTELLE|PERTE_DONNEES|MODIFICATION_NON_AUTORISEE|RANSOMWARE|EXFILTRATION|AUTRE")
        @JsonAlias("type_violation")
        String typeViolation,

        @NotBlank
        @JsonAlias("descriptionViolation")
        String description,

        @NotEmpty
        @JsonAlias("categories_donnees")
        List<String> categoriesDonnees,

        @Min(0)
        @JsonAlias("nombrePersonnesConcernees")
        Integer nbPersonnesEstimees,

        String entitesConcernees,

        @Pattern(regexp = "FAIBLE|MODERE|ELEVE|CRITIQUE")
        String severite,

        @JsonAlias("mesuresPrisesImmediatement")
        String mesuresImmediates,

        boolean notifAutoriteRequise,

        boolean notifPersonnesRequise
) {}
