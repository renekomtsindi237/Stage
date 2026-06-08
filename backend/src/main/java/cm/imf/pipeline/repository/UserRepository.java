package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUid(UUID uid);

    Optional<User> findByUidAndImfId(UUID uid, Long imfId);

    /** Charge toujours l'IMF en même temps — évite LazyInitializationException sur login. */
    @EntityGraph(attributePaths = {"imf"})
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    // ── Requêtes scoped par IMF ───────────────────────────────────────────────

    Page<User> findByImfId(Long imfId, Pageable pageable);

    /** Liste paginée des utilisateurs d'une IMF en excluant un rôle donné (ex: DSI). */
    Page<User> findByImfIdAndRoleNot(Long imfId, Role role, Pageable pageable);

    Optional<User> findByIdAndImfId(Long id, Long imfId);

    List<User> findByImfIdAndZoneIdAndRoleIn(Long imfId, String zoneId, List<Role> roles);

    List<User> findByImfIdAndRoleAndFcmTokenIsNotNull(Long imfId, Role role);

    // ── Requêtes legacy (zone + role sans IMF — conservées pour compatibilité) ─

    List<User> findByZoneIdAndRoleIn(String zoneId, List<Role> roles);

    List<User> findByRoleAndFcmTokenIsNotNull(Role role);

    long countByRoleNot(cm.imf.pipeline.enums.Role role);

    long countByImfId(Long imfId);

    boolean existsByImfIdAndRole(Long imfId, Role role);

    boolean existsByImfIdAndZoneId(Long imfId, String zoneId);

    @Query("SELECT DISTINCT u.imf.id FROM User u WHERE u.role = :role AND u.imf IS NOT NULL")
    java.util.Set<Long> findImfIdsByRole(@org.springframework.data.repository.query.Param("role") Role role);

    @Modifying
    @Query("UPDATE User u SET u.fcmToken = :token WHERE u.id = :userId")
    void updateFcmToken(Long userId, String token);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :now WHERE u.id = :userId")
    void updateLastLogin(Long userId, OffsetDateTime now);
}
