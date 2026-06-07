package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.KycDossier;
import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.NiveauRisque;
import cm.imf.pipeline.enums.StatutKyc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDossierRepository extends JpaRepository<KycDossier, Long> {

    Optional<KycDossier> findByUid(UUID uid);

    Optional<KycDossier> findByUidAndImf_Id(UUID uid, Long imfId);

    Optional<KycDossier> findByImfIdAndClientId(Long imfId, String clientId);

    @Query("SELECT d FROM KycDossier d WHERE d.imf.id = :imfId")
    Page<KycDossier> findByImfId(@Param("imfId") Long imfId, Pageable pageable);

    @Query("SELECT d FROM KycDossier d WHERE d.imf.id = :imfId AND d.statut = :statut")
    Page<KycDossier> findByImfIdAndStatut(@Param("imfId") Long imfId, @Param("statut") StatutKyc statut, Pageable pageable);

    @Query("SELECT d FROM KycDossier d WHERE d.imf.id = :imfId AND d.niveauActuel = :niveau")
    Page<KycDossier> findByImfIdAndNiveau(@Param("imfId") Long imfId, @Param("niveau") NiveauKyc niveau, Pageable pageable);

    @Query("SELECT d FROM KycDossier d WHERE d.imf.id = :imfId AND d.niveauRisque = :risque")
    Page<KycDossier> findByImfIdAndRisque(@Param("imfId") Long imfId, @Param("risque") NiveauRisque risque, Pageable pageable);

    @Query("SELECT d FROM KycDossier d WHERE d.imf.id = :imfId AND d.statut = :statut AND d.niveauActuel = :niveau")
    Page<KycDossier> findByImfIdAndStatutAndNiveau(
            @Param("imfId") Long imfId, @Param("statut") StatutKyc statut,
            @Param("niveau") NiveauKyc niveau, Pageable pageable);

    @Query("SELECT COUNT(d) FROM KycDossier d WHERE d.imf.id = :imfId AND d.statut = :statut")
    long countByImfIdAndStatut(@Param("imfId") Long imfId, @Param("statut") StatutKyc statut);

    @Query("SELECT COUNT(d) FROM KycDossier d WHERE d.imf.id = :imfId AND d.estPep = true")
    long countPepByImfId(@Param("imfId") Long imfId);

    // Dossiers dont le KYC expire dans les 30 jours — pour les alertes de renouvellement
    @Query("SELECT d FROM KycDossier d WHERE d.dateExpirationKyc IS NOT NULL AND d.dateExpirationKyc <= :dateLimite AND d.statut = 'APPROUVE'")
    List<KycDossier> findExpirantAvant(@Param("dateLimite") LocalDate dateLimite);
}
