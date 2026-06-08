package cm.imf.pipeline.repository;

import cm.imf.pipeline.entity.OtpCode;
import cm.imf.pipeline.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now")
    void deleteExpired(OffsetDateTime now);
}
