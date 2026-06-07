package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.JournalAudit;

import java.time.OffsetDateTime;

public record AuditEntryResponse(
        Long id,
        String username,
        String action,
        String entite,
        String entiteId,
        String details,
        String ipClient,
        String statut,
        OffsetDateTime createdAt
) {
    public static AuditEntryResponse from(JournalAudit a) {
        return new AuditEntryResponse(
                a.getId(), a.getUsername(), a.getAction(),
                a.getEntite(), a.getEntiteId(), a.getDetails(),
                a.getIpClient(), a.getStatut(), a.getCreatedAt()
        );
    }
}
