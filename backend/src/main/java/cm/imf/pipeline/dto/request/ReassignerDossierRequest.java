package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReassignerDossierRequest(
        @NotNull(message = "L'identifiant du nouvel agent est obligatoire.")
        UUID nouvelAgentUid,
        String motif
) {}
