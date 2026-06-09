package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ClientScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientScoreRepository extends JpaRepository<ClientScore, Long> {

    Optional<ClientScore> findByClientIdExterneAndImfId(String clientIdExterne, Long imfId);
}
