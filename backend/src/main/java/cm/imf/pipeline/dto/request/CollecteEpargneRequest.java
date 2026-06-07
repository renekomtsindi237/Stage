package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CollecteEpargneRequest(

    @NotNull(message = "L'UUID mobile est obligatoire pour la déduplication")
    UUID uuidMobile,

    @NotBlank(message = "L'identifiant client est obligatoire")
    String clientIdExterne,

    UUID cycleUid,

    UUID agenceUid,

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    BigDecimal montantCollecte,

    @NotNull(message = "La date de collecte est obligatoire")
    LocalDate dateCollecte,

    LocalTime heureCollecte,

    @NotBlank(message = "Le canal de paiement est obligatoire")
    String canalPaiement,

    String referenceTransaction,

    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal precisionGpsMetres,

    String observation
) {}
