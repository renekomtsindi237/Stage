package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "procedures_contentieux", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureContentieux extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id", nullable = false)
    private Long dossierId;

    /**
     * INJONCTION_PAYER | SAISIE_CONSERVATOIRE | SAISIE_ATTRIBUTION |
     * SAISIE_VENTE | REALISATION_HYPOTHEQUE
     */
    @Column(name = "type_procedure", nullable = false, length = 40)
    private String typeProcedure;

    @Column(length = 200)
    private String juridiction;

    @Column(name = "numero_affaire", length = 100)
    private String numeroAffaire;

    @Column(name = "date_saisine")
    private LocalDate dateSaisine;

    /**
     * EN_COURS | JUGEMENT_RENDU | CLOTUREE | SUSPENDUE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "EN_COURS";

    @Column(name = "responsable_id", nullable = false)
    private Long responsableId;

    @Column(name = "montant_reclame", precision = 15, scale = 2)
    private BigDecimal montantReclame;

    @Column(name = "montant_recouvre", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantRecouvre = java.math.BigDecimal.ZERO;

    @Column(name = "date_decheance_terme")
    private LocalDate dateDechéanceTerme;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
