package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Consentement RGPD d'un client ou agent pour une finalité de traitement.
 * Mappe app.consentements (Flyway V26).
 * Art. 9 et 50 — Loi n° 2024/017 Cameroun.
 */
@Entity
@Table(name = "consentements", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consentement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    @Column(name = "sujet_type", nullable = false, length = 20)
    private String sujetType;   // CLIENT ou AGENT

    @Column(name = "sujet_id", nullable = false)
    private Long sujetId;

    @Column(name = "sujet_reference", length = 100)
    private String sujetReference;

    @Column(name = "finalite", nullable = false, length = 100)
    private String finalite;

    @Column(name = "accorde", nullable = false)
    @Builder.Default
    private boolean accorde = false;

    @Column(name = "date_consentement", nullable = false)
    @Builder.Default
    private OffsetDateTime dateConsentement = OffsetDateTime.now();

    @Column(name = "date_retrait")
    private OffsetDateTime dateRetrait;

    @Column(name = "canal_collecte", nullable = false, length = 30)
    @Builder.Default
    private String canalCollecte = "APPLICATION";

    @Column(name = "version_politique", nullable = false, length = 20)
    @Builder.Default
    private String versionPolitique = "1.0";

    @Column(name = "ip_collecte", length = 45)
    private String ipCollecte;

    @Column(name = "recollecte_requise", nullable = false)
    @Builder.Default
    private boolean recollecteRequise = false;

    @Column(name = "collecte_par_id")
    private Long collecteParId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    // ── Finalités possibles ───────────────────────────────────────────────────

    public static final String FINALITE_GEOLOCALISATION      = "GEOLOCALISATION";
    public static final String FINALITE_RECOUVREMENT         = "RECOUVREMENT";
    public static final String FINALITE_SCORING_ML           = "SCORING_ML";
    public static final String FINALITE_NOTIFICATION_FCM     = "NOTIFICATION_FCM";
    public static final String FINALITE_PARTAGE_DONNEES_CBS  = "PARTAGE_DONNEES_CBS";
    public static final String FINALITE_EXPORT_RAPPORT       = "EXPORT_RAPPORT";
    public static final String FINALITE_CONSERVATION_ETENDUE = "CONSERVATION_ETENDUE";
}
