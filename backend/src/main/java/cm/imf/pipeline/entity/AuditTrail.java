package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Piste d'audit immuable — art. 27 Loi n° 2024/017 Cameroun.
 * Mappe app.audit_trail (Flyway V26).
 *
 * L'immuabilité est garantie au niveau DB via des règles PostgreSQL
 * (no UPDATE/DELETE). Côté JPA, le repository n'expose que des méthodes
 * de lecture et d'insertion — jamais de save() sur une entité existante.
 */
@Entity
@Immutable
@Table(name = "audit_trail", schema = "app")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imf_id")
    private Long imfId;

    @Column(name = "acteur_id")
    private Long acteurId;

    @Column(name = "acteur_username", nullable = false, length = 50)
    private String acteurUsername;

    @Column(name = "acteur_role", nullable = false, length = 30)
    private String acteurRole;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entite_type", nullable = false, length = 50)
    private String entiteType;

    @Column(name = "entite_id", length = 100)
    private String entiteId;

    /** État de l'entité avant la modification (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ancienne_valeur", columnDefinition = "jsonb")
    private Map<String, Object> ancienneValeur;

    /** État de l'entité après la modification (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nouvelle_valeur", columnDefinition = "jsonb")
    private Map<String, Object> nouvelleValeur;

    @Column(name = "motif", columnDefinition = "TEXT")
    private String motif;

    @Column(name = "ip_client", length = 45)
    private String ipClient;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private String statut = "SUCCES";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // ── Actions possibles ─────────────────────────────────────────────────────

    public static final String ACTION_CREATION          = "CREATION";
    public static final String ACTION_MODIFICATION      = "MODIFICATION";
    public static final String ACTION_SUPPRESSION       = "SUPPRESSION";
    public static final String ACTION_CONSULTATION      = "CONSULTATION";
    public static final String ACTION_EXPORT            = "EXPORT";
    public static final String ACTION_CONNEXION         = "CONNEXION";
    public static final String ACTION_DECONNEXION       = "DECONNEXION";
    public static final String ACTION_CHANGEMENT_STATUT = "CHANGEMENT_STATUT";
    public static final String ACTION_ACCES_REFUSE      = "ACCES_REFUSE";
    public static final String ACTION_MASQUAGE_DONNEES  = "MASQUAGE_DONNEES";
    public static final String ACTION_DEMANDE_RGPD      = "DEMANDE_RGPD";
    public static final String ACTION_CONSENTEMENT      = "CONSENTEMENT";

    // ── Types d'entités ───────────────────────────────────────────────────────

    public static final String ENTITE_DOSSIER      = "DOSSIER";
    public static final String ENTITE_CREANCE      = "CREANCE";
    public static final String ENTITE_CLIENT       = "CLIENT";
    public static final String ENTITE_POSITION     = "POSITION";
    public static final String ENTITE_COLLECTE     = "COLLECTE";
    public static final String ENTITE_ALERTE       = "ALERTE";
    public static final String ENTITE_UTILISATEUR  = "UTILISATEUR";
    public static final String ENTITE_ECHEANCE     = "ECHEANCE";
    public static final String ENTITE_ETIQUETTE    = "ETIQUETTE";
    public static final String ENTITE_CONSENTEMENT = "CONSENTEMENT";
    public static final String ENTITE_EXPORT             = "EXPORT";
    public static final String ENTITE_AUTH               = "AUTH";
    public static final String ENTITE_VIOLATION_DONNEES  = "VIOLATION_DONNEES";
}
