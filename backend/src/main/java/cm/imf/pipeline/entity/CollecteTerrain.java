package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.StatutCollecte;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "collectes_terrain", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollecteTerrain extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_collecte_mobile", nullable = false, unique = true, length = 50)
    private String idCollecteMobile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private User agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id")
    private cm.imf.pipeline.entity.Imf imf;

    @NotBlank
    @Column(name = "client_id", nullable = false, length = 30)
    private String clientId;

    @NotBlank
    @Column(name = "pret_id", nullable = false, length = 30)
    private String pretId;

    @NotNull
    @Column(name = "date_collecte", nullable = false)
    private LocalDate dateCollecte;

    @NotNull
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    @Column(name = "montant_collecte", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantCollecte;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "canal_paiement", nullable = false, length = 20)
    private CanalPaiement canalPaiement;

    @Column(name = "reference_transaction", length = 100)
    private String referenceTransaction;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutCollecte statut = StatutCollecte.SOUMISE;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        if (statut == null) statut = StatutCollecte.SOUMISE;
    }
}
