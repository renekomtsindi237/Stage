package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_clients", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiClient {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id", nullable = false)
    private Imf imf;

    /** Utilisateur système créé automatiquement pour ce client API (rôle API_CLIENT). */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "system_user_id")
    private User systemUser;

    /** Préfixe affiché dans l'UI pour identifier la clé sans l'exposer. Ex: mcr_live_a1b2c3d4 */
    @Column(name = "key_prefix", nullable = false, unique = true, length = 25)
    private String keyPrefix;

    /** SHA-256 hex de la clé brute complète. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** Clé brute chiffrée AES-256-GCM (base64). Déchiffrée uniquement après vérification du mot de passe. */
    @Column(name = "key_encrypted", columnDefinition = "text")
    private String keyEncrypted;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String scopes = "collectes:write,clients:read,creances:read,scores:read,alertes:read";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    public boolean isActive() {
        return "ACTIVE".equals(statut);
    }
}
