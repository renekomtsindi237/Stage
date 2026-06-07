package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CollecteEpargneResponse(
    String uid,
    UUID uuidMobile,
    String clientIdExterne,
    String cycleUid,
    String nomCycle,
    String agentUid,
    String agentUsername,
    String agenceUid,
    String nomAgence,
    BigDecimal montantCollecte,
    LocalDate dateCollecte,
    LocalTime heureCollecte,
    String canalPaiement,
    String referenceTransaction,
    BigDecimal latitude,
    BigDecimal longitude,
    String statut,
    String motifRejet,
    String observation,
    OffsetDateTime syncedAt,
    OffsetDateTime createdAt
) {
    public record SyncCollectesResponse(
        int total,
        int acceptees,
        int doublons,
        int rejetees
    ) {}

    public record KpiJour(
        LocalDate date,
        int nbCollectes,
        BigDecimal montantTotal,
        BigDecimal montantEspeces,
        BigDecimal montantMobileMoney,
        int nbClientsUniques
    ) {}
}
