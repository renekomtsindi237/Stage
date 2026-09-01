package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateAgenceRequest;
import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.request.UpdateUserRequest;
import cm.imf.pipeline.dto.response.AgenceResponse;
import cm.imf.pipeline.dto.response.ImfResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.Agence;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.DuplicateResourceException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.util.ImageFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service d'administration IMF — réservé DSI.
 * Toutes les opérations sont strictement isolées par imf_id du DSI connecté.
 * Le DSI ne peut pas créer de comptes SUPER_ADMIN ou DSI (rôles plateforme).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService implements IAdminService {

    /** Rôles que le DSI peut assigner — jamais DSI ni SUPER_ADMIN. */
    private static final Set<Role>    ROLES_DSI_ALLOWED = Set.of(
            Role.DIRECTEUR, Role.RESPONSABLE_RECOUVREMENT, Role.ANALYSTE, Role.AGENT,
            Role.AGENT_CREDIT, Role.CHEF_AGENCE, Role.ANALYSTE_ENGAGEMENTS,
            Role.AGENT_SAISIE, Role.CAISSIER);
    private static final String       LOGO_SUB           = "imf-logos";

    private final UserRepository   userRepository;
    private final AgenceRepository agenceRepository;
    private final ImfRepository    imfRepository;
    private final IUserService     userService;
    private final PasswordEncoder  passwordEncoder;
    private final EmailService     emailService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Value("${app.upload.dir:/uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size-mb:5}")
    private int maxSizeMb;

    // ── IMF du DSI ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ImfResponse getImfInfo() {
        Imf imf = requireCurrentImf();
        boolean hasDsi = userRepository.existsByImfIdAndRole(imf.getId(), Role.DSI);
        return ImfResponse.of(imf, hasDsi);
    }

    // ── Utilisateurs ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(int page, int size) {
        Long imfId = TenantContext.currentImfId();
        var pageable = PageRequest.of(page, size, Sort.by("username"));
        return userRepository.findByImfIdAndRoleNotAndSupprimeFalse(imfId, Role.DSI, pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID uid) {
        Long imfId = TenantContext.currentImfId();
        User user = userRepository.findByUidAndImfId(uid, imfId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", uid));
        if (user.getRole() == Role.DSI) {
            throw new ResourceNotFoundException("Utilisateur", uid);
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // ── Vérification du rôle assigné ────────────────────────────────────
        if (!ROLES_DSI_ALLOWED.contains(request.role())) {
            throw new BusinessException(
                "Le rôle '" + request.role().name() + "' ne peut pas être assigné par un DSI. " +
                "Rôles autorisés : DIRECTEUR, RESPONSABLE_RECOUVREMENT, ANALYSTE, AGENT.",
                HttpStatus.FORBIDDEN
            );
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Utilisateur", "username", request.username());
        }

        Imf imf = requireCurrentImf();

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(request.password() != null && !request.password().isBlank()
                        ? passwordEncoder.encode(request.password())
                        : passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .role(request.role())
                .zoneId(request.zoneId())
                .imf(imf)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .actif(true)
                .mustChangePassword(false)
                .build();

        User saved = userRepository.save(user);
        log.info("Utilisateur créé : {} [{}] — IMF : {}", saved.getUsername(), saved.getRole(), imf.getCode());

        // Email d'accueil avec instructions de connexion (OTP)
        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            emailService.sendNouvelUtilisateurEmail(
                    saved.getEmail(), saved.getUsername(),
                    saved.getRole().name(), imf.getNom());
        }

        return UserResponse.from(saved);
    }

    @Transactional
    public void deleteUser(UUID uid) {
        User user = findInCurrentImf(uid);
        User currentUser = TenantContext.currentUser();
        if (currentUser != null && currentUser.getUsername().equals(user.getUsername())) {
            throw new BusinessException("Le DSI ne peut pas supprimer son propre compte.", HttpStatus.FORBIDDEN);
        }
        // Soft-delete : anonymisation plutôt que suppression physique.
        // Les nombreuses FK (audit_trail avec RULE NO UPDATE, collectes, recouvrement...)
        // rendent la suppression physique impossible sans casser l'historique métier.
        String anon = "s_" + uid;
        user.setActif(false);
        user.setSupprime(true);
        user.setEmail("supprime_" + uid + "@deleted.imf");
        user.setUsername(anon);
        user.setAvatarUrl(null);
        user.setFcmToken(null);
        user.setZoneId(null);
        user.setLatitude(null);
        user.setLongitude(null);
        userRepository.save(user);
        log.info("Utilisateur anonymisé (soft-delete) : {} [{}]", uid, user.getRole());
    }

    @Transactional
    public UserResponse updateUser(UUID uid, UpdateUserRequest request) {
        User user = findInCurrentImf(uid);
        if (request.role() != null && !ROLES_DSI_ALLOWED.contains(request.role())) {
            throw new BusinessException(
                "Le rôle '" + request.role().name() + "' ne peut pas être assigné par un DSI.",
                HttpStatus.FORBIDDEN);
        }
        if (request.email() != null && !request.email().isBlank()) user.setEmail(request.email());
        if (request.role() != null) user.setRole(request.role());
        if (request.zoneId() != null) user.setZoneId(request.zoneId());
        log.info("Utilisateur modifié : {} → email={}, role={}", user.getUsername(), request.email(), request.role());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse deactivate(UUID uid) {
        User user = findInCurrentImf(uid);
        user.setActif(false);
        log.info("Utilisateur désactivé : {}", user.getUsername());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse activate(UUID uid) {
        User user = findInCurrentImf(uid);
        user.setActif(true);
        log.info("Utilisateur réactivé : {}", user.getUsername());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(UUID uid, String newPassword) {
        User user = findInCurrentImf(uid);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        log.info("Mot de passe réinitialisé pour : {}", user.getUsername());
    }

    // ── Agences ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgenceResponse> listAgences() {
        Long imfId = TenantContext.currentImfId();
        return jdbc.query("""
                SELECT
                    a.uid::text                                          AS uid,
                    a.nom, a.ville, a.responsable, a.telephone,
                    a.actif, a.created_at,
                    COALESCE((
                        SELECT COUNT(*)
                        FROM app.utilisateurs u
                        WHERE u.imf_id = a.imf_id
                          AND u.role   = 'AGENT'
                          AND u.actif  = TRUE
                    ), 0)                                               AS agents_count,
                    COALESCE((
                        SELECT COUNT(*)
                        FROM app.clients_informels ci
                        WHERE ci.agence_id = a.id
                    ), 0)                                               AS clients_count,
                    COALESCE((
                        SELECT SUM(c.montant_impaye)
                        FROM app.creances c
                        WHERE c.agence_id = a.id
                          AND c.statut    = 'ACTIVE'
                    ), 0)                                               AS encours_fcfa,
                    COALESCE((
                        SELECT ROUND(
                            100.0 * SUM(c.montant_impaye)
                                FILTER (WHERE c.jours_retard > 30)
                            / NULLIF(SUM(c.montant_impaye), 0),
                            1
                        )
                        FROM app.creances c
                        WHERE c.agence_id = a.id
                          AND c.statut    = 'ACTIVE'
                    ), 0)                                               AS par30
                FROM app.agences a
                WHERE a.imf_id = ?
                ORDER BY a.nom
                """,
                (rs, i) -> new AgenceResponse(
                        rs.getString("uid"),
                        rs.getString("nom"),
                        rs.getString("ville"),
                        rs.getString("responsable"),
                        rs.getString("telephone"),
                        rs.getBoolean("actif"),
                        rs.getObject("created_at",
                                java.time.OffsetDateTime.class),
                        rs.getLong("agents_count"),
                        rs.getLong("clients_count"),
                        rs.getLong("encours_fcfa"),
                        rs.getDouble("par30")),
                imfId);
    }

    @Transactional(readOnly = true)
    public List<String> listAgenceNoms() {
        Long imfId = TenantContext.currentImfId();
        return agenceRepository.findByImfIdOrderByNomAsc(imfId)
                .stream()
                .filter(Agence::isActif)
                .map(Agence::getNom)
                .toList();
    }

    @Transactional
    public AgenceResponse createAgence(CreateAgenceRequest request) {
        Imf imf = requireCurrentImf();
        if (agenceRepository.existsByImfIdAndNomIgnoreCase(imf.getId(), request.nom())) {
            throw new DuplicateResourceException("Agence", "nom", request.nom());
        }
        Agence agence = Agence.builder()
                .imf(imf)
                .nom(request.nom().strip())
                .ville(request.ville())
                .responsable(request.responsable())
                .telephone(request.telephone())
                .actif(true)
                .build();
        Agence saved = agenceRepository.save(agence);
        log.info("Agence créée : {} — IMF : {}", saved.getNom(), imf.getCode());
        return AgenceResponse.from(saved);
    }

    @Transactional
    public AgenceResponse toggleAgence(UUID uid) {
        Long imfId = TenantContext.currentImfId();
        Agence agence = agenceRepository.findByUidAndImfId(uid, imfId)
                .orElseThrow(() -> new ResourceNotFoundException("Agence", uid));
        agence.setActif(!agence.isActif());
        log.info("Agence {} : actif → {}", agence.getNom(), agence.isActif());
        return AgenceResponse.from(agenceRepository.save(agence));
    }

    @Transactional
    public void deleteAgence(UUID uid) {
        Long imfId = TenantContext.currentImfId();
        Agence agence = agenceRepository.findByUidAndImfId(uid, imfId)
                .orElseThrow(() -> new ResourceNotFoundException("Agence", uid));
        if (userRepository.existsByImfIdAndZoneId(imfId, agence.getNom())) {
            throw new BusinessException(
                "Impossible de supprimer l'agence '" + agence.getNom() + "' : des utilisateurs lui sont affectés.",
                HttpStatus.CONFLICT
            );
        }
        agenceRepository.delete(agence);
        log.info("Agence supprimée : {} — IMF {}", agence.getNom(), imfId);
    }

    // ── Avatar utilisateur (action DSI) ──────────────────────────────────────

    @Transactional
    public UserResponse uploadUserAvatar(UUID targetUid, MultipartFile file) {
        User target = findInCurrentImf(targetUid);
        return userService.uploadAvatarForUser(target.getId(), file);
    }

    @Transactional
    public UserResponse removeUserAvatar(UUID targetUid) {
        User target = findInCurrentImf(targetUid);
        return userService.removeAvatarForUser(target.getId());
    }

    // ── Logo IMF (action DSI) ─────────────────────────────────────────────────

    @Transactional
    public ImfResponse uploadImfLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Fichier manquant", HttpStatus.BAD_REQUEST);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Impossible de lire le fichier", HttpStatus.BAD_REQUEST);
        }
        String contentType = ImageFiles.resolveContentType(file, bytes);
        if (contentType == null) {
            throw new BusinessException(
                    "Type de fichier non supporté (JPEG, PNG, WEBP, GIF uniquement)",
                    HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > (long) maxSizeMb * 1024 * 1024) {
            throw new BusinessException("Fichier trop volumineux (max " + maxSizeMb + " Mo)",
                    HttpStatus.BAD_REQUEST);
        }

        String ext = ImageFiles.extension(contentType);
        String filename = UUID.randomUUID() + ext;
        Imf imf = requireCurrentImf();

        try {
            Path dir = Paths.get(uploadDir, LOGO_SUB);
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);
        } catch (IOException e) {
            log.error("Sauvegarde logo IMF impossible : {}", e.getMessage());
            throw new BusinessException("Impossible d'enregistrer le logo sur le serveur",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (imf.getLogoUrl() != null && imf.getLogoUrl().contains("/uploads/")) {
            int idx = imf.getLogoUrl().indexOf("/uploads/");
            String relative = imf.getLogoUrl().substring(idx + "/uploads/".length());
            try {
                Files.deleteIfExists(Paths.get(uploadDir, relative));
            } catch (IOException ignored) {}
        }
        imf.setLogoR2Key(null);
        String logoUrl = "/api/v1/uploads/" + LOGO_SUB + "/" + filename;
        imf.setLogoUrl(logoUrl);
        log.info("Logo IMF {} enregistré localement : {}", imf.getCode(), logoUrl);

        Imf saved = imfRepository.save(imf);
        boolean hasDsi = userRepository.existsByImfIdAndRole(saved.getId(), Role.DSI);
        return ImfResponse.of(saved, hasDsi);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findInCurrentImf(UUID userUid) {
        Long imfId = TenantContext.currentImfId();
        return userRepository.findByUidAndImfId(userUid, imfId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userUid));
    }

    private Imf requireCurrentImf() {
        Imf imf = TenantContext.currentImf();
        if (imf == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucune IMF associée à votre compte");
        }
        return imf;
    }
}
