package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "delegations", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delegation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    /** Utilisateur qui délègue (supérieur hiérarchique). */
    @Column(name = "delegant_id", nullable = false)
    private Long delegantId;

    /** Utilisateur qui reçoit la délégation. */
    @Column(name = "delegataire_id", nullable = false)
    private Long delegataireId;

    /**
     * REASSIGNATION_DOSSIER — transfert d'un dossier crédit à un autre agent.
     * DELEGATION_AUTORITE   — délégation temporaire de pouvoir de validation.
     */
    @Column(name = "type_delegation", nullable = false, length = 30)
    private String typeDelegation;

    /** ID du dossier concerné (non null si REASSIGNATION_DOSSIER). */
    @Column(name = "objet_id")
    private Long objetId;

    @Column(name = "objet_type", length = 50)
    private String objetType;

    @Column(columnDefinition = "TEXT")
    private String motif;

    /** Rôle dont l'autorité est déléguée (DELEGATION_AUTORITE seulement). */
    @Column(name = "role_delegue", length = 30)
    private String roleDelegue;

    /** Plafond d'autorité délégué en FCFA (null = aucune limite explicite). */
    @Column(name = "montant_seuil", precision = 15, scale = 2)
    private BigDecimal montantSeuil;

    @Column(name = "date_debut", nullable = false)
    @Builder.Default
    private LocalDate dateDebut = LocalDate.now();

    /** Null = délégation sans limite de durée. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
