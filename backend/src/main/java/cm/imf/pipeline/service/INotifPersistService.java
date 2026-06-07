package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.NotificationDto;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Service de persistance des notifications temps réel.
 * Distinct de INotificationService (push FCM/email).
 */
public interface INotifPersistService {

    NotificationDto save(String type, String titre, String message,
                         String targetRole, Long imfId, Object payload);

    Page<NotificationDto> getNotifications(Long imfId, String role, int page, int size);

    long countUnread(Long imfId, String role);

    void markAsRead(UUID uid);

    void markAllAsRead(Long imfId, String role);
}
