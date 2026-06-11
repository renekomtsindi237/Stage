package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DeleguerAutoriteRequest(
        @NotNull(message = "L'identifiant du délégataire est obligatoire.")
        UUID delegataireUid,

        /** Rôle dont les droits sont délégués (ex : CHEF_AGENCE pour validation comité). */
        String roleDelegue,

        /** Plafond maximal d'autorité délégué en FCFA. Null = sans limite explicite. */
        BigDecimal montantSeuil,

        /** Date de fin de la délégation. Null = durée indéfinie. */
        @FutureOrPresent
        LocalDate dateFin,

        String motif
) {}
