package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreanceResponse(
    String uid,
    String idPretExterne,
    String clientIdExterne,
    String agenceUid,
    String nomAgence,
    BigDecimal montantInitial,
    BigDecimal montantImpaye,
    BigDecimal capitalRestantDu,
    BigDecimal interetsRetard,
    BigDecimal montantProvision,
    int joursRetard,
    String categoriePar,
    String classeRisqueCobac,
    BigDecimal tauxProvisionCobac,
    String typeGarantie,
    String statut,
    String agentResponsableUsername,
    LocalDate dateOuvertureCreance,
    ScoreMcrs scoreMcrs,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public record ScoreMcrs(
        double scoreCrs,
        double scoreRps,
        double scoreCsi,
        double scoreMcrs,
        String classeRisque,
        double probabiliteDefaut90j,
        String actionRecommandee,
        int prioriteRecouvrement,
        String topFeature,
        double topShapValue
    ) {}
}
