package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sync_logs", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_id", nullable = false, unique = true, length = 36)
    private String syncId;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private User agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imf_id")
    private cm.imf.pipeline.entity.Imf imf;

    @Column(name = "nb_items_soumis", nullable = false)
    @Builder.Default
    private int nbItemsSoumis = 0;

    @Column(name = "nb_succes", nullable = false)
    @Builder.Default
    private int nbSucces = 0;

    @Column(name = "nb_doublons", nullable = false)
    @Builder.Default
    private int nbDoublons = 0;

    @Column(name = "nb_conflits", nullable = false)
    @Builder.Default
    private int nbConflits = 0;

    @Column(name = "nb_erreurs", nullable = false)
    @Builder.Default
    private int nbErreurs = 0;

    @Column(name = "statut_sync", nullable = false, length = 20)
    @Builder.Default
    private String statutSync = "EN_COURS";

    @Column(name = "message_sync", columnDefinition = "TEXT")
    private String messageSync;

    @Column(name = "sync_started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime syncStartedAt = OffsetDateTime.now();

    @Column(name = "sync_completed_at")
    private OffsetDateTime syncCompletedAt;

    @Column(name = "ip_client", length = 45)
    private String ipClient;
}
