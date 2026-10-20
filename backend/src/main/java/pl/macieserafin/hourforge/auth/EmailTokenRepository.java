package pl.macieserafin.hourforge.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    Optional<EmailToken> findByTokenHash(String tokenHash);

    List<EmailToken> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, EmailTokenPurpose purpose);

    /** One-time tokens that expired or were used before the given moment are of no further use. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailToken t where t.expiresAt < :before or (t.usedAt is not null and t.usedAt < :before)")
    int deleteSpent(java.time.Instant before);

    @Modifying(flushAutomatically = true)
    @Query("delete from EmailToken t where t.user.id = :userId")
    int deleteByUserId(UUID userId);
}
