package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "client_activites_produits",
    schema = "app",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "produit_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientActiviteProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientInformel client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private ProduitGenerique produit;

    @Column(name = "est_produit_principal", nullable = false)
    @Builder.Default
    private boolean estProduitPrincipal = false;

    @Column(name = "volume_habituel", precision = 10, scale = 2)
    private BigDecimal volumeHabituel;

    @Column(name = "unite_volume", length = 20)
    private String uniteVolume;

    @Column(name = "revenu_mensuel_produit", precision = 15, scale = 2)
    private BigDecimal revenuMensuelProduit;

    @Column(name = "mois_activite", columnDefinition = "integer[]")
    private Integer[] moisActivite;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
