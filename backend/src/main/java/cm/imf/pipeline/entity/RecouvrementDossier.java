package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.CategorieCobtac;
import cm.imf.pipeline.enums.RecouvrementPhase;
import cm.imf.pipeline.enums.TypeGarantie;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dossiers_recouvrement", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecouvrementDossier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    @Column(name = "id_pret", nullable = false, length = 100)
    private String idPret;

    @Column(name = "nom_client", length = 200)
    private String nomClient;

    @Column(name = "montant_impaye", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantImpaye = BigDecimal.ZERO;

    @Column(name = "jours_retard", nullable = false)
    @Builder.Default
    private int joursRetard = 0;

    // ── Classification COBAC ──────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_cobtac", length = 30)
    private CategorieCobtac categorieCobtac;

    /** Taux de provisionnement obligatoire (5 / 25 / 50 / 100 %) */
    @Column(name = "taux_provision", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal tauxProvision = BigDecimal.ZERO;

    /** Montant provisionné = montantImpaye × tauxProvision / 100 */
    @Column(name = "montant_provision", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantProvision = BigDecimal.ZERO;

    /** Date du premier impayé — base de calcul COBAC du délai de retard */
    @Column(name = "date_premiere_echeance_impayee")
    private LocalDate datePremiereEcheanceImpayee;

    // ── Caution / Garantie (mécanisme central des EMF camerounais) ────────────

    @Column(name = "nom_caution", length = 200)
    private String nomCaution;

    @Column(name = "telephone_caution", length = 30)
    private String telephoneCaution;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_garantie", length = 40)
    private TypeGarantie typeGarantie;

    // ── Frais de recouvrement (cumulés au fil des actions) ────────────────────

    /** Total des frais engagés : huissier, déplacements, avocat — récupérables sur le débiteur */
    @Column(name = "frais_recouvrement", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fraisRecouvrement = BigDecimal.ZERO;

    // ── Workflow ──────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 60)
    @Builder.Default
    private RecouvrementPhase phase = RecouvrementPhase.RELANCE_AMIABLE;

    @Column(name = "date_ouverture", nullable = false)
    private OffsetDateTime dateOuverture;

    @Column(name = "date_derniere_action")
    private OffsetDateTime dateDerniereAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_responsable_id")
    private User agentResponsable;

    @Column(name = "clos", nullable = false)
    @Builder.Default
    private boolean clos = false;

    @Column(name = "date_cloture")
    private OffsetDateTime dateCloture;

    @Column(name = "motif_cloture", length = 300)
    private String motifCloture;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (dateOuverture == null) dateOuverture = now;
        if (createdAt == null)     createdAt     = now;
        updatedAt = now;
        recalculerCobtac();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
        recalculerCobtac();
    }

    /** Recalcule automatiquement la catégorie COBAC et le montant provisionné. */
    public void recalculerCobtac() {
        if (joursRetard <= 0) return;
        this.categorieCobtac  = CategorieCobtac.of(joursRetard);
        this.tauxProvision    = categorieCobtac.getTauxProvision();
        this.montantProvision = montantImpaye
                .multiply(tauxProvision)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }
}
