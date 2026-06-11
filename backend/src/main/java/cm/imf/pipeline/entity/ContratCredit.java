package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "contrats_credit", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContratCredit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id", nullable = false, unique = true)
    private Long dossierId;

    @Column(name = "reference_contrat", nullable = false, length = 50)
    private String referenceContrat;

    @Column(name = "date_signature")
    private LocalDate dateSignature;

    @Column(name = "montant_final", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantFinal;

    @Column(name = "taux_interet", nullable = false, precision = 6, scale = 4)
    private BigDecimal tauxInteret;

    @Column(name = "frais_dossier", precision = 12, scale = 2)
    private BigDecimal fraisDossier;

    @Column(name = "nb_echeances", nullable = false)
    private Integer nbEcheances;

    /**
     * MENSUEL | HEBDOMADAIRE | QUOTIDIEN
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String periodicite = "MENSUEL";

    @Column(name = "signatures_conformes", nullable = false)
    @Builder.Default
    private boolean signaturesConformes = false;

    @Column(name = "agent_saisie_id", nullable = false)
    private Long agentSaisieId;

    @Column(name = "date_generation", nullable = false, updatable = false)
    private OffsetDateTime dateGeneration;

    @Column(name = "url_contrat_pdf", length = 500)
    private String urlContratPdf;

    /**
     * REDIGE | SIGNE | ARCHIVE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "REDIGE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null)        createdAt = now;
        if (dateGeneration == null)   dateGeneration = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
