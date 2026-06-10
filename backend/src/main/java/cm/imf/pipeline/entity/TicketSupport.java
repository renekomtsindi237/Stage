package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ticket de support — canal de communication entre les utilisateurs
 * et l'équipe SUPPORT de la plateforme IMF Pipeline.
 *
 * Table : app.tickets_support
 */
@Entity
@Table(name = "tickets_support", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketSupport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false,
            columnDefinition = "uuid DEFAULT gen_random_uuid()")
    @Builder.Default
    private UUID uid = UUID.randomUUID();

    /** Null pour les tickets ouverts par SUPPORT / SUPER_ADMIN (cross-IMF). */
    @Column(name = "imf_id")
    private Long imfId;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(name = "auteur_username", nullable = false, length = 50)
    private String auteurUsername;

    @Column(name = "auteur_role", nullable = false, length = 30)
    private String auteurRole;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * BUG_TECHNIQUE | QUESTION_FONCTIONNELLE | DEMANDE_ACCES | PERFORMANCE | AUTRE
     */
    @Column(nullable = false, length = 50)
    private String categorie;

    /**
     * BASSE | NORMALE | HAUTE | CRITIQUE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String priorite = "NORMALE";

    /**
     * OUVERT | EN_COURS | RESOLU | FERME
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "OUVERT";

    @Column(name = "traite_par_id")
    private Long traitéParId;

    @Column(name = "traite_par_username", length = 50)
    private String traitéParUsername;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "date_traitement")
    private OffsetDateTime dateTraitement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (uid == null) uid = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
