package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.TypeDocumentKyc;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "kyc_documents", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private KycDossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 50)
    private TypeDocumentKyc typeDocument;

    @Column(name = "nom_fichier", nullable = false, length = 300)
    private String nomFichier;

    @Column(name = "chemin_stockage", length = 1000)
    private String cheminStockage;

    /** Contenu base64 — utilisé pour le stockage temporaire avant migration vers S3 */
    @Column(name = "contenu_base64", columnDefinition = "TEXT")
    private String contenuBase64;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "date_expiration_doc")
    private LocalDate dateExpirationDoc;

    /** NULL = non encore vérifié ; TRUE = accepté ; FALSE = rejeté */
    @Column(name = "valide")
    private Boolean valide;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifie_par_id")
    private User verifiePar;

    @Column(name = "date_verification")
    private OffsetDateTime dateVerification;

    // ── Analyse IA (OCR + extraction structurée) ─────────────────────────────
    // Signale les écarts au DSI pendant la vérification manuelle ; ne valide
    // ni ne bloque jamais automatiquement un document (décision humaine seule).

    /** Champs lus sur la pièce par le modèle vision (nom, dateNaissance, numeroPiece, ...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "donnees_extraites", columnDefinition = "jsonb")
    private Map<String, Object> donneesExtraites;

    /** Liste des divergences entre les champs saisis et les champs détectés. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ecarts_detectes", columnDefinition = "jsonb")
    private List<Map<String, Object>> ecartsDetectes;

    @Column(name = "analyse_ia_at")
    private OffsetDateTime analyseIaAt;

    /** Message d'erreur si l'analyse a échoué (ex : IA indisponible) — non bloquant. */
    @Column(name = "analyse_ia_erreur", length = 500)
    private String analyseIaErreur;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
