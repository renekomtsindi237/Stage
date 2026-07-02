package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.DossierCredit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierCreditRepository extends JpaRepository<DossierCredit, Long> {

    Optional<DossierCredit> findByUid(UUID uid);

    Page<DossierCredit> findByImfId(Long imfId, Pageable pageable);

    Page<DossierCredit> findByImfIdAndStatut(Long imfId, String statut, Pageable pageable);

    Page<DossierCredit> findByAgentCreditId(Long agentCreditId, Pageable pageable);

    Page<DossierCredit> findByAgentCreditIdAndStatut(Long agentCreditId, String statut, Pageable pageable);

    long countByImfIdAndStatut(Long imfId, String statut);

    List<DossierCredit> findByImfIdAndStatutOrderByDateSoumissionAsc(Long imfId, String statut);
}
