package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateApiClientRequest;
import cm.imf.pipeline.dto.response.ApiClientCreatedResponse;
import cm.imf.pipeline.dto.response.ApiClientResponse;
import cm.imf.pipeline.dto.response.ApiKeyRevealedResponse;
import cm.imf.pipeline.entity.ApiClient;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.filter.ApiKeyAuthenticationFilter;
import cm.imf.pipeline.repository.ApiClientRepository;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.ApiKeyEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClientService {

    private final ApiClientRepository    apiClientRepository;
    private final UserRepository         userRepository;
    private final ImfRepository          imfRepository;
    private final PasswordEncoder        passwordEncoder;
    private final ApiKeyEncryptionService encryptionService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Crée un nouveau client API avec sa clé.
     * Génère également un User système (rôle API_CLIENT) associé à l'IMF.
     * La clé brute est retournée UNE SEULE FOIS — jamais ré-affichée.
     */
    @Transactional
    public ApiClientCreatedResponse create(CreateApiClientRequest req, User createdBy) {

        Imf imf = resolveImf(req, createdBy);

        // Générer la clé : mcr_live_<64 hex chars>
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String suffix  = HexFormat.of().formatHex(randomBytes); // 64 hex chars
        String rawKey  = "mcr_live_" + suffix;                  // 73 chars total
        String prefix  = rawKey.substring(0, ApiKeyAuthenticationFilter.PREFIX_LENGTH); // 17 chars
        String keyHash      = ApiKeyAuthenticationFilter.sha256hex(rawKey);
        String keyEncrypted = encryptionService.encrypt(rawKey);

        // Créer le User système invisible (ne peut pas se connecter via login normal)
        String systemUsername = "api_" + imf.getCode().toLowerCase() + "_" +
                                suffix.substring(0, 8);
        String systemEmail    = systemUsername + "@api.microrecouv.internal";

        User systemUser = User.builder()
                .username(systemUsername)
                .email(systemEmail)
                .passwordHash(passwordEncoder.encode(rawKey)) // mot de passe inutilisable en pratique
                .role(Role.API_CLIENT)
                .imf(imf)
                .actif(true)
                .mustChangePassword(false)
                .build();
        systemUser = userRepository.save(systemUser);

        // Créer l'enregistrement ApiClient
        ApiClient apiClient = ApiClient.builder()
                .name(req.name())
                .description(req.description())
                .imf(imf)
                .systemUser(systemUser)
                .keyPrefix(prefix)
                .keyHash(keyHash)
                .keyEncrypted(keyEncrypted)
                .statut("ACTIVE")
                .createdBy(createdBy)
                .createdAt(OffsetDateTime.now())
                .build();
        apiClient = apiClientRepository.save(apiClient);

        log.info("Clé API créée : {} pour IMF {} par {}", prefix, imf.getCode(), createdBy.getUsername());

        return new ApiClientCreatedResponse(
                apiClient.getId(),
                apiClient.getName(),
                apiClient.getDescription(),
                imf.getNom(),
                imf.getCode(),
                rawKey,           // clé brute — affichée une seule fois
                prefix,
                apiClient.getScopes(),
                apiClient.getStatut(),
                apiClient.getCreatedAt()
        );
    }

    /** Liste tous les clients API d'une IMF. */
    public List<ApiClientResponse> list(Long imfId) {
        return apiClientRepository.findByImf_IdOrderByCreatedAtDesc(imfId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Révoque une clé API (ne supprime pas, pour garder l'historique). */
    @Transactional
    public ApiClientResponse revoke(UUID id, User revokedBy) {
        ApiClient client = apiClientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client API introuvable : " + id));

        if (!client.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette clé est déjà révoquée.");
        }

        client.setStatut("REVOKED");
        client.setRevokedAt(OffsetDateTime.now());
        client.setRevokedBy(revokedBy);

        // Désactiver aussi le User système
        if (client.getSystemUser() != null) {
            client.getSystemUser().setActif(false);
            userRepository.save(client.getSystemUser());
        }

        log.info("Clé API révoquée : {} par {}", client.getKeyPrefix(), revokedBy.getUsername());
        return toResponse(apiClientRepository.save(client));
    }

    /**
     * Révèle la clé brute après vérification du mot de passe du demandeur.
     * Déchiffre via AES-256-GCM — nécessite que la clé ait été créée après V54.
     */
    public ApiKeyRevealedResponse reveal(UUID id, String password, User requester) {
        ApiClient client = apiClientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client API introuvable : " + id));

        // Vérifier le mot de passe du demandeur
        if (!passwordEncoder.matches(password, requester.getPassword())) {
            log.warn("Tentative de révélation de clé API avec mot de passe incorrect — user: {}, clé: {}",
                    requester.getUsername(), client.getKeyPrefix());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Mot de passe incorrect.");
        }

        if (client.getKeyEncrypted() == null) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Cette clé a été créée avant la fonctionnalité de révélation et ne peut pas être récupérée.");
        }

        String rawKey = encryptionService.decrypt(client.getKeyEncrypted());
        log.info("Clé API révélée : {} par {}", client.getKeyPrefix(), requester.getUsername());

        return new ApiKeyRevealedResponse(
                client.getId(),
                client.getName(),
                client.getKeyPrefix(),
                rawKey
        );
    }

    private Imf resolveImf(CreateApiClientRequest req, User createdBy) {
        // SUPPORT utilise son propre IMF
        if (createdBy.getRole() == Role.SUPPORT || createdBy.getRole() == Role.DSI) {
            if (createdBy.getImf() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Votre compte n'est associé à aucune IMF.");
            }
            return createdBy.getImf();
        }

        // SUPER_ADMIN peut choisir l'IMF
        if (req.imfUid() == null || req.imfUid().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "imfUid obligatoire pour SUPER_ADMIN.");
        }
        return imfRepository.findByUid(UUID.fromString(req.imfUid()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "IMF introuvable : " + req.imfUid()));
    }

    private ApiClientResponse toResponse(ApiClient c) {
        return new ApiClientResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getImf() != null ? c.getImf().getNom() : null,
                c.getImf() != null ? c.getImf().getCode() : null,
                c.getKeyPrefix(),
                c.getScopes(),
                c.getStatut(),
                c.getCreatedAt(),
                c.getLastUsedAt(),
                c.getRevokedAt()
        );
    }
}
