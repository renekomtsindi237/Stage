package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.response.NotificationDto;
import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.Notification;
import cm.imf.pipeline.event.*;
import cm.imf.pipeline.i18n.SyncMessages;
import cm.imf.pipeline.repository.NotificationRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.INotifPersistService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistance des notifications SSE dans app.notifications.
 * Les @EventListener ici sont synchrones (même thread que le publisher),
 * garantissant l'accès à TenantContext.
 * Propagation REQUIRES_NEW : le save commit même si la tx parente rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifPersistServiceImpl implements INotifPersistService {

    private final NotificationRepository notifRepo;
    private final ObjectMapper objectMapper;

    // ── Listeners d'événements métier ─────────────────────────────────────────

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlerteChanged(AlerteChangedEvent event) {
        Long imfId = TenantContext.currentImfId();
        if (imfId == null) return;

        if (event.isCreation()) {
            String msg = SyncMessages.nouvelleAlerte(
                    event.getAlerte().idPret(), event.getAlerte().joursRetard());
            save(SseEventDto.TYPE_ALERTE_CREATED,
                    "Nouvelle alerte impayé", msg,
                    "RESPONSABLE_RECOUVREMENT", imfId, event.getAlerte());
        } else {
            String msg = switch (event.getAlerte().statutAlerte()) {
                case CLOTUREE  -> SyncMessages.alerteCloturee(event.getAlerte().idPret());
                case ESCALADEE -> SyncMessages.alerteEscaladee(event.getAlerte().idPret());
                default        -> "Alerte " + event.getAlerte().idPret() + " mise à jour.";
            };
            save(SseEventDto.TYPE_ALERTE_UPDATED,
                    "Alerte mise à jour", msg, null, imfId, event.getAlerte());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCollecteConfirmed(CollecteConfirmedEvent event) {
        Long imfId = TenantContext.currentImfId();
        if (imfId == null) return;
        String msg = "Collecte confirmée par " + event.getAgentUsername();
        save(SseEventDto.TYPE_COLLECTE_CONFIRMED,
                "Collecte confirmée", msg, null, imfId, event.getCollecte());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSyncCompleted(SyncCompletedEvent event) {
        Long imfId = TenantContext.currentImfId();
        if (imfId == null) return;
        String msg = "Sync de " + event.getAgentUsername() + " terminée.";
        save(SseEventDto.TYPE_SYNC_COMPLETED,
                "Synchronisation terminée", msg, null, imfId, event.getSyncResponse());
    }

    // ── INotifPersistService ──────────────────────────────────────────────────

    @Override
    @Transactional
    public NotificationDto save(String type, String titre, String message,
                                String targetRole, Long imfId, Object payload) {
        String payloadJson = null;
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                log.debug("Sérialisation payload notification ignorée : {}", e.getMessage());
            }
        }
        Notification notif = Notification.builder()
                .type(type).titre(titre).message(message)
                .targetRole(targetRole).imfId(imfId).payload(payloadJson)
                .build();
        return NotificationDto.from(notifRepo.save(notif));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long imfId, String role, int page, int size) {
        return notifRepo.findForRole(imfId, role, PageRequest.of(page, size))
                .map(NotificationDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long imfId, String role) {
        return notifRepo.countUnreadForRole(imfId, role);
    }

    @Override
    @Transactional
    public void markAsRead(java.util.UUID uid) {
        Long imfId = TenantContext.currentImfId();
        notifRepo.findByUidAndImfId(uid, imfId).ifPresent(n -> {
            n.setLu(true);
            notifRepo.save(n);
        });
    }

    @Override
    @Transactional
    public void markAllAsRead(Long imfId, String role) {
        notifRepo.markAllReadForRole(imfId, role);
    }
}
