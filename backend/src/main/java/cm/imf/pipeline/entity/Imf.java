package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.NiveauKyc;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tenant de la plateforme — une institution de microfinance.
 * Toutes les données métier sont isolées par imf_id.
 */
@Entity
@Table(name = "imf", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Imf extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique — ex: CAMCCUL, MUCCC */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String pays = "Cameroun";

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    // ── Identité & constitution ──────────────────────────────────────────────

    @Column(name = "denomination_sociale", length = 200)
    private String denominationSociale;

    @Column(name = "adresse_siege", length = 500)
    private String adresseSiege;

    /** SA, SARL, Coopérative, Mutuelle, Association */
    @Column(name = "forme_juridique", length = 50)
    private String formeJuridique;

    @Column(name = "capital_social", precision = 20, scale = 2)
    private BigDecimal capitalSocial;

    @Column(name = "num_agrement", length = 100)
    private String numAgrement;

    @Column(length = 20)
    private String telephone;

    @Column(length = 100)
    private String email;

    // ── Paramètres crédit ────────────────────────────────────────────────────

    @Column(name = "taux_interet_annuel", precision = 5, scale = 2)
    private BigDecimal tauxInteretAnnuel;

    @Column(name = "duree_max_credit_mois")
    private Integer dureeMaxCreditMois;

    @Column(name = "taux_penalite_retard", precision = 5, scale = 2)
    private BigDecimal tauxPenaliteRetard;

    @Column(name = "seuil_relance_jours")
    private Integer seuilRelanceJours;

    // ── Paramètres épargne ───────────────────────────────────────────────────

    @Column(name = "taux_epargne", precision = 5, scale = 2)
    private BigDecimal tauxEpargne;

    @Column(name = "solde_min_epargne", precision = 15, scale = 2)
    private BigDecimal soldeMinEpargne;

    @Column(name = "frais_tenue_compte", precision = 10, scale = 2)
    private BigDecimal fraisTenueCompte;

    // ── Segmentation & garanties ─────────────────────────────────────────────

    /** Segments clients acceptés — valeurs séparées par virgule */
    @Column(name = "segments_clients", length = 200)
    private String segmentsClients;

    /** Types de garanties acceptées — valeurs séparées par virgule */
    @Column(name = "types_garanties", length = 200)
    private String typesGaranties;

    // ── Paramètres opérationnels adaptables ──────────────────────────────────

    /** Taille maximale d'un document KYC en octets (défaut : 5 Mo). */
    @Column(name = "max_document_kyc_octets")
    @Builder.Default
    private Long maxDocumentKycOctets = 5_242_880L;

    /** Niveau KYC minimal obligatoire pour accorder un crédit (COBAC-configurable). */
    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_kyc_minimal", length = 20)
    @Builder.Default
    private NiveauKyc niveauKycMinimal = NiveauKyc.NIVEAU_1;

    /** Nombre maximum de tentatives de connexion avant blocage temporaire par IP. */
    @Column(name = "max_tentatives_connexion")
    @Builder.Default
    private Integer maxTentativesConnexion = 5;

    /** URL du logo de l'IMF (stocké côté serveur, servi via /api/uploads/imf-logos/). */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    // ── Audit ────────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
