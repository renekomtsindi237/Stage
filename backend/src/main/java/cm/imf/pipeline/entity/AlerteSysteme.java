package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Alerte système générée automatiquement ou manuellement lors d'un incident
 * d'infrastructure (CPU, disque, erreur DAG, pod KO, etc.).
 *
 * Table : app.alertes_systeme
 */
@Entity
@Table(name = "alertes_systeme", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlerteSysteme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type fonctionnel : CPU_SEUIL, DISQUE_PLEIN, DAG_ECHEC, CONTAINER_KO,
     * MEMORY_SEUIL, CONNEXION_SUSPECTE, API_DEGRADEE, etc.
     */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detail;

    /**
     * INFO | WARN | CRITIQUE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severite = "INFO";

    /**
     * ACTIVE | EN_COURS | RESOLUE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "ACTIVE";

    /** Composant source : backend-core, ml-api, postgres, airflow, nginx... */
    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "acquitte_par_id")
    private Long acquittéParId;

    @Column(name = "acquitte_at")
    private OffsetDateTime acquittéAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
