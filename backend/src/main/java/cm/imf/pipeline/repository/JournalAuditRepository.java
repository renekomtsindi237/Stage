package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.JournalAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

    Page<JournalAudit> findByUsername(String username, Pageable pageable);

    Page<JournalAudit> findByAction(String action, Pageable pageable);

    Page<JournalAudit> findByImfId(Long imfId, Pageable pageable);

    Page<JournalAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<JournalAudit> findByUtilisateurIdAndCreatedAtAfter(Long utilisateurId, OffsetDateTime since);

    long countByActionAndCreatedAtAfter(String action, OffsetDateTime since);
}
