package cm.imf.pipeline.security;

import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitaire statique pour accéder au tenant (IMF) de l'utilisateur courant
 * depuis n'importe quel service sans injection supplémentaire.
 *
 * Usage :
 *   Long imfId = TenantContext.currentImfId();   // null si SUPER_ADMIN
 *   boolean root = TenantContext.isSuperAdmin();
 */
public final class TenantContext {

    private TenantContext() {}

    /** ID de l'IMF de l'utilisateur connecté. NULL pour SUPER_ADMIN. */
    public static Long currentImfId() {
        User user = currentUser();
        return (user != null && user.getImf() != null) ? user.getImf().getId() : null;
    }

    /** Entité IMF complète de l'utilisateur connecté. NULL pour SUPER_ADMIN. */
    public static Imf currentImf() {
        User user = currentUser();
        return user != null ? user.getImf() : null;
    }

    /**
     * Vrai si l'utilisateur courant est SUPER_ADMIN (accès cross-IMF).
     * Les services peuvent l'utiliser pour décider de filtrer ou non par IMF.
     */
    public static boolean isSuperAdmin() {
        User user = currentUser();
        return user != null && user.getRole() == Role.SUPER_ADMIN;
    }

    /** Utilisateur JPA courant extrait du SecurityContext. */
    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) return null;
        return user;
    }
}
