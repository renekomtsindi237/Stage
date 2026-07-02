package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    Optional<ApiClient> findByKeyPrefix(String keyPrefix);

    List<ApiClient> findByImf_IdOrderByCreatedAtDesc(Long imfId);

    @Modifying
    @Query("UPDATE ApiClient a SET a.lastUsedAt = :now WHERE a.id = :id")
    void updateLastUsedAt(UUID id, OffsetDateTime now);
}
