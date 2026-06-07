package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Notification temps réel persistée — événement SSE mémorisé pour historique.
 * Mappe la table app.notifications (Flyway V10).
 */
@Entity
@Table(name = "notifications", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IMF propriétaire — null uniquement pour notifications système globales. */
    @Column(name = "imf_id")
    private Long imfId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Rôle ciblé — null = visible par tous les utilisateurs de l'IMF. */
    @Column(name = "target_role", length = 50)
    private String targetRole;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    @Builder.Default
    private boolean lu = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
