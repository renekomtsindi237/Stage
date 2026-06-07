package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.DemandeRgpd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DemandeRgpdRepository extends JpaRepository<DemandeRgpd, Long> {

    Optional<DemandeRgpd> findByUid(UUID uid);

    Page<DemandeRgpd> findByImfIdOrderByDateSoumissionDesc(Long imfId, Pageable pageable);

    Page<DemandeRgpd> findByDemandeurIdOrderByDateSoumissionDesc(Long demandeurId, Pageable pageable);

    Page<DemandeRgpd> findByImfIdAndStatutOrderByDateSoumissionDesc(
            Long imfId, String statut, Pageable pageable);

    /** Demandes en retard : délai de 30j dépassé (art. 41). */
    @Query("""
            SELECT d FROM DemandeRgpd d
            WHERE d.imfId = :imfId
              AND d.statut IN ('EN_ATTENTE', 'EN_COURS')
              AND d.dateLimiteReponse < :now
            ORDER BY d.dateLimiteReponse ASC
            """)
    List<DemandeRgpd> findEnRetard(@Param("imfId") Long imfId,
                                    @Param("now") OffsetDateTime now);

    long countByImfIdAndStatutIn(Long imfId, List<String> statuts);
}
