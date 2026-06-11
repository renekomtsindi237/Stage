package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.VoteComite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoteComiteRepository extends JpaRepository<VoteComite, Long> {

    List<VoteComite> findByComiteId(Long comiteId);

    Optional<VoteComite> findByComiteIdAndVotantId(Long comiteId, Long votantId);

    long countByComiteId(Long comiteId);
}
