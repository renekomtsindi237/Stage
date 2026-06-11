package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DecisionComiteRequest(
        @NotNull UUID comiteUid,
        /** APPROUVE | REJETE | AJOURNE | RESTRUCTURE */
        @NotBlank String decision,
        BigDecimal montantApprouve,
        BigDecimal tauxApprouve,
        Integer dureeApprouvee,
        String conditions,
        String motifRejet
) {}
