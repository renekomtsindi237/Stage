package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "decaissements", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Decaissement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrat_id", nullable = false, unique = true)
    private Long contratId;

    @Column(name = "caissier_id", nullable = false)
    private Long caissierId;

    @Column(name = "montant_net", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantNet;

    /**
     * ESPECES | MOBILE_MONEY | VIREMENT | CHEQUE
     */
    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "reference_paiement", length = 100)
    private String referencePaiement;

    @Column(name = "date_decaissement")
    private OffsetDateTime dateDecaissement;

    @Column(name = "autorise_par_id")
    private Long autoriseParId;

    /**
     * EN_ATTENTE | EXECUTE | ANNULE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "EN_ATTENTE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
