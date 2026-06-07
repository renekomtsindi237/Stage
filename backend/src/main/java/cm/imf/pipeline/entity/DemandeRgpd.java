package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Demande d'exercice de droit RGPD.
 * Mappe app.demandes_rgpd (Flyway V26).
 * Art. 37-43 — Loi n° 2024/017 Cameroun — délai légal de réponse 30 jours.
 */
@Entity
@Table(name = "demandes_rgpd", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeRgpd extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    @Column(name = "demandeur_id")
    private Long demandeurId;

    @Column(name = "demandeur_username", nullable = false, length = 50)
    private String demandeurUsername;

    @Column(name = "demandeur_email", length = 150)
    private String demandeurEmail;

    @Column(name = "type_droit", nullable = false, length = 30)
    private String typeDroit;

    @Column(name = "perimetre", nullable = false, columnDefinition = "TEXT")
    private String perimetre;

    @Column(name = "finalite_concernee", length = 100)
    private String finaliteConcernee;

    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private String statut = "EN_ATTENTE";

    @Column(name = "date_soumission", nullable = false)
    @Builder.Default
    private OffsetDateTime dateSoumission = OffsetDateTime.now();

    @Column(name = "date_limite_reponse", nullable = false)
    @Builder.Default
    private OffsetDateTime dateLimiteReponse = OffsetDateTime.now().plusDays(30);

    @Column(name = "date_traitement")
    private OffsetDateTime dateTraitement;

    @Column(name = "traite_par_id")
    private Long traiteParId;

    @Column(name = "traite_par_username", length = 50)
    private String traiteParUsername;

    @Column(name = "reponse", columnDefinition = "TEXT")
    private String reponse;

    @Column(name = "motif_refus", columnDefinition = "TEXT")
    private String motifRefus;

    @Column(name = "export_url", length = 500)
    private String exportUrl;

    @Column(name = "export_expire_at")
    private OffsetDateTime exportExpireAt;

    @Column(name = "canal_soumission", nullable = false, length = 30)
    @Builder.Default
    private String canalSoumission = "APPLICATION";

    @Column(name = "ip_soumission", length = 45)
    private String ipSoumission;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    // ── Types de droits ───────────────────────────────────────────────────────

    public static final String DROIT_ACCES         = "ACCES";
    public static final String DROIT_RECTIFICATION = "RECTIFICATION";
    public static final String DROIT_EFFACEMENT    = "EFFACEMENT";
    public static final String DROIT_OPPOSITION    = "OPPOSITION";
    public static final String DROIT_PORTABILITE   = "PORTABILITE";
    public static final String DROIT_LIMITATION    = "LIMITATION";

    public static final String STATUT_EN_ATTENTE   = "EN_ATTENTE";
    public static final String STATUT_EN_COURS     = "EN_COURS";
    public static final String STATUT_TRAITEE      = "TRAITEE";
    public static final String STATUT_REFUSEE      = "REFUSEE";
    public static final String STATUT_PARTIELLE    = "PARTIELLEMENT_TRAITEE";
}
