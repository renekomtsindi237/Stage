package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "creances",
    schema = "app",
    uniqueConstraints = @UniqueConstraint(columnNames = {"imf_id", "id_pret_externe"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Creance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imf_id", nullable = false)
    private Imf imf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    @NotBlank
    @Column(name = "id_pret_externe", nullable = false, length = 100)
    private String idPretExterne;

    @NotBlank
    @Column(name = "client_id_externe", nullable = false, length = 50)
    private String clientIdExterne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_informel_id")
    private ClientInformel clientInformel;

    // Montants
    @NotNull
    @Column(name = "montant_initial", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantInitial;

    @Column(name = "montant_impaye", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantImpaye = BigDecimal.ZERO;

    @Column(name = "capital_restant_du", precision = 15, scale = 2)
    private BigDecimal capitalRestantDu;

    @Column(name = "interets_retard", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interetsRetard = BigDecimal.ZERO;

    @Column(name = "penalites", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penalites = BigDecimal.ZERO;

    // Temporalité
    @Column(name = "date_deblocage")
    private LocalDate dateDeblocage;

    @Column(name = "date_premiere_echeance")
    private LocalDate datePremièreEcheance;

    @Column(name = "date_premiere_echeance_impayee")
    private LocalDate datePremièreEcheanceImpayee;

    @Column(name = "date_ouverture_creance", nullable = false)
    @Builder.Default
    private LocalDate dateOuvertureCreance = LocalDate.now();

    // PAR / COBAC
    @Column(name = "jours_retard", nullable = false)
    @Builder.Default
    private int joursRetard = 0;

    @Column(name = "categorie_par", nullable = false, length = 10)
    @Builder.Default
    private String categoriePar = "COURANT";

    @Column(name = "classe_risque_cobac", length = 10)
    private String classeRisqueCobac;

    @Column(name = "taux_provision_cobac", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal tauxProvisionCobac = BigDecimal.ZERO;

    @Column(name = "montant_provision", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantProvision = BigDecimal.ZERO;

    // Garanties
    @Column(name = "type_garantie", length = 40)
    private String typeGarantie;

    @Column(name = "valeur_garantie", precision = 15, scale = 2)
    private BigDecimal valeurGarantie;

    @Column(name = "nom_caution", length = 200)
    private String nomCaution;

    @Column(name = "telephone_caution", length = 20)
    private String telephoneCaution;

    // Statut workflow
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String statut = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_responsable_id")
    private User agentResponsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_recouvrement_id")
    private RecouvrementDossier dossierRecouvrement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
