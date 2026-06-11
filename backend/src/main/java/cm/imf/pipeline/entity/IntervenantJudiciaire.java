package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "intervenants_judiciaires", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntervenantJudiciaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "procedure_id", nullable = false)
    private Long procedureId;

    /**
     * HUISSIER | AVOCAT | COMMISSAIRE_PRISEUR | TRIBUNAL
     */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(name = "reference_mission", length = 100)
    private String referenceMission;

    @Column(name = "date_mandat")
    private LocalDate dateMandat;

    @Column(precision = 12, scale = 2)
    private BigDecimal honoraires;

    @Column(name = "statut_mission", length = 50)
    private String statutMission;

    @Column(length = 500)
    private String observations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
