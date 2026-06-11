package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "garanties_credit", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GarantieCredit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id", nullable = false)
    private Long dossierId;

    /**
     * AVAL | NANTISSEMENT_STOCK | NANTISSEMENT_MATERIEL | HYPOTHEQUE | AUTRE
     */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "valeur_estimee", precision = 15, scale = 2)
    private BigDecimal valeurEstimee;

    @Column(name = "reference_document", length = 200)
    private String referenceDocument;

    @Column(name = "caution_nom", length = 200)
    private String cautionNom;

    @Column(name = "caution_telephone", length = 30)
    private String cautionTelephone;

    /**
     * COLLECTEE | ACTIVEE | REALISEE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "COLLECTEE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
