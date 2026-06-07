package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.KycDocument;
import cm.imf.pipeline.enums.TypeDocumentKyc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    Optional<KycDocument> findByUid(UUID uid);

    List<KycDocument> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    @Query("SELECT d FROM KycDocument d WHERE d.dossier.id = :dossierId AND d.typeDocument = :type ORDER BY d.createdAt DESC")
    Optional<KycDocument> findLatestByDossierAndType(@Param("dossierId") Long dossierId, @Param("type") TypeDocumentKyc type);

    @Query("SELECT COUNT(d) FROM KycDocument d WHERE d.dossier.id = :dossierId AND d.valide = true")
    long countValidByDossierId(@Param("dossierId") Long dossierId);
}
