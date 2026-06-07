package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.ChangePasswordRequest;
import cm.imf.pipeline.dto.request.UpdatePreferencesRequest;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final String AVATAR_SUB = "avatars";

    private final UserRepository        userRepository;
    private final INotificationService  notificationService;
    private final PasswordEncoder       passwordEncoder;
    private final OnlineTrackingService onlineTracking;

    @Value("${app.upload.dir:/uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size-mb:2}")
    private int maxSizeMb;

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(int page, int size) {
        return userRepository.findAll(
                        PageRequest.of(page, size, Sort.by("username")))
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }

    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", username));
    }

    /**
     * Enregistre ou met à jour le token FCM d'un utilisateur connecté.
     */
    @Transactional
    public void updateFcmToken(User user, String token) {
        userRepository.updateFcmToken(user.getId(), token);
    }

    /**
     * Désactive un compte utilisateur.
     */
    @Transactional
    public UserResponse deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setActif(false);
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Récupère les utilisateurs d'une zone par rôle (pour ciblage FCM).
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listByZoneAndRole(String zoneId, Role role) {
        return userRepository.findByZoneIdAndRoleIn(zoneId, List.of(role))
                .stream().map(UserResponse::from).toList();
    }

    /**
     * Changement de mot de passe self-service.
     * Vérifie le mot de passe actuel avant d'appliquer le nouveau.
     */
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mot de passe actuel incorrect", HttpStatus.BAD_REQUEST);
        }
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", user.getId()));
        managed.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        managed.setMustChangePassword(false);
        userRepository.save(managed);
    }

    /**
     * Mise à jour partielle des préférences utilisateur.
     * Seuls les champs non-null sont appliqués (patch sémantique).
     */
    @Transactional
    public UserResponse updatePreferences(User user, UpdatePreferencesRequest req) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", user.getId()));
        if (req.prefTheme()            != null) managed.setPrefTheme(req.prefTheme());
        if (req.prefLangue()           != null) managed.setPrefLangue(req.prefLangue());
        if (req.notificationsActives() != null) managed.setNotificationsActives(req.notificationsActives());
        if (req.notifAlertes()         != null) managed.setNotifAlertes(req.notifAlertes());
        if (req.notifCollectes()       != null) managed.setNotifCollectes(req.notifCollectes());
        if (req.notifSync()            != null) managed.setNotifSync(req.notifSync());
        if (req.notifPipeline()        != null) managed.setNotifPipeline(req.notifPipeline());
        if (req.elementsParPage()      != null) managed.setElementsParPage(req.elementsParPage());
        return UserResponse.from(userRepository.save(managed));
    }

    @Transactional
    public UserResponse uploadAvatar(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Fichier manquant", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Type de fichier non supporté (JPEG, PNG, WEBP, GIF uniquement)",
                    HttpStatus.BAD_REQUEST);
        }
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("Fichier trop volumineux (max " + maxSizeMb + " Mo)",
                    HttpStatus.BAD_REQUEST);
        }

        String ext = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default            -> ".gif";
        };
        String filename = UUID.randomUUID() + ext;

        try {
            Path dir = Paths.get(uploadDir, AVATAR_SUB);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Erreur lors de la sauvegarde du fichier", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", user.getId()));

        // Supprimer l'ancien fichier si c'est un upload local
        deleteOldAvatar(managed.getAvatarUrl());

        managed.setAvatarUrl("/api/uploads/" + AVATAR_SUB + "/" + filename);
        return UserResponse.from(userRepository.save(managed));
    }

    @Transactional
    public UserResponse removeAvatar(User user) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", user.getId()));
        deleteOldAvatar(managed.getAvatarUrl());
        managed.setAvatarUrl(null);
        return UserResponse.from(userRepository.save(managed));
    }

    @Transactional
    public UserResponse uploadAvatarForUser(Long targetId, MultipartFile file) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", targetId));
        // Réutiliser la logique existante en passant l'entité managed
        return uploadAvatar(target, file);
    }

    @Transactional
    public UserResponse removeAvatarForUser(Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", targetId));
        return removeAvatar(target);
    }

    public long countOnline() {
        return onlineTracking.countOnline();
    }

    public long countOnlineByImf(Long imfId) {
        return onlineTracking.countOnlineByImf(imfId);
    }

    private void deleteOldAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("/api/uploads/")) return;
        String relative = avatarUrl.substring("/api/uploads/".length());
        try {
            Files.deleteIfExists(Paths.get(uploadDir, relative));
        } catch (IOException ignored) {}
    }
}
