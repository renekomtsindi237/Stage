package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.AlerteImpaye;
import cm.imf.pipeline.enums.StatutAlerte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlerteRepository extends JpaRepository<AlerteImpaye, Long> {

    Optional<AlerteImpaye> findByUid(UUID uid);

    Optional<AlerteImpaye> findByUidAndImf_Id(UUID uid, Long imfId);

    // ── Requêtes scoped par IMF ───────────────────────────────────────────────

    Page<AlerteImpaye> findByImfIdAndStatutAlerte(Long imfId, StatutAlerte statut, Pageable pageable);

    Page<AlerteImpaye> findByImfId(Long imfId, Pageable pageable);

    long countByImfIdAndStatutAlerte(Long imfId, StatutAlerte statut);

    List<AlerteImpaye> findByImfIdAndStatutAlerteAndJoursRetardGreaterThanEqual(
            Long imfId, StatutAlerte statut, int joursMin);

    @Query("""
            SELECT a FROM AlerteImpaye a
            WHERE a.imf.id = :imfId AND a.statutAlerte = 'ACTIVE'
            ORDER BY a.joursRetard DESC
            """)
    List<AlerteImpaye> findActiveByImfIdOrderByJoursRetardDesc(Long imfId);

    // ── Legacy (sans filtre IMF) ──────────────────────────────────────────────

    Page<AlerteImpaye> findByStatutAlerte(StatutAlerte statut, Pageable pageable);

    Optional<AlerteImpaye> findByIdPretAndStatutAlerte(String idPret, StatutAlerte statut);

    long countByStatutAlerte(StatutAlerte statut);

    List<AlerteImpaye> findByStatutAlerteAndJoursRetardGreaterThanEqual(
            StatutAlerte statut, int joursMin);

    @Query("""
            SELECT a FROM AlerteImpaye a
            WHERE a.statutAlerte = 'ACTIVE'
            ORDER BY a.joursRetard DESC
            """)
    List<AlerteImpaye> findActiveOrderByJoursRetardDesc();
}
