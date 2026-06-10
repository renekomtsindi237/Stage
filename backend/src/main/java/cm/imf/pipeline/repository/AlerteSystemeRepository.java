package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.AlerteSysteme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlerteSystemeRepository extends JpaRepository<AlerteSysteme, Long> {

    /** Alertes non encore résolues, triées par date décroissante. */
    List<AlerteSysteme> findByStatutNotOrderByCreatedAtDesc(String statut);

    /** Alertes critiques actives. */
    List<AlerteSysteme> findBySeveriteAndStatutNot(String severite, String statut);

    long countBySeveriteAndStatutNot(String severite, String statut);

    long countByStatut(String statut);
}
