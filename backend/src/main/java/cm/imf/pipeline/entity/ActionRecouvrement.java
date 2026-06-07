package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.ResultatActionRecouvrement;
import cm.imf.pipeline.enums.StatutVerifMomo;
import cm.imf.pipeline.enums.TypeActionRecouvrement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "actions_recouvrement", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionRecouvrement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private RecouvrementDossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_action", nullable = false, length = 60)
    private TypeActionRecouvrement typeAction;

    @Column(name = "date_action", nullable = false)
    private OffsetDateTime dateAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private User agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultat", length = 60)
    private ResultatActionRecouvrement resultat;

    @Column(name = "promesse_date")
    private LocalDate promesseDate;

    @Column(name = "promesse_montant", precision = 15, scale = 2)
    private BigDecimal promesseMontant;

    // ── Paiement Mobile Money ─────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_paiement", length = 30)
    private CanalPaiement canalPaiement;

    @Column(name = "reference_transaction", length = 100)
    private String referenceTransaction;

    /** Numéro de téléphone MoMo/OM du payeur (format camerounais : 6XXXXXXXX) */
    @Column(name = "numero_telephone_paiement", length = 20)
    private String numeroTelephonePaiement;

    /**
     * Statut de vérification du paiement MoMo/Orange Money.
     * Prévient la fraude par capture d'écran falsifiée.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_verif_momo", length = 20)
    private StatutVerifMomo statutVerifMomo;

    // ── Frais et coûts de l'action ────────────────────────────────────────────

    /** Frais engendrés par cette action (huissier, déplacement, etc.) en FCFA */
    @Column(name = "frais_engages", precision = 15, scale = 2)
    private BigDecimal fraisEngages;

    @Column(name = "observation", columnDefinition = "TEXT")
    private String observation;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (dateAction == null) dateAction = now;
        if (createdAt  == null) createdAt  = now;
    }
}
