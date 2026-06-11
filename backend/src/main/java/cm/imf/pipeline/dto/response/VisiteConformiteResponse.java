package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.VisiteConformite;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VisiteConformiteResponse(
        UUID uid,
        Long dossierId,
        Long agentCreditId,
        LocalDate dateVisite,
        boolean conformiteObservee,
        String observations,
        Double latitude,
        Double longitude,
        OffsetDateTime createdAt
) {
    public static VisiteConformiteResponse from(VisiteConformite v) {
        return new VisiteConformiteResponse(
                v.getUid(), v.getDossierId(), v.getAgentCreditId(), v.getDateVisite(),
                v.isConformiteObservee(), v.getObservations(), v.getLatitude(),
                v.getLongitude(), v.getCreatedAt()
        );
    }
}
