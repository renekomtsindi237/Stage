package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.KycVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {

    Optional<KycVerification> findByUid(UUID uid);

    List<KycVerification> findByDossierIdOrderByCreatedAtDesc(Long dossierId);
}
