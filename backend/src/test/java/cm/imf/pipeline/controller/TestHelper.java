package cm.imf.pipeline.controller;

import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/**
 * Fixtures partagées pour les tests @WebMvcTest.
 *
 * Utiliser {@code .with(TestHelper.asDsi())} pour injecter un vrai
 * {@code cm.imf.pipeline.entity.User} comme principal — ce que
 * {@code @WithMockUser} ne fait pas (il crée un UserDetails Spring générique).
 * Ceci est nécessaire pour les endpoints qui accèdent à user.getImf().getId().
 */
public final class TestHelper {

    private TestHelper() {}

    // ── Fixtures Imf ─────────────────────────────────────────────────────────

    public static Imf mockImf() {
        Imf imf = new Imf();
        imf.setId(1L);
        imf.setUid(UUID.fromString("11111111-0000-0000-0000-000000000000"));
        imf.setCode("CAMCCUL");
        imf.setNom("Caisse Mutuelle du Cameroun");
        imf.setActif(true);
        return imf;
    }

    // ── Fixtures User par rôle ────────────────────────────────────────────────

    public static User userWithRole(Role role) {
        User u = new User();
        u.setId(10L + role.ordinal());
        u.setUid(UUID.randomUUID());
        u.setUsername(role.name().toLowerCase() + "_test");
        u.setPasswordHash("$2a$10$xxxx");
        u.setRole(role);
        u.setActif(true);
        u.setImf(mockImf());
        return u;
    }

    public static User mockAgent()     { return userWithRole(Role.AGENT); }
    public static User mockDsi()       { return userWithRole(Role.DSI); }
    public static User mockDirecteur() { return userWithRole(Role.DIRECTEUR); }
    public static User mockAnalyste()  { return userWithRole(Role.ANALYSTE); }
    public static User mockRr()        { return userWithRole(Role.RESPONSABLE_RECOUVREMENT); }
    public static User mockSuperAdmin() {
        User u = userWithRole(Role.SUPER_ADMIN);
        u.setImf(null); // SUPER_ADMIN n'a pas de tenant
        return u;
    }

    // ── RequestPostProcessors ─────────────────────────────────────────────────

    private static RequestPostProcessor asUser(User user) {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()));
    }

    public static RequestPostProcessor asAgent()     { return asUser(mockAgent()); }
    public static RequestPostProcessor asDsi()       { return asUser(mockDsi()); }
    public static RequestPostProcessor asDirecteur() { return asUser(mockDirecteur()); }
    public static RequestPostProcessor asAnalyste()  { return asUser(mockAnalyste()); }
    public static RequestPostProcessor asRr()        { return asUser(mockRr()); }
    public static RequestPostProcessor asSuperAdmin(){ return asUser(mockSuperAdmin()); }
}
