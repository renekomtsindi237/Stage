package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "actions_contentieux", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionContentieux {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "procedure_id", nullable = false)
    private Long procedureId;

    /**
     * CONSTAT | SIGNIFICATION | AUDIENCE | SAISIE_EXECUTION | VENTE_ENCHERES
     */
    @Column(name = "type_action", nullable = false, length = 30)
    private String typeAction;

    @Column(name = "date_action", nullable = false)
    private LocalDate dateAction;

    @Column(length = 500)
    private String intervenants;

    @Column(length = 500)
    private String resultat;

    @Column(name = "montant_recouvre", precision = 15, scale = 2)
    private BigDecimal montantRecouvre;

    @Column(name = "pj_url", length = 500)
    private String pjUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        if (dateAction == null) dateAction = LocalDate.now();
    }
}
