package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.Notification;

import java.time.OffsetDateTime;

public record NotificationDto(
        String uid,
        String type,
        String titre,
        String message,
        String targetRole,
        boolean lu,
        OffsetDateTime createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getUid() != null ? n.getUid().toString() : null,
                n.getType(), n.getTitre(), n.getMessage(),
                n.getTargetRole(), n.isLu(), n.getCreatedAt());
    }
}
