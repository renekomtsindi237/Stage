package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * Repository en lecture seule pour app.audit_trail.
 * L'immutabilité est garantie au niveau DB (rules PostgreSQL no UPDATE/DELETE).
 * Ce repository expose uniquement save() pour l'insertion et des méthodes de lecture.
 * Ne jamais appeler delete*() ou save() sur une entité avec un id existant.
 */
@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

    Page<AuditTrail> findByImfIdOrderByCreatedAtDesc(Long imfId, Pageable pageable);

    Page<AuditTrail> findByImfIdAndActeurUsernameOrderByCreatedAtDesc(
            Long imfId, String username, Pageable pageable);

    Page<AuditTrail> findByImfIdAndActionOrderByCreatedAtDesc(
            Long imfId, String action, Pageable pageable);

    Page<AuditTrail> findByImfIdAndEntiteTypeAndEntiteIdOrderByCreatedAtDesc(
            Long imfId, String entiteType, String entiteId, Pageable pageable);

    @Query("""
            SELECT a FROM AuditTrail a
            WHERE a.imfId = :imfId
              AND (:entiteType IS NULL OR a.entiteType = :entiteType)
              AND (:entiteId IS NULL OR a.entiteId = :entiteId)
              AND (:action IS NULL OR a.action = :action)
              AND (:username IS NULL OR a.acteurUsername = :username)
              AND (:debut IS NULL OR a.createdAt >= :debut)
              AND (:fin IS NULL OR a.createdAt <= :fin)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditTrail> rechercher(
            @Param("imfId")      Long imfId,
            @Param("entiteType") String entiteType,
            @Param("entiteId")   String entiteId,
            @Param("action")     String action,
            @Param("username")   String username,
            @Param("debut")      OffsetDateTime debut,
            @Param("fin")        OffsetDateTime fin,
            Pageable pageable);

    /** Nombre d'accès à une entité depuis une date donnée — détection d'accès suspects. */
    long countByImfIdAndEntiteTypeAndEntiteIdAndCreatedAtAfter(
            Long imfId, String entiteType, String entiteId, OffsetDateTime since);
}
