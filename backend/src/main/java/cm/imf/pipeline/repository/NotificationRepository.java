package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByUidAndImfId(UUID uid, Long imfId);

    @Query("SELECT n FROM Notification n " +
           "WHERE n.imfId = :imfId " +
           "AND (n.targetRole IS NULL OR n.targetRole = :role) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findForRole(@Param("imfId") Long imfId,
                                   @Param("role") String role,
                                   Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.imfId = :imfId " +
           "AND n.lu = false " +
           "AND (n.targetRole IS NULL OR n.targetRole = :role)")
    long countUnreadForRole(@Param("imfId") Long imfId, @Param("role") String role);

    @Modifying
    @Query("UPDATE Notification n SET n.lu = true " +
           "WHERE n.imfId = :imfId " +
           "AND (n.targetRole IS NULL OR n.targetRole = :role)")
    void markAllReadForRole(@Param("imfId") Long imfId, @Param("role") String role);
}
