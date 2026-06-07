package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.CollecteEpargne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollecteEpargneRepository extends JpaRepository<CollecteEpargne, Long> {

    boolean existsByUuidMobile(UUID uuidMobile);

    Optional<CollecteEpargne> findByUuidMobile(UUID uuidMobile);

    Optional<CollecteEpargne> findByUid(UUID uid);

    Optional<CollecteEpargne> findByUidAndImf_Id(UUID uid, Long imfId);

    List<CollecteEpargne> findByAgent_IdAndSyncedAtIsNullOrderByDateCollecteDesc(Long agentId);
}
