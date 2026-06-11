package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "plans_apurement", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanApurement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id", nullable = false)
    private Long dossierId;

    @Column(name = "nb_echeances", nullable = false)
    private Integer nbEcheances;

    @Column(name = "montant_par_echeance", precision = 14, scale = 2)
    private BigDecimal montantParEcheance;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "signe_client", nullable = false)
    @Builder.Default
    private boolean signeClient = false;

    /**
     * ACTIF | RESPECTE | ROMPU
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "ACTIF";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
