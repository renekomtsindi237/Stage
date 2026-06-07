package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.NotificationDto;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.INotifPersistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Historique notifications temps réel — lecture et marquage")
public class NotificationController {

    private final INotifPersistService notifService;

    @Operation(summary = "Liste paginée des notifications de l'utilisateur connecté")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (user.getImf() == null) {
            return ResponseEntity.ok(ApiResponse.ok(Page.empty()));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                notifService.getNotifications(
                        user.getImf().getId(), user.getRole().name(), page, size)));
    }

    @Operation(summary = "Nombre de notifications non lues")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal User user) {
        long count = user.getImf() == null ? 0 :
                notifService.countUnread(user.getImf().getId(), user.getRole().name());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }

    @Operation(summary = "Marquer une notification comme lue")
    @PutMapping("/{uid}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID uid) {
        notifService.markAsRead(uid);
        return ResponseEntity.ok(ApiResponse.ok("Notification lue"));
    }

    @Operation(summary = "Marquer toutes les notifications comme lues")
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal User user) {
        if (user.getImf() != null) {
            notifService.markAllAsRead(user.getImf().getId(), user.getRole().name());
        }
        return ResponseEntity.ok(ApiResponse.ok("Toutes les notifications lues"));
    }
}
