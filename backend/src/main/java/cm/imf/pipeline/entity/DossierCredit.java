package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dossiers_credit", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierCredit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    @Column(name = "agence_id")
    private Long agenceId;

    @Column(name = "agent_credit_id", nullable = false)
    private Long agentCreditId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "client_nom", length = 200)
    private String clientNom;

    @Column(name = "montant_demande", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantDemande;

    @Column(name = "duree_mois", nullable = false)
    private Integer dureeMois;

    @Column(name = "objet_financement", nullable = false, length = 300)
    private String objetFinancement;

    @Column(name = "secteur_activite", length = 100)
    private String secteurActivite;

    @Column(name = "revenu_estime", precision = 15, scale = 2)
    private BigDecimal revenuEstime;

    @Column(name = "charges_mensuelles", precision = 15, scale = 2)
    private BigDecimal chargesMensuelles;

    @Column(name = "capacite_remboursement", precision = 15, scale = 2)
    private BigDecimal capaciteRemboursement;

    /**
     * INSTRUCTION | EN_COMITE | APPROUVE | REJETE | AJOURNE | DEBLOQUE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "INSTRUCTION";

    @Column(name = "note_analyse", columnDefinition = "TEXT")
    private String noteAnalyse;

    @Column(name = "date_soumission")
    private OffsetDateTime dateSoumission;

    @Column(name = "date_decision")
    private OffsetDateTime dateDecision;

    @Column(name = "chef_agence_id")
    private Long chefAgenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (revenuEstime != null && chargesMensuelles != null) {
            capaciteRemboursement = revenuEstime.subtract(chargesMensuelles);
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
