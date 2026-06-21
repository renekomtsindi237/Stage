package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.ClientInformel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientInformelRepository extends JpaRepository<ClientInformel, Long> {

    Optional<ClientInformel> findByImfIdAndClientIdExterne(Long imfId, String clientIdExterne);

    boolean existsByImfIdAndClientIdExterne(Long imfId, String clientIdExterne);

    long countByImfId(Long imfId);
}
