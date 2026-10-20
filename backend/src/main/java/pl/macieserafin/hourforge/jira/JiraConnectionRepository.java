package pl.macieserafin.hourforge.jira;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JiraConnectionRepository extends JpaRepository<JiraConnection, UUID> {
    Optional<JiraConnection> findByTeamId(UUID teamId);

    boolean existsByCloudId(String cloudId);

    @Modifying(flushAutomatically = true)
    @Query("delete from JiraConnection connection where connection.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);
}
