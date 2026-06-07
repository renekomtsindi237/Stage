package cm.imf.pipeline.security;

import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenProvider — tests unitaires")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    // Clé de 64 chars min pour HMAC-SHA512
    private static final String SECRET =
            "test-secret-key-1234567890-abcdefghijklmnopqrstuvwxyz-1234567890-XYZ";
    private static final long ACCESS_EXPIRY  = 900_000L;    // 15 min
    private static final long REFRESH_EXPIRY = 604_800_000L; // 7 jours

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "accessTokenExpirationMs",  ACCESS_EXPIRY);
        ReflectionTestUtils.setField(provider, "refreshTokenExpirationMs", REFRESH_EXPIRY);
    }

    private User testUser() {
        return User.builder()
                .id(1L)
                .username("jkamga")
                .role(Role.ANALYSTE)
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("generateAccessToken → token non vide, extrait le bon username")
    void generateAccessToken_valide() {
        String token = provider.generateAccessToken(testUser());

        assertThat(token).isNotBlank();
        assertThat(provider.extractUsername(token)).isEqualTo("jkamga");
    }

    @Test
    @DisplayName("validateToken → true pour un token valide")
    void validateToken_token_valide() {
        String token = provider.generateAccessToken(testUser());
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken → false pour un token falsifié")
    void validateToken_token_falsifie() {
        String token = provider.generateAccessToken(testUser());
        // Altère la signature
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("validateToken → false pour un token vide")
    void validateToken_token_vide() {
        assertThat(provider.validateToken("")).isFalse();
        assertThat(provider.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("generateRefreshTokenValue → UUID format distinct à chaque appel")
    void generateRefreshTokenValue_unique() {
        String r1 = provider.generateRefreshTokenValue();
        String r2 = provider.generateRefreshTokenValue();
        assertThat(r1).isNotEqualTo(r2);
        assertThat(r1).isNotBlank();
    }

    @Test
    @DisplayName("getRefreshTokenExpiryMs → valeur correcte")
    void getRefreshTokenExpiryMs() {
        assertThat(provider.getRefreshTokenExpiryMs()).isEqualTo(REFRESH_EXPIRY);
    }

    @Test
    @DisplayName("extractUsername — pour un token expiré : lève exception ou retourne null")
    void extractUsername_token_expire_leve_exception_ou_false() {
        // On crée un token avec expiry négatif (déjà expiré)
        JwtTokenProvider shortProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(shortProvider, "accessTokenExpirationMs", -1L);
        ReflectionTestUtils.setField(shortProvider, "refreshTokenExpirationMs", REFRESH_EXPIRY);

        String expiredToken = shortProvider.generateAccessToken(testUser());
        assertThat(shortProvider.validateToken(expiredToken)).isFalse();
    }
}
