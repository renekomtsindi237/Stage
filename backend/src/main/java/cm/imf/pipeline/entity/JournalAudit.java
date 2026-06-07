package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Entité de journal d'audit — trace chaque action sensible de l'API.
 * Mappe la table app.journal_audit (Flyway V3).
 */
@Entity
@Table(name = "journal_audit", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private User utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id")
    private cm.imf.pipeline.entity.Imf imf;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entite", length = 50)
    private String entite;

    @Column(name = "entite_id", length = 100)
    private String entiteId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

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
}
