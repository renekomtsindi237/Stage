package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ActionRecouvrement;
import cm.imf.pipeline.enums.TypeActionRecouvrement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActionRecouvrementRepository extends JpaRepository<ActionRecouvrement, Long> {

    Optional<ActionRecouvrement> findByUid(UUID uid);

    List<ActionRecouvrement> findByDossierIdOrderByDateActionDesc(Long dossierId);

    long countByDossierId(Long dossierId);

    boolean existsByDossierIdAndTypeAction(Long dossierId, TypeActionRecouvrement typeAction);

    @Query("SELECT COUNT(a) FROM ActionRecouvrement a WHERE a.dossier.imfId = :imfId AND a.createdAt >= :debut AND a.createdAt < :fin")
    long countByImfIdAndPeriode(@Param("imfId") Long imfId, @Param("debut") OffsetDateTime debut, @Param("fin") OffsetDateTime fin);
}
