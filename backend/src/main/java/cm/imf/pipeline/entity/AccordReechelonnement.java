package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Accord formel de rééchelonnement d'une créance en souffrance.
 * Doit être validé par le comité de crédit de l'EMF et signé par le client.
 * Génère un nouveau calendrier de remboursement.
 */
@Entity
@Table(name = "accords_reechelonnement", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccordReechelonnement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private RecouvrementDossier dossier;

    /** Nouvelle mensualité négociée (en FCFA) */
    @Column(name = "nouveau_montant_mensuel", nullable = false, precision = 15, scale = 2)
    private BigDecimal nouveauMontantMensuel;

    /** Nombre de nouvelles échéances accordées */
    @Column(name = "nombre_nouvelles_echeances", nullable = false)
    private int nombreNouvellesEcheances;

    /** Date du premier paiement du nouveau calendrier */
    @Column(name = "date_debut_nouvel_echeancier", nullable = false)
    private LocalDate dateDebutNouvelEcheancier;

    /** Taux d'intérêt annuel appliqué sur le rééchelonnement (peut différer du taux initial) */
    @Column(name = "taux_interet_annuel", precision = 5, scale = 2)
    private BigDecimal tauxInteretAnnuel;

    /** Responsable ayant approuvé l'accord (directeur EMF ou comité de crédit) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approuve_par_id")
    private User approuvePar;

    /** Date de signature du contrat d'avenant par le client */
    @Column(name = "date_signature")
    private LocalDate dateSignature;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    /** Indique si cet accord est encore en vigueur (false si annulé ou remplacé) */
    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
