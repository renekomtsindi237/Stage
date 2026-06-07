package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Imf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImfRepository extends JpaRepository<Imf, Long> {

    Optional<Imf> findByUid(UUID uid);

    Optional<Imf> findByCode(String code);

    boolean existsByCode(String code);

    List<Imf> findByActifTrue();

    long countByActifTrue();

    @Query("SELECT COUNT(i) FROM Imf i WHERE YEAR(i.createdAt) = YEAR(CURRENT_TIMESTAMP) AND MONTH(i.createdAt) = MONTH(CURRENT_TIMESTAMP)")
    long countCreatedThisMonth();
}
