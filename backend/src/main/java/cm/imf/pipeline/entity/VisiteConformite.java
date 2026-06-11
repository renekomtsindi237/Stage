package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "visites_conformite", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteConformite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id", nullable = false)
    private Long dossierId;

    @Column(name = "agent_credit_id", nullable = false)
    private Long agentCreditId;

    @Column(name = "date_visite", nullable = false)
    private LocalDate dateVisite;

    @Column(name = "conformite_observee", nullable = false)
    private boolean conformiteObservee;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String observations;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        if (dateVisite == null) dateVisite = LocalDate.now();
    }
}
