package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EtiquetteRequest(

        @NotBlank
        @Size(max = 50)
        String dossierRef,

        @NotBlank
        @Pattern(regexp = "DOSSIER_RECOUVREMENT|DOSSIER_CREANCE|DOSSIER_CLIENT",
                 message = "Type de dossier invalide")
        String dossierType,

        @NotBlank
        @Pattern(regexp = "PRIORITAIRE|SENSIBLE|CONTENTIEUX|RESTRUCTURE|PERDU|DECEDE|FRAUDE_SUSPECTEE|GARANTIE_ACTIVEE|SAISONNALITE|SUIVI_SPECIAL",
                 message = "Code étiquette invalide")
        String codeEtiquette,

        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "Couleur hex invalide (ex: #FF5733)")
        String couleur,

        @Size(max = 100)
        String libelleCustom,

        @Size(max = 1000)
        String commentaire,

        @Size(max = 500)
        String motif
) {}
