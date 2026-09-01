package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.ChangePasswordRequest;
import cm.imf.pipeline.dto.request.UpdatePreferencesRequest;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.util.ImageFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final UserRepository        userRepository;
    private final INotificationService  notificationService;
    private final PasswordEncoder       passwordEncoder;
    private final OnlineTrackingService onlineTracking;

    @Value("${app.upload.dir:/uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size-mb:2}")
    private int maxSizeMb;

    // ── Lecture ──────────────────────────────────────────────────────────────

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

    @Transactional
    public void updateFcmToken(User user, String token) {
        userRepository.updateFcmToken(user.getId(), token);
    }

    @Transactional
    public UserResponse deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setActif(false);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByZoneAndRole(String zoneId, Role role) {
        return userRepository.findByZoneIdAndRoleIn(zoneId, List.of(role))
                .stream().map(UserResponse::from).toList();
    }

    // ── Mot de passe ─────────────────────────────────────────────────────────

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

    // ── Préférences ──────────────────────────────────────────────────────────

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

    // ── Avatar ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse uploadAvatar(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Fichier manquant", HttpStatus.BAD_REQUEST);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Impossible de lire le fichier", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String contentType = ImageFiles.resolveContentType(file, bytes);
        if (contentType == null) {
            throw new BusinessException(
                    "Type de fichier non supporté (JPEG, PNG, WEBP, GIF uniquement)",
                    HttpStatus.BAD_REQUEST);
        }
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("Fichier trop volumineux (max " + maxSizeMb + " Mo)",
                    HttpStatus.BAD_REQUEST);
        }

        String ext = ImageFiles.extension(contentType);

        String avatarUrl = saveLocalAvatar(bytes, ext);
        log.info("Avatar enregistré sur disque : user={} url={}", user.getId(), avatarUrl);

        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", user.getId()));
        deleteOldAvatar(managed.getAvatarUrl());
        managed.setAvatarUrl(avatarUrl);
        return UserResponse.from(userRepository.save(managed));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> serveAvatar(User user) {
        try {
            if (user != null && user.getId() != null) {
                User managed = userRepository.findById(user.getId()).orElse(null);
                String url = managed != null ? managed.getAvatarUrl() : null;
                byte[] data = readAvatarBytes(url);
                if (data != null && data.length > 0) {
                    return pngOrDetected(data, url);
                }
            }
        } catch (Exception e) {
            log.warn("Lecture avatar impossible : {}", e.getMessage());
        }
        return defaultAvatarResponse();
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
        return uploadAvatar(target, file);
    }

    @Transactional
    public UserResponse removeAvatarForUser(Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", targetId));
        return removeAvatar(target);
    }

    // ── Métriques en ligne ───────────────────────────────────────────────────

    public long countOnline() {
        return onlineTracking.countOnline();
    }

    public long countOnlineByImf(Long imfId) {
        return onlineTracking.countOnlineByImf(imfId);
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private String saveLocalAvatar(byte[] bytes, String ext) {
        String filename = UUID.randomUUID() + ext;
        try {
            Path dir = Paths.get(uploadDir, "avatars");
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);
        } catch (IOException e) {
            log.error("Sauvegarde avatar locale impossible ({}): {}", uploadDir, e.getMessage());
            throw new BusinessException("Erreur lors de la sauvegarde du fichier",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return "/api/v1/uploads/avatars/" + filename;
    }

    private byte[] readAvatarBytes(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()
                || avatarUrl.equals(UserResponse.DEFAULT_AVATAR_URL)
                || avatarUrl.contains("/users/me/avatar")) {
            return null;
        }
        String localPrefix = localAvatarPrefix(avatarUrl);
        if (localPrefix == null) {
            return null;
        }
        String filename = avatarUrl.substring(localPrefix.length());
        if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return null;
        }
        Path path = Paths.get(uploadDir, "avatars", filename);
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String localAvatarPrefix(String avatarUrl) {
        if (avatarUrl.startsWith("/api/v1/uploads/avatars/")) {
            return "/api/v1/uploads/avatars/";
        }
        if (avatarUrl.startsWith("/api/uploads/avatars/")) {
            return "/api/uploads/avatars/";
        }
        return null;
    }

    private ResponseEntity<byte[]> pngOrDetected(byte[] data, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(detectAvatarType(url));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate());
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    private MediaType detectAvatarType(String url) {
        if (url == null) return MediaType.IMAGE_PNG;
        String u = url.toLowerCase();
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (u.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (u.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_PNG;
    }

    private ResponseEntity<byte[]> defaultAvatarResponse() {
        byte[] data;
        try {
            ClassPathResource res = new ClassPathResource("static/profile.png");
            data = res.getInputStream().readAllBytes();
        } catch (Exception e) {
            data = MINIMAL_PNG;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /** PNG 1×1 transparent — dernier recours si profile.png n'est pas dans le JAR. */
    private static final byte[] MINIMAL_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    /** Supprime l'ancien fichier local. Ignore les URL R2 héritées et l'image par défaut. */
    private void deleteOldAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.equals(UserResponse.DEFAULT_AVATAR_URL)) return;
        String localPrefix = localAvatarPrefix(avatarUrl);
        if (localPrefix == null) return;
        String filename = avatarUrl.substring(localPrefix.length());
        if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(uploadDir, "avatars", filename));
        } catch (IOException ignored) {}
    }
}
