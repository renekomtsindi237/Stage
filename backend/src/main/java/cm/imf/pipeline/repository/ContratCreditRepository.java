package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ContratCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContratCreditRepository extends JpaRepository<ContratCredit, Long> {

    Optional<ContratCredit> findByUid(UUID uid);

    Optional<ContratCredit> findByDossierId(Long dossierId);
}
