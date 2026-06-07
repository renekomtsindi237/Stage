package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.StatutEcheance;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Requête de mise à jour d'une échéance (paiement ou changement de statut).
 */
public record EcheanceUpdateRequest(

        @NotNull(message = "Le statut est obligatoire")
        StatutEcheance statut,

        @DecimalMin(value = "0.0", message = "Le montant payé ne peut pas être négatif")
        BigDecimal montantPaye,

        LocalDate datePaiement,

        String observation
) {}
