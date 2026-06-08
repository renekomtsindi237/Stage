package cm.imf.pipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "otp_codes", schema = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "attempts_used", nullable = false)
    private int attemptsUsed;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isExhausted() {
        return used || attemptsUsed >= 3;
    }
}
