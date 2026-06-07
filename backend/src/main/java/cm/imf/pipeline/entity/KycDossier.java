package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.NiveauRisque;
import cm.imf.pipeline.enums.StatutKyc;
import cm.imf.pipeline.enums.TypeDocumentKyc;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kyc_dossiers", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDossier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id", nullable = false)
    private Imf imf;

    // ── Identité client ───────────────────────────────────────────────────────

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "nom_client", nullable = false, length = 200)
    private String nomClient;

    @Column(name = "prenom_client", length = 200)
    private String prenomClient;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "lieu_naissance", length = 200)
    private String lieuNaissance;

    @Column(name = "nationalite", length = 100)
    @Builder.Default
    private String nationalite = "Camerounaise";

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "adresse", length = 500)
    private String adresse;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "profession", length = 200)
    private String profession;

    @Column(name = "employeur", length = 200)
    private String employeur;

    @Column(name = "revenu_mensuel_estim", precision = 15, scale = 2)
    private BigDecimal revenuMensuelEstim;

    // ── Pièce d'identité principale ───────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "type_piece_identite", length = 40)
    private TypeDocumentKyc typePieceIdentite;

    @Column(name = "numero_piece", length = 80)
    private String numeroPiece;

    @Column(name = "date_emission_piece")
    private LocalDate dateEmissionPiece;

    @Column(name = "date_expiration_piece")
    private LocalDate dateExpirationPiece;

    @Column(name = "lieu_emission_piece", length = 150)
    private String lieuEmissionPiece;

    // ── Niveaux KYC ───────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_actuel", nullable = false, length = 20)
    @Builder.Default
    private NiveauKyc niveauActuel = NiveauKyc.NIVEAU_1;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_demande", nullable = false, length = 20)
    @Builder.Default
    private NiveauKyc niveauDemande = NiveauKyc.NIVEAU_1;

    // ── Statut ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    @Builder.Default
    private StatutKyc statut = StatutKyc.EN_ATTENTE;

    // ── Évaluation du risque LBC/FT ───────────────────────────────────────────

    /** Score 0-100 calculé par scorerRisque() à chaque mise à jour */
    @Column(name = "score_risque")
    @Builder.Default
    private int scoreRisque = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_risque", length = 20)
    @Builder.Default
    private NiveauRisque niveauRisque = NiveauRisque.FAIBLE;

    /** Personne Politiquement Exposée (PPE) — déclenche KYC renforcé obligatoire */
    @Column(name = "est_pep", nullable = false)
    @Builder.Default
    private boolean estPep = false;

    @Column(name = "motif_risque_eleve", length = 500)
    private String motifRisqueEleve;

    // ── Conformité LBC/FT ─────────────────────────────────────────────────────

    @Column(name = "verif_sanctions")
    @Builder.Default
    private boolean verifSanctions = false;

    @Column(name = "verif_listes_noires")
    @Builder.Default
    private boolean verifListesNoires = false;

    @Column(name = "date_dernier_audit")
    private OffsetDateTime dateDernierAudit;

    // ── Traçabilité ───────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificateur_id")
    private User verificateur;

    @Column(name = "date_verification")
    private OffsetDateTime dateVerification;

    @Column(name = "date_expiration_kyc")
    private LocalDate dateExpirationKyc;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ── Relations ─────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<KycDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<KycVerification> verifications = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        scorerRisque();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
        scorerRisque();
    }

    /**
     * Calcule automatiquement le score de risque et la catégorie LBC/FT.
     * Critères pondérés conformes au Règlement COBAC R-2005/01 :
     *   PPE (+40), niveau 3 demandé (+20), revenu élevé (+15),
     *   liste noire non vérifiée (+15), nationalité étrangère (+10)
     */
    public void scorerRisque() {
        int score = 0;
        if (estPep)                                                       score += 40;
        if (niveauDemande == NiveauKyc.NIVEAU_3)                         score += 20;
        if (revenuMensuelEstim != null
                && revenuMensuelEstim.compareTo(new BigDecimal("500000")) > 0) score += 15;
        if (!verifListesNoires)                                           score += 10;
        if (nationalite != null && !nationalite.equalsIgnoreCase("Camerounaise")) score += 10;
        if (motifRisqueEleve != null && !motifRisqueEleve.isBlank())     score += 5;

        this.scoreRisque = Math.min(score, 100);
        this.niveauRisque = NiveauRisque.of(this.scoreRisque);
    }
}
