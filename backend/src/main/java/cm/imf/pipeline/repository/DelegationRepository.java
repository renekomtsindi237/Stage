package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Delegation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DelegationRepository extends JpaRepository<Delegation, Long> {

    Optional<Delegation> findByUid(UUID uid);

    Page<Delegation> findByImfId(Long imfId, Pageable pageable);

    List<Delegation> findByDelegantIdAndActif(Long delegantId, boolean actif);

    List<Delegation> findByDelegataireIdAndActif(Long delegataireId, boolean actif);

    /** Délégations d'autorité actives sur une IMF (pour résolution des droits étendus). */
    List<Delegation> findByImfIdAndTypeDelegationAndActif(Long imfId, String typeDelegation, boolean actif);

    /** Vérifie si un dossier a déjà été réassigné pour éviter les doublons actifs. */
    boolean existsByObjetIdAndTypeDelegationAndActif(Long objetId, String typeDelegation, boolean actif);
}
