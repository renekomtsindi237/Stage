package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.EtiquetteDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtiquetteDossierRepository extends JpaRepository<EtiquetteDossier, Long> {

    Optional<EtiquetteDossier> findByUid(UUID uid);

    List<EtiquetteDossier> findByImfIdAndDossierRefAndActiveTrue(Long imfId, String dossierRef);

    List<EtiquetteDossier> findByImfIdAndCodeEtiquetteAndActiveTrue(Long imfId, String codeEtiquette);

    Optional<EtiquetteDossier> findByImfIdAndDossierRefAndCodeEtiquetteAndActiveTrue(
            Long imfId, String dossierRef, String codeEtiquette);

    boolean existsByImfIdAndDossierRefAndCodeEtiquetteAndActiveTrue(
            Long imfId, String dossierRef, String codeEtiquette);
}
