package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Optional<SyncLog> findBySyncId(String syncId);

    List<SyncLog> findByDeviceIdOrderBySyncStartedAtDesc(String deviceId);

    @Query("""
            SELECT s FROM SyncLog s
            WHERE s.agent.id = :agentId
            ORDER BY s.syncStartedAt DESC
            """)
    List<SyncLog> findByAgentIdOrderByDateDesc(Long agentId);

    boolean existsBySyncId(String syncId);

    @Query("""
            SELECT COALESCE(SUM(s.nbSucces), 0)
            FROM SyncLog s
            WHERE s.deviceId = :deviceId
            """)
    int sumSuccesByDeviceId(String deviceId);

    @Query("""
            SELECT COALESCE(SUM(s.nbConflits), 0)
            FROM SyncLog s
            WHERE s.deviceId = :deviceId AND s.statutSync != 'COMPLETE'
            """)
    int sumConflitsOuvertsByDeviceId(String deviceId);
}
