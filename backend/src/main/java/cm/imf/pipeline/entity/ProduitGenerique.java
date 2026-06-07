package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "produits_generiques", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduitGenerique extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "code_produit", nullable = false, unique = true, length = 30)
    private String codeProduit;

    @NotBlank
    @Column(name = "nom_produit", nullable = false, length = 100)
    private String nomProduit;

    @NotBlank
    @Column(nullable = false, length = 30)
    private String categorie;

    @Column(name = "sous_categorie", length = 50)
    private String sousCateGorie;

    @Column(name = "unite_mesure_ref", nullable = false, length = 20)
    @Builder.Default
    private String uniteMesureRef = "KG";

    @Column(nullable = false)
    @Builder.Default
    private boolean saisonnalite = true;

    // Stocké comme entier[] PostgreSQL — géré via JDBC natif pour simplifier
    @Column(name = "mois_saison_haute", columnDefinition = "integer[]")
    private String moisSaisonHaute;

    @Column(name = "zones_production", columnDefinition = "varchar[]")
    private String zonesProduction;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
