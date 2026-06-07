package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.DuplicateResourceException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService — tests unitaires")
class AdminServiceTest {

    @Mock UserRepository   userRepository;
    @Mock AgenceRepository agenceRepository;
    @Mock ImfRepository    imfRepository;
    @Mock IUserService     userService;
    @Mock PasswordEncoder  passwordEncoder;
    @InjectMocks AdminService adminService;

    private static final UUID USER_UID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private Imf  mockImf;
    private User existingUser;

    @BeforeEach
    void setUp() {
        mockImf = new Imf();
        mockImf.setId(1L);
        mockImf.setCode("CAMCCUL");
        mockImf.setNom("Caisse Mutuelle");
        mockImf.setActif(true);

        existingUser = User.builder()
                .id(1L).username("agent01").role(Role.AGENT).actif(true).imf(mockImf).build();

        // TenantContext lit le SecurityContextHolder — injecter un DSI avec IMF
        User dsiUser = User.builder()
                .id(99L).username("dsi_test").role(Role.DSI).actif(true).imf(mockImf).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(dsiUser, null, dsiUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createUser — nouvel utilisateur → sauvegardé avec hash BCrypt")
    void createUser_nouveau_utilisateur() {
        CreateUserRequest req = new CreateUserRequest(
                "newagent", "SecurePass123!", null, Role.AGENT, "YD001", null, null, null);
        when(userRepository.existsByUsername("newagent")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = adminService.createUser(req);

        assertThat(result.username()).isEqualTo("newagent");
        assertThat(result.role()).isEqualTo(Role.AGENT);
        verify(passwordEncoder).encode("SecurePass123!");
        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("$2a$12$hashed")));
    }

    @Test
    @DisplayName("createUser — username déjà existant → DuplicateResourceException")
    void createUser_username_existant_leve_exception() {
        when(userRepository.existsByUsername("newagent")).thenReturn(true);

        assertThatThrownBy(() ->
                adminService.createUser(new CreateUserRequest(
                        "newagent", "pass", null, Role.AGENT, null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("deactivate — utilisateur existant → actif=false")
    void deactivate_utilisateur_existant() {
        when(userRepository.findByUidAndImfId(USER_UID, 1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = adminService.deactivate(USER_UID);

        assertThat(result.actif()).isFalse();
    }

    @Test
    @DisplayName("activate — utilisateur désactivé → actif=true")
    void activate_utilisateur_desactive() {
        existingUser.setActif(false);
        when(userRepository.findByUidAndImfId(USER_UID, 1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = adminService.activate(USER_UID);

        assertThat(result.actif()).isTrue();
    }

    @Test
    @DisplayName("deactivate — UID inconnu → ResourceNotFoundException")
    void deactivate_id_inconnu_leve_exception() {
        UUID unknownUid = UUID.fromString("ffffffff-0000-0000-0000-000000000099");
        when(userRepository.findByUidAndImfId(eq(unknownUid), eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deactivate(unknownUid))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("resetPassword — hache le nouveau mot de passe")
    void resetPassword_encode_le_mot_de_passe() {
        when(userRepository.findByUidAndImfId(USER_UID, 1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("NewSecure!99")).thenReturn("$2a$12$newhash");
        when(userRepository.save(any())).thenReturn(existingUser);

        adminService.resetPassword(USER_UID, "NewSecure!99");

        verify(userRepository).save(argThat(u -> "$2a$12$newhash".equals(u.getPasswordHash())));
    }
}
