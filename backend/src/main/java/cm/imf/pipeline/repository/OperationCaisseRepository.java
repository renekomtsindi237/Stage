package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.OperationCaisse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationCaisseRepository extends JpaRepository<OperationCaisse, Long> {

    Page<OperationCaisse> findByImfIdOrderByDateOperationDesc(Long imfId, Pageable pageable);

    Page<OperationCaisse> findByCaissierIdOrderByDateOperationDesc(Long caissierId, Pageable pageable);
}
