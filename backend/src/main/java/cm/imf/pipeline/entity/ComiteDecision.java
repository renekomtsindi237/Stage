package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "comite_decisions", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComiteDecision extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id", nullable = false)
    private Long dossierId;

    /**
     * AGENCE | SIEGE | GRAND_COMITE
     */
    @Column(name = "type_comite", nullable = false, length = 20)
    private String typeComite;

    @Column(name = "president_id", nullable = false)
    private Long presidentId;

    @Column(name = "date_seance")
    private OffsetDateTime dateSeance;

    /**
     * APPROUVE | REJETE | AJOURNE | RESTRUCTURE — null tant que la décision n'est pas prise
     */
    @Column(length = 20)
    private String decision;

    @Column(name = "montant_approuve", precision = 15, scale = 2)
    private BigDecimal montantApprouve;

    @Column(name = "taux_approuve", precision = 6, scale = 4)
    private BigDecimal tauxApprouve;

    @Column(name = "duree_approuvee")
    private Integer dureeApprouvee;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "quorum_atteint", nullable = false)
    @Builder.Default
    private boolean quorumAtteint = false;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
