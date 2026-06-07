package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.StatutEcheance;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entité JPA — échéances de remboursement applicatives.
 * Mappe la table app.echeances_app (Flyway V4).
 *
 * Contrainte métier : (id_pret, num_echeance) est unique.
 */
@Entity
@Table(name = "echeances_app", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcheanceApp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence métier du prêt (clé logique vers staging.stg_prets) */
    @Column(name = "id_pret", nullable = false, length = 30)
    private String idPret;

    /** Agent responsable du suivi (optionnel) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private User agent;

    /** Numéro d'ordre de l'échéance (1, 2, 3…) */
    @Column(name = "num_echeance", nullable = false)
    private int numEcheance;

    /** Date prévue de paiement */
    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    /** Montant total attendu pour cette échéance */
    @Column(name = "montant_du", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantDu;

    /** Montant effectivement payé (peut être partiel) */
    @Column(name = "montant_paye", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantPaye = BigDecimal.ZERO;

    /** Date effective du paiement */
    @Column(name = "date_paiement")
    private LocalDate datePaiement;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutEcheance statut = StatutEcheance.EN_ATTENTE;

    /** Collecte terrain liée au paiement (traçabilité) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collecte_id")
    private CollecteTerrain collecte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id")
    private cm.imf.pipeline.entity.Imf imf;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
