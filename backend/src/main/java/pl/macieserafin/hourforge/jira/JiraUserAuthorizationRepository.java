package pl.macieserafin.hourforge.jira;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JiraUserAuthorizationRepository extends JpaRepository<JiraUserAuthorization, UUID> {
    Optional<JiraUserAuthorization> findByUserIdAndCloudId(UUID userId, String cloudId);

    /**
     * Serializes token refreshes: Atlassian disables the refresh token that was used, and one grant can sit in several
     * rows (one per site), so every row of the person is locked, always in the same order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from JiraUserAuthorization a where a.user.id = :userId order by a.id")
    List<JiraUserAuthorization> findAllLockedByUserId(UUID userId);

    List<JiraUserAuthorization> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from JiraUserAuthorization a where a.cloudId = :cloudId")
    int deleteAllByCloudId(String cloudId);

    @Modifying(flushAutomatically = true)
    @Query("delete from JiraUserAuthorization a where a.user.id = :userId")
    int deleteAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from JiraUserAuthorization a where a.user.id = :userId and a.cloudId = :cloudId")
    int deleteByUserIdAndCloudId(UUID userId, String cloudId);
}
