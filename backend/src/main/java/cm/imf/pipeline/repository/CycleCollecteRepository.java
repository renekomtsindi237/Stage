package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.CycleCollecte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CycleCollecteRepository extends JpaRepository<CycleCollecte, Long> {
    Optional<CycleCollecte> findByUid(UUID uid);
}
