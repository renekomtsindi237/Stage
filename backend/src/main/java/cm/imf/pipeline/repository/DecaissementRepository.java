package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Decaissement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DecaissementRepository extends JpaRepository<Decaissement, Long> {

    Optional<Decaissement> findByUid(UUID uid);

    Optional<Decaissement> findByContratId(Long contratId);
}
