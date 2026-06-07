package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.EcheanceApp;
import cm.imf.pipeline.enums.StatutEcheance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EcheanceAppRepository extends JpaRepository<EcheanceApp, Long> {

    Optional<EcheanceApp> findByUid(UUID uid);

    Optional<EcheanceApp> findByUidAndImfId(UUID uid, Long imfId);

    /** Toutes les échéances d'un prêt, ordonnées par numéro d'échéance */
    List<EcheanceApp> findByIdPretOrderByNumEcheanceAsc(String idPret);

    /** Échéances d'un agent pour un statut donné */
    Page<EcheanceApp> findByAgentIdAndStatut(Long agentId, StatutEcheance statut, Pageable pageable);

    /** Toutes les échéances d'un statut donné, filtrées par IMF */
    Page<EcheanceApp> findByImfIdAndStatut(Long imfId, StatutEcheance statut, Pageable pageable);

    /** Compte par statut et IMF — utilisé par le dashboard */
    long countByImfIdAndStatut(Long imfId, StatutEcheance statut);

    /** Toutes les échéances d'un statut donné (paginées) */
    Page<EcheanceApp> findByStatut(StatutEcheance statut, Pageable pageable);

    /** Compte par statut — utilisé par le dashboard */
    long countByStatut(StatutEcheance statut);

    /** Vérifie l'unicité (id_pret, num_echeance) */
    boolean existsByIdPretAndNumEcheance(String idPret, int numEcheance);
}
