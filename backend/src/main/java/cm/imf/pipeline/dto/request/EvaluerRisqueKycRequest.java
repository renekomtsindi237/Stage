package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record EvaluerRisqueKycRequest(
        boolean estPep,
        boolean verifSanctions,
        boolean verifListesNoires,
        @Min(0) @Max(100) Integer scoreManuel,
        String motifRisqueEleve,
        String observations
) {}
