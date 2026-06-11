package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record AjouterGarantieRequest(
        /** AVAL | NANTISSEMENT_STOCK | NANTISSEMENT_MATERIEL | HYPOTHEQUE | AUTRE */
        @NotBlank String type,
        @NotBlank String description,
        BigDecimal valeurEstimee,
        String referenceDocument,
        String cautionNom,
        String cautionTelephone
) {}
