package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ComiteDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComiteDecisionRepository extends JpaRepository<ComiteDecision, Long> {

    Optional<ComiteDecision> findByUid(UUID uid);

    List<ComiteDecision> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    Optional<ComiteDecision> findFirstByDossierIdAndDecisionIsNullOrderByCreatedAtDesc(Long dossierId);
}
