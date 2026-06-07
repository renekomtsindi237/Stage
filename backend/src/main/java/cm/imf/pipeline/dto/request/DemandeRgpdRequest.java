package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DemandeRgpdRequest(

        @NotBlank
        @Pattern(regexp = "ACCES|RECTIFICATION|EFFACEMENT|OPPOSITION|PORTABILITE|LIMITATION",
                 message = "Type de droit invalide")
        String typeDroit,

        @NotBlank
        @Size(max = 2000)
        String perimetre,

        /** Finalité spécifique concernée — optionnel (pour OPPOSITION partielle). */
        @Size(max = 100)
        String finaliteConcernee
) {}
