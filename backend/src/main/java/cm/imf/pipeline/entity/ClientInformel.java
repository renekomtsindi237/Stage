package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "clients_informels",
    schema = "app",
    uniqueConstraints = @UniqueConstraint(columnNames = {"imf_id", "client_id_externe"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInformel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imf_id", nullable = false)
    private Imf imf;

    @NotBlank
    @Column(name = "client_id_externe", nullable = false, length = 50)
    private String clientIdExterne;

    @NotBlank
    @Column(name = "nom_complet", nullable = false, length = 200)
    private String nomComplet;

    @Column(name = "telephone_principal", length = 20)
    private String telephonePrincipal;

    @Column(name = "telephone_secondaire", length = 20)
    private String telephoneSecondaire;

    @Column(name = "zone_id", length = 20)
    private String zoneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "sexe", length = 1)
    private String sexe;

    @Column(name = "secteur_principal", nullable = false, length = 30)
    @Builder.Default
    private String secteurPrincipal = "COMMERCE";

    @Column(name = "sous_secteur", length = 50)
    private String sousSecteur;

    @Column(name = "annees_experience")
    private Short anneesExperience;

    @Column(name = "revenu_mensuel_estime", precision = 15, scale = 2)
    private BigDecimal revenuMensuelEstime;

    @Column(name = "marche_principal", length = 100)
    private String marchePrincipal;

    @Column(name = "frequence_marche", length = 20)
    private String frequenceMarche;

    @Column(name = "niveau_education", length = 20)
    private String niveauEducation;

    @Column(name = "situation_familiale", length = 20)
    private String situationFamiliale;

    @Column(name = "nombre_personnes_charge")
    private Short nombrePersonnesCharge;

    @Column(name = "latitude_activite", precision = 10, scale = 7)
    private BigDecimal latitudeActivite;

    @Column(name = "longitude_activite", precision = 10, scale = 7)
    private BigDecimal longitudeActivite;

    @Column(name = "adresse_activite", columnDefinition = "TEXT")
    private String adresseActivite;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClientActiviteProduit> activitesProduits = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
