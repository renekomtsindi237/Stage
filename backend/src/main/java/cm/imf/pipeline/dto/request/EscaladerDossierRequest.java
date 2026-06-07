package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.RecouvrementPhase;
import jakarta.validation.constraints.NotNull;

public record EscaladerDossierRequest(
        @NotNull RecouvrementPhase nouvellePhase,
        String motif
) {}
