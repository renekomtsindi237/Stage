package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.CollecteTerrain;
import cm.imf.pipeline.enums.StatutCollecte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollecteRepository extends JpaRepository<CollecteTerrain, Long> {

    Optional<CollecteTerrain> findByUid(UUID uid);

    Optional<CollecteTerrain> findByIdCollecteMobile(String idCollecteMobile);

    Page<CollecteTerrain> findByImfIdAndAgentId(Long imfId, Long agentId, Pageable pageable);

    Page<CollecteTerrain> findByAgentId(Long agentId, Pageable pageable);

    List<CollecteTerrain> findByAgentIdAndDateCollecteBetween(
            Long agentId, LocalDate debut, LocalDate fin);

    boolean existsByReferenceTransactionAndDateCollecte(
            String referenceTransaction, LocalDate dateCollecte);

    boolean existsByIdCollecteMobile(String idCollecteMobile);

    @Query("""
            SELECT c FROM CollecteTerrain c
            WHERE c.statut = :statut
            AND c.dateCollecte BETWEEN :debut AND :fin
            ORDER BY c.createdAt DESC
            """)
    List<CollecteTerrain> findByStatutAndDateRange(
            StatutCollecte statut, LocalDate debut, LocalDate fin);
}
