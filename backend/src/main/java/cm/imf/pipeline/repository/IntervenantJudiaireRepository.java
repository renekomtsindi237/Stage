package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.IntervenantJudiciaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntervenantJudiaireRepository extends JpaRepository<IntervenantJudiciaire, Long> {

    List<IntervenantJudiciaire> findByProcedureId(Long procedureId);
}
