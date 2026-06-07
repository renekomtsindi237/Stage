package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TraiterDemandeRgpdRequest(

        @NotBlank
        @Pattern(regexp = "EN_COURS|TRAITEE|REFUSEE|PARTIELLEMENT_TRAITEE",
                 message = "Statut invalide")
        String statut,

        @NotBlank
        @Size(max = 3000)
        String reponse,

        @Size(max = 1000)
        String motifRefus,

        /** URL de l'export généré (pour PORTABILITE / ACCES). */
        @Size(max = 500)
        String exportUrl
) {}
