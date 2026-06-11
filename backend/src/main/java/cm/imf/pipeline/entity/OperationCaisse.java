package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "operations_caisse", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caissier_id", nullable = false)
    private Long caissierId;

    @Column(name = "imf_id", nullable = false)
    private Long imfId;

    /**
     * ENCAISSEMENT | DECAISSEMENT
     */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(name = "pret_id", length = 100)
    private String pretId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "date_operation", nullable = false)
    private OffsetDateTime dateOperation;

    @Column(name = "solde_avant", precision = 18, scale = 2)
    private BigDecimal soldeAvant;

    @Column(name = "solde_apres", precision = 18, scale = 2)
    private BigDecimal soldeApres;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (dateOperation == null) dateOperation = now;
        if (createdAt == null)     createdAt = now;
    }
}
