package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.TicketSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketSupportRepository extends JpaRepository<TicketSupport, Long> {

    Optional<TicketSupport> findByUid(UUID uid);

    /** Tickets d'une IMF — vue agent/DSI */
    Page<TicketSupport> findByImfIdOrderByCreatedAtDesc(Long imfId, Pageable pageable);

    /** Tous les tickets — vue SUPPORT cross-IMF */
    Page<TicketSupport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Filtrage par statut — vue SUPPORT */
    Page<TicketSupport> findByStatutOrderByCreatedAtDesc(String statut, Pageable pageable);

    /** Tickets ouverts par un auteur donné */
    Page<TicketSupport> findByAuteurIdOrderByCreatedAtDesc(Long auteurId, Pageable pageable);
}
