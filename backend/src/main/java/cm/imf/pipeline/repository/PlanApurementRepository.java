package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.PlanApurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanApurementRepository extends JpaRepository<PlanApurement, Long> {

    Optional<PlanApurement> findByUid(UUID uid);

    List<PlanApurement> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    Optional<PlanApurement> findFirstByDossierIdAndStatut(Long dossierId, String statut);
}
