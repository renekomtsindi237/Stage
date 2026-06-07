package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.StatutAlerte;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "alertes_impayes", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlerteImpaye extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_pret", nullable = false, length = 30)
    private String idPret;

    @Column(name = "date_generation", nullable = false)
    private OffsetDateTime dateGeneration;

    @Column(name = "jours_retard", nullable = false)
    private Integer joursRetard;

    @Column(name = "montant_en_retard", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantEnRetard;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_alerte", nullable = false, length = 20)
    @Builder.Default
    private StatutAlerte statutAlerte = StatutAlerte.ACTIVE;

    @Column(name = "fcm_sent", nullable = false)
    @Builder.Default
    private boolean fcmSent = false;

    @Column(name = "email_sent", nullable = false)
    @Builder.Default
    private boolean emailSent = false;

    @Column(name = "date_cloture")
    private OffsetDateTime dateCloture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id")
    private cm.imf.pipeline.entity.Imf imf;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (dateGeneration == null) dateGeneration = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
