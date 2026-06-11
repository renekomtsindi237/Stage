package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ActionContentieux;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionContentieuxRepository extends JpaRepository<ActionContentieux, Long> {

    List<ActionContentieux> findByProcedureIdOrderByDateActionDesc(Long procedureId);
}
