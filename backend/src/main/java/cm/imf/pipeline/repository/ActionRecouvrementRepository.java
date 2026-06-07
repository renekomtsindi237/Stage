package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ActionRecouvrement;
import cm.imf.pipeline.enums.TypeActionRecouvrement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActionRecouvrementRepository extends JpaRepository<ActionRecouvrement, Long> {

    Optional<ActionRecouvrement> findByUid(UUID uid);

    List<ActionRecouvrement> findByDossierIdOrderByDateActionDesc(Long dossierId);

    long countByDossierId(Long dossierId);

    boolean existsByDossierIdAndTypeAction(Long dossierId, TypeActionRecouvrement typeAction);
}
