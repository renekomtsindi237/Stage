package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VoterComiteRequest(
        @NotNull UUID comiteUid,
        /** POUR | CONTRE | ABSTENTION */
        @NotBlank String vote,
        String commentaire
) {}
