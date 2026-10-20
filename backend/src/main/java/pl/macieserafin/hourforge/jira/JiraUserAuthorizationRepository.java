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

    /** Serializes token refreshes of one grant: Atlassian disables the refresh token that was used. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from JiraUserAuthorization a where a.user.id = :userId and a.cloudId = :cloudId")
    Optional<JiraUserAuthorization> findLockedByUserIdAndCloudId(UUID userId, String cloudId);

    List<JiraUserAuthorization> findAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from JiraUserAuthorization a where a.user.id = :userId")
    int deleteAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from JiraUserAuthorization a where a.user.id = :userId and a.cloudId = :cloudId")
    int deleteByUserIdAndCloudId(UUID userId, String cloudId);
}
