package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.ResultatVerificationKyc;
import jakarta.validation.constraints.NotNull;

public record VerifierKycRequest(
        @NotNull ResultatVerificationKyc resultat,
        NiveauKyc niveauApprouve,   // Niveau validé par le verificateur (peut être < niveauDemande)
        String commentaire,
        String motifRejet           // Obligatoire si resultat = REJETE
) {}
