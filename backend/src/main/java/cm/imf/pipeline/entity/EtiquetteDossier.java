package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Étiquette apposée sur un dossier de recouvrement.
 * Mappe app.etiquettes_dossiers (Flyway V26).
 */
@Entity
@Table(name = "etiquettes_dossiers", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtiquetteDossier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    @Column(name = "dossier_ref", nullable = false, length = 50)
    private String dossierRef;

    @Column(name = "dossier_type", nullable = false, length = 30)
    @Builder.Default
    private String dossierType = "DOSSIER_RECOUVREMENT";

    @Column(name = "code_etiquette", nullable = false, length = 50)
    private String codeEtiquette;

    @Column(name = "couleur", length = 7)
    private String couleur;

    @Column(name = "libelle_custom", length = 100)
    private String libelleCustom;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "date_debut", nullable = false)
    @Builder.Default
    private OffsetDateTime dateDebut = OffsetDateTime.now();

    @Column(name = "date_fin")
    private OffsetDateTime dateFin;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "pose_par_id", nullable = false)
    private Long posePar_Id;

    @Column(name = "pose_par_username", nullable = false, length = 50)
    private String poseParUsername;

    @Column(name = "retire_par_id")
    private Long retirePar_Id;

    @Column(name = "retire_par_username", length = 50)
    private String retireParUsername;

    @Column(name = "date_retrait")
    private OffsetDateTime dateRetrait;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    // ── Codes d'étiquette disponibles ─────────────────────────────────────────

    public static final String CODE_PRIORITAIRE       = "PRIORITAIRE";
    public static final String CODE_SENSIBLE          = "SENSIBLE";
    public static final String CODE_CONTENTIEUX       = "CONTENTIEUX";
    public static final String CODE_RESTRUCTURE       = "RESTRUCTURE";
    public static final String CODE_PERDU             = "PERDU";
    public static final String CODE_DECEDE            = "DECEDE";
    public static final String CODE_FRAUDE_SUSPECTEE  = "FRAUDE_SUSPECTEE";
    public static final String CODE_GARANTIE_ACTIVEE  = "GARANTIE_ACTIVEE";
    public static final String CODE_SAISONNALITE      = "SAISONNALITE";
    public static final String CODE_SUIVI_SPECIAL     = "SUIVI_SPECIAL";
}
