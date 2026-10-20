package pl.macieserafin.hourforge.jira;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OAuthStateRepository extends JpaRepository<OAuthState, UUID> {
    /** Locked, so two callbacks carrying the same state cannot both be accepted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OAuthState> findByStateHash(String stateHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from OAuthState s where s.expiresAt < :before")
    int deleteExpired(Instant before);

    @Modifying(flushAutomatically = true)
    @Query("delete from OAuthState s where s.user.id = :userId")
    int deleteAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from OAuthState s where s.teamId = :teamId")
    int deleteAllByTeamId(UUID teamId);
}
