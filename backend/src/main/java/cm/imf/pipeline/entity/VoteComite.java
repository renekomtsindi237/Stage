package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "votes_comite", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteComite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comite_id", nullable = false)
    private Long comiteId;

    @Column(name = "votant_id", nullable = false)
    private Long votantId;

    @Column(name = "role_votant", nullable = false, length = 30)
    private String roleVotant;

    /**
     * POUR | CONTRE | ABSTENTION
     */
    @Column(nullable = false, length = 15)
    private String vote;

    @Column(length = 500)
    private String commentaire;

    @Column(name = "voted_at", nullable = false, updatable = false)
    private OffsetDateTime votedAt;

    @PrePersist
    void prePersist() {
        votedAt = OffsetDateTime.now();
    }
}
