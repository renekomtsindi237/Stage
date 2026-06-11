package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.VisiteConformite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisiteConformiteRepository extends JpaRepository<VisiteConformite, Long> {

    Optional<VisiteConformite> findByUid(UUID uid);

    List<VisiteConformite> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    boolean existsByDossierIdAndAgentCreditId(Long dossierId, Long agentCreditId);
}
