package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * Score MCRS calculé par le service ML et reçu via Kafka (imf.ml.scoring.results).
 * Un seul enregistrement par (client_id_externe, imf_id) — mis à jour à chaque scoring.
 */
@Entity
@Table(
    name = "client_scores",
    schema = "ml",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id_externe", "imf_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id_externe", nullable = false, length = 50)
    private String clientIdExterne;

    @Column(name = "imf_id", nullable = false)
    private Integer imfId;

    @Column(name = "score_mcrs", nullable = false)
    private Double scoreMcrs;

    @Column(name = "score_crs", nullable = false)
    private Double scoreCrs;

    @Column(name = "score_rps", nullable = false)
    private Double scoreRps;

    @Column(name = "score_csi", nullable = false)
    private Double scoreCsi;

    @Column(name = "niveau_risque", nullable = false, length = 20)
    private String niveauRisque;

    @Column(name = "cobac_classe", nullable = false, length = 5)
    private String cobacClasse;

    @Column(name = "cobac_provision_taux", nullable = false)
    private Double cobacProvisionTaux;

    @ElementCollection
    @CollectionTable(
        name = "client_score_alertes",
        schema = "ml",
        joinColumns = @JoinColumn(name = "client_score_id")
    )
    @Column(name = "alerte", length = 100)
    private List<String> alertes;

    @Column(name = "model_version", length = 20)
    private String modelVersion;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
