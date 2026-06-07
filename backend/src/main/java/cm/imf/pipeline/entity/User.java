package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "utilisateurs", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Coordonnées GPS de l'utilisateur (agents terrain principalement). */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Column(name = "zone_id", length = 100)
    private String zoneId;

    @Column(name = "email", length = 150)
    private String email;

    /** Tenant auquel appartient cet utilisateur. NULL uniquement pour SUPER_ADMIN. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id")
    private Imf imf;

    /** Langue préférée pour les notifications et l'interface (fr / en). */
    @Column(name = "pref_langue", length = 5)
    @Builder.Default
    private String prefLangue = "fr";

    /** Thème visuel préféré : light, dark ou auto (suit le système). */
    @Column(name = "pref_theme", length = 10)
    @Builder.Default
    private String prefTheme = "auto";

    /** Maître-switch : désactiver toutes les notifications SSE et FCM. */
    @Column(name = "notifications_actives", nullable = false)
    @Builder.Default
    private boolean notificationsActives = true;

    /** Recevoir les notifications ALERTE_CREATED / ALERTE_UPDATED. */
    @Column(name = "notif_alertes", nullable = false)
    @Builder.Default
    private boolean notifAlertes = true;

    /** Recevoir les notifications COLLECTE_CONFIRMED. */
    @Column(name = "notif_collectes", nullable = false)
    @Builder.Default
    private boolean notifCollectes = false;

    /** Recevoir les notifications SYNC_COMPLETED. */
    @Column(name = "notif_sync", nullable = false)
    @Builder.Default
    private boolean notifSync = false;

    /** Recevoir les notifications PIPELINE_STATUS (technique — DSI uniquement). */
    @Column(name = "notif_pipeline", nullable = false)
    @Builder.Default
    private boolean notifPipeline = false;

    /** Nombre d'éléments par page dans les listes paginées (10 / 20 / 50). */
    @Column(name = "elements_par_page")
    @Builder.Default
    private Integer elementsParPage = 20;

    /** Forcer le changement de mot de passe à la première connexion. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── UserDetails ──────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return actif; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return actif; }
}
