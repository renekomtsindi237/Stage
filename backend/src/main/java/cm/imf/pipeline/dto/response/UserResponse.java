package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;

import java.time.OffsetDateTime;

public record UserResponse(
        String uid,
        String username,
        Role role,
        String zoneId,
        String email,
        String avatarUrl,
        String imfUid,
        String imfCode,
        String imfNom,
        boolean actif,
        boolean mustChangePassword,
        OffsetDateTime lastLogin,
        OffsetDateTime createdAt,

        // ── Géolocalisation ───────────────────────────────────────────────────
        Double latitude,
        Double longitude,

        // ── Préférences utilisateur ───────────────────────────────────────────
        String prefLangue,
        String prefTheme,
        boolean notificationsActives,
        boolean notifAlertes,
        boolean notifCollectes,
        boolean notifSync,
        boolean notifPipeline,
        int elementsParPage
) {
    /** URL publique de l'avatar par défaut — servi par le backend pour tous les profils. */
    public static final String DEFAULT_AVATAR_URL = "/api/v1/public/default-avatar";

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getUid() != null ? u.getUid().toString() : null,
                u.getUsername(),
                u.getRole(),
                u.getZoneId(),
                u.getEmail(),
                u.getAvatarUrl() != null && !u.getAvatarUrl().contains("/users/me/avatar")
                        ? u.getAvatarUrl() : DEFAULT_AVATAR_URL,
                u.getImf() != null && u.getImf().getUid() != null ? u.getImf().getUid().toString() : null,
                u.getImf() != null ? u.getImf().getCode() : null,
                u.getImf() != null ? u.getImf().getNom()  : null,
                u.isActif(),
                u.isMustChangePassword(),
                u.getLastLogin(),
                u.getCreatedAt(),
                u.getLatitude(),
                u.getLongitude(),
                u.getPrefLangue(),
                u.getPrefTheme(),
                u.isNotificationsActives(),
                u.isNotifAlertes(),
                u.isNotifCollectes(),
                u.isNotifSync(),
                u.isNotifPipeline(),
                u.getElementsParPage() != null ? u.getElementsParPage() : 20
        );
    }
}
