package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PretResponse(
        String idPret,
        String idClient,
        String nomClient,
        String nomAgence,
        String nomProduit,
        String nomAgent,
        BigDecimal montantPret,
        LocalDate dateDeblocage,
        LocalDate dateEcheance,
        BigDecimal montantRembourse,
        BigDecimal soldeRestant,
        String statutPret,
        int joursRetard
) {}
