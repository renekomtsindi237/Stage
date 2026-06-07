package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collectes_epargne", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollecteEpargne extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_mobile", nullable = false, unique = true)
    private UUID uuidMobile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imf_id", nullable = false)
    private Imf imf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private CycleCollecte cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private User agent;

    @NotBlank
    @Column(name = "client_id_externe", nullable = false, length = 50)
    private String clientIdExterne;

    @NotNull
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    @Column(name = "montant_collecte", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantCollecte;

    @NotNull
    @Column(name = "date_collecte", nullable = false)
    private LocalDate dateCollecte;

    @Column(name = "heure_collecte")
    private LocalTime heureCollecte;

    @NotBlank
    @Column(name = "canal_paiement", nullable = false, length = 20)
    private String canalPaiement;

    @Column(name = "reference_transaction", length = 100)
    private String referenceTransaction;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "precision_gps_metres", precision = 6, scale = 1)
    private BigDecimal precisionGpsMetres;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "SOUMISE";

    @Column(name = "motif_rejet", columnDefinition = "TEXT")
    private String motifRejet;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private User validatedBy;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = OffsetDateTime.now();
        if (uuidMobile == null) uuidMobile = UUID.randomUUID();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
