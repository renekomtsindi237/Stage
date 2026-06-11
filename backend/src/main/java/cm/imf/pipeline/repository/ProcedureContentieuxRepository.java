package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ProcedureContentieux;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureContentieuxRepository extends JpaRepository<ProcedureContentieux, Long> {

    Optional<ProcedureContentieux> findByUid(UUID uid);

    List<ProcedureContentieux> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    Page<ProcedureContentieux> findByResponsableIdAndStatutNot(Long responsableId, String statut, Pageable pageable);
}
