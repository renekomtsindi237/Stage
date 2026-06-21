package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Agence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgenceRepository extends JpaRepository<Agence, Long> {

    Optional<Agence> findByUid(UUID uid);

    Optional<Agence> findByUidAndImfId(UUID uid, Long imfId);

    List<Agence> findByImfIdOrderByNomAsc(Long imfId);

    Optional<Agence> findByIdAndImfId(Long id, Long imfId);

    Optional<Agence> findByImfIdAndNomIgnoreCase(Long imfId, String nom);

    boolean existsByImfIdAndNomIgnoreCase(Long imfId, String nom);
}
