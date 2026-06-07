package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.AccordReechelonnement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccordReechelonnementRepository extends JpaRepository<AccordReechelonnement, Long> {

    Optional<AccordReechelonnement> findByUid(UUID uid);

    List<AccordReechelonnement> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    Optional<AccordReechelonnement> findByDossierIdAndActifTrue(Long dossierId);
}
