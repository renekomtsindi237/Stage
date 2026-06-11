package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.GarantieCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GarantieCreditRepository extends JpaRepository<GarantieCredit, Long> {

    Optional<GarantieCredit> findByUid(UUID uid);

    List<GarantieCredit> findByDossierId(Long dossierId);
}
