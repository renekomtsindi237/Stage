package cm.imf.pipeline.entity;

import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.ResultatVerificationKyc;
import cm.imf.pipeline.enums.StatutKyc;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "kyc_verifications", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private KycDossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificateur_id")
    private User verificateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "ancien_statut", length = 30)
    private StatutKyc ancienStatut;

    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_statut", length = 30)
    private StatutKyc nouveauStatut;

    @Enumerated(EnumType.STRING)
    @Column(name = "ancien_niveau", length = 20)
    private NiveauKyc ancienNiveau;

    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_niveau", length = 20)
    private NiveauKyc nouveauNiveau;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultat", length = 30)
    private ResultatVerificationKyc resultat;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
