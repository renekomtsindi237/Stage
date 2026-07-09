package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.Creance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CreanceRepository extends JpaRepository<Creance, Long> {

    Optional<Creance> findByUid(UUID uid);

    Optional<Creance> findByUidAndImf_Id(UUID uid, Long imfId);

    /** Résout clientIdExterne à partir de la référence prêt CBS — utilisé pour
     * déclencher le scoring temps réel à l'ouverture d'un dossier de recouvrement. */
    Optional<Creance> findByImf_IdAndIdPretExterne(Long imfId, String idPretExterne);

    @Query("""
            SELECT c FROM Creance c
            WHERE (:imfId IS NULL OR c.imf.id = :imfId)
              AND (:agenceId IS NULL OR c.agence.id = :agenceId)
              AND (:categoriePar IS NULL OR c.categoriePar = :categoriePar)
              AND (:statut IS NULL OR c.statut = :statut)
              AND (:dateDebut IS NULL OR c.dateOuvertureCreance >= :dateDebut)
              AND (:dateFin IS NULL OR c.dateOuvertureCreance <= :dateFin)
            ORDER BY c.createdAt DESC
            """)
    Page<Creance> findFiltered(
            @Param("imfId")       Long      imfId,
            @Param("agenceId")    Long      agenceId,
            @Param("categoriePar") String   categoriePar,
            @Param("statut")      String    statut,
            @Param("dateDebut")   LocalDate dateDebut,
            @Param("dateFin")     LocalDate dateFin,
            Pageable pageable
    );
}
