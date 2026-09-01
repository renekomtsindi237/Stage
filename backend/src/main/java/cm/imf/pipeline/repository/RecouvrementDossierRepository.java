package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.RecouvrementDossier;
import cm.imf.pipeline.enums.RecouvrementPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecouvrementDossierRepository extends JpaRepository<RecouvrementDossier, Long> {

    Optional<RecouvrementDossier> findByUid(UUID uid);

    Optional<RecouvrementDossier> findByUidAndImfId(UUID uid, Long imfId);

    Page<RecouvrementDossier> findByImfId(Long imfId, Pageable pageable);

    Page<RecouvrementDossier> findByImfIdAndPhase(Long imfId, RecouvrementPhase phase, Pageable pageable);

    Page<RecouvrementDossier> findByImfIdAndClos(Long imfId, boolean clos, Pageable pageable);

    Page<RecouvrementDossier> findByImfIdAndPhaseAndClos(Long imfId, RecouvrementPhase phase, boolean clos, Pageable pageable);

    @Query("""
            SELECT d FROM RecouvrementDossier d
            WHERE d.imfId = :imfId
              AND (:phase IS NULL OR d.phase = :phase)
              AND (:clos IS NULL OR d.clos = :clos)
              AND (
                    LOWER(COALESCE(d.nomClient, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(d.idPret) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(d.telephoneCaution, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(d.nomCaution, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<RecouvrementDossier> search(
            @Param("imfId") Long imfId,
            @Param("phase") RecouvrementPhase phase,
            @Param("clos") Boolean clos,
            @Param("q") String q,
            Pageable pageable);

    @Query("SELECT d FROM RecouvrementDossier d WHERE d.imfId = :imfId AND d.idPret = :idPret AND d.clos = false")
    Optional<RecouvrementDossier> findDossierActif(@Param("imfId") Long imfId, @Param("idPret") String idPret);

    @Query("SELECT COUNT(d) > 0 FROM RecouvrementDossier d WHERE d.imfId = :imfId AND d.idPret = :idPret AND d.clos = false")
    boolean existsDossierActif(@Param("imfId") Long imfId, @Param("idPret") String idPret);

    long countByImfIdAndClos(Long imfId, boolean clos);

    long countByImfIdAndPhaseAndClos(Long imfId, RecouvrementPhase phase, boolean clos);

    @Query("SELECT d FROM RecouvrementDossier d WHERE d.imfId = :imfId AND d.clos = false ORDER BY d.joursRetard DESC")
    List<RecouvrementDossier> findActivesOrderByJoursRetardDesc(@Param("imfId") Long imfId);

    @Query("SELECT COALESCE(SUM(d.montantImpaye), 0) FROM RecouvrementDossier d WHERE d.imfId = :imfId AND d.clos = false")
    BigDecimal sumMontantImapyeActif(@Param("imfId") Long imfId);

    long countByImfId(Long imfId);
}
