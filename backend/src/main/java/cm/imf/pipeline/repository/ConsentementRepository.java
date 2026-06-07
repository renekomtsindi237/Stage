package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Consentement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentementRepository extends JpaRepository<Consentement, Long> {

    Optional<Consentement> findByImfIdAndSujetTypeAndSujetIdAndFinalite(
            Long imfId, String sujetType, Long sujetId, String finalite);

    List<Consentement> findByImfIdAndSujetTypeAndSujetId(
            Long imfId, String sujetType, Long sujetId);

    List<Consentement> findByImfIdAndSujetTypeAndSujetIdAndAccordeTrue(
            Long imfId, String sujetType, Long sujetId);

    boolean existsByImfIdAndSujetTypeAndSujetIdAndFinaliteAndAccordeTrue(
            Long imfId, String sujetType, Long sujetId, String finalite);
}
