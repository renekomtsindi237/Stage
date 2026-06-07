package cm.imf.pipeline.security;

import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests TenantContext — isolation multi-tenant (imf_id par requête).
 * Critique pour la sécurité : aucun utilisateur ne doit accéder
 * aux données d'un autre IMF.
 */
@DisplayName("TenantContext — isolation multi-tenant")
class TenantContextTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setCurrentUser(User user) {
        var auth = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User userWithImf(Long imfId) {
        Imf imf = new Imf();
        imf.setId(imfId);
        imf.setCode("IMF-" + imfId);

        User user = new User();
        user.setId(imfId + 100);
        user.setUsername("user_imf_" + imfId);
        user.setRole(Role.ANALYSTE);
        user.setImf(imf);
        return user;
    }

    // ── currentImfId ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("currentImfId() → retourne l'ID de l'IMF de l'utilisateur connecté")
    void currentImfId_retourne_id_correct() {
        setCurrentUser(userWithImf(42L));
        assertThat(TenantContext.currentImfId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Deux IMF différents → TenantContext.currentImfId() les distingue")
    void isolation_deux_imf_differents() {
        setCurrentUser(userWithImf(1L));
        Long imfId1 = TenantContext.currentImfId();

        SecurityContextHolder.clearContext();

        setCurrentUser(userWithImf(2L));
        Long imfId2 = TenantContext.currentImfId();

        assertThat(imfId1).isNotEqualTo(imfId2);
        assertThat(imfId1).isEqualTo(1L);
        assertThat(imfId2).isEqualTo(2L);
    }

    @Test
    @DisplayName("SUPER_ADMIN sans IMF → currentImfId() retourne null")
    void superAdmin_sans_imf_retourne_null() {
        User superAdmin = new User();
        superAdmin.setId(1L);
        superAdmin.setUsername("super");
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setImf(null);
        setCurrentUser(superAdmin);

        assertThat(TenantContext.currentImfId()).isNull();
    }

    @Test
    @DisplayName("Sans authentification → currentImfId() retourne null (pas d'exception)")
    void sans_auth_retourne_null() {
        SecurityContextHolder.clearContext();
        assertThatCode(TenantContext::currentImfId).doesNotThrowAnyException();
        assertThat(TenantContext.currentImfId()).isNull();
    }

    // ── isSuperAdmin ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isSuperAdmin() → true pour SUPER_ADMIN, false pour DSI")
    void isSuperAdmin_discrimine_correctement() {
        User superAdmin = new User();
        superAdmin.setId(1L);
        superAdmin.setUsername("sa");
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setImf(null);
        setCurrentUser(superAdmin);

        assertThat(TenantContext.isSuperAdmin()).isTrue();

        SecurityContextHolder.clearContext();

        setCurrentUser(userWithImf(1L)); // ANALYSTE avec IMF
        assertThat(TenantContext.isSuperAdmin()).isFalse();
    }

    // ── currentUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("currentUser() → retourne l'utilisateur JPA exact")
    void currentUser_retourne_user_exact() {
        User user = userWithImf(5L);
        setCurrentUser(user);

        assertThat(TenantContext.currentUser())
                .isSameAs(user)
                .extracting(User::getUsername)
                .isEqualTo("user_imf_5");
    }
}
