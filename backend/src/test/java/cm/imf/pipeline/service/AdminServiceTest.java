package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.DuplicateResourceException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService — tests unitaires")
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AdminService adminService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(1L).username("dsi01").role(Role.DSI).actif(true).build();
    }

    @Test
    @DisplayName("createUser — nouvel utilisateur → sauvegardé avec hash BCrypt")
    void createUser_nouveau_utilisateur() {
        CreateUserRequest req = new CreateUserRequest("newagent", "SecurePass123!", Role.AGENT, "YD001");
        when(userRepository.findByUsername("newagent")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        UserResponse result = adminService.createUser(req);

        assertThat(result.username()).isEqualTo("newagent");
        assertThat(result.role()).isEqualTo(Role.AGENT);
        verify(passwordEncoder).encode("SecurePass123!");
        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("$2a$12$hashed")));
    }

    @Test
    @DisplayName("createUser — username déjà existant → DuplicateResourceException")
    void createUser_username_existant_leve_exception() {
        when(userRepository.findByUsername("dsi01")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() ->
                adminService.createUser(new CreateUserRequest("dsi01", "pass", Role.DSI, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("deactivate — utilisateur existant → actif=false")
    void deactivate_utilisateur_existant() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = adminService.deactivate(1L);

        assertThat(result.actif()).isFalse();
    }

    @Test
    @DisplayName("activate — utilisateur désactivé → actif=true")
    void activate_utilisateur_desactive() {
        existingUser.setActif(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = adminService.activate(1L);

        assertThat(result.actif()).isTrue();
    }

    @Test
    @DisplayName("deactivate — ID inconnu → ResourceNotFoundException")
    void deactivate_id_inconnu_leve_exception() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("resetPassword — hache le nouveau mot de passe")
    void resetPassword_encode_le_mot_de_passe() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("NewSecure!99")).thenReturn("$2a$12$newhash");
        when(userRepository.save(any())).thenReturn(existingUser);

        adminService.resetPassword(1L, "NewSecure!99");

        verify(userRepository).save(argThat(u -> "$2a$12$newhash".equals(u.getPasswordHash())));
    }
}
