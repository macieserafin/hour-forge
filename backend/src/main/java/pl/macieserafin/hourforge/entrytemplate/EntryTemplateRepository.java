package pl.macieserafin.hourforge.entrytemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EntryTemplateRepository extends JpaRepository<EntryTemplate, UUID> {
    List<EntryTemplate> findByUserIdAndTeamIdOrderByNameAsc(UUID userId, UUID teamId);

    Optional<EntryTemplate> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndTeamId(UUID userId, UUID teamId);

    boolean existsByUserIdAndTeamIdAndNameIgnoreCase(UUID userId, UUID teamId, String name);

    @Modifying(flushAutomatically = true)
    @Query("delete from EntryTemplate t where t.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from EntryTemplate t where t.user.id = :userId")
    int deleteAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from EntryTemplate t where t.projectId = :projectId")
    int deleteAllByProjectId(UUID projectId);

    /** A Jira site stops being used: the links in templates go with it, the templates stay. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EntryTemplate t set t.jiraConnectionId = null, t.jiraIssueId = null, t.jiraIssueKey = null where t.jiraConnectionId = :connectionId")
    int clearJiraLinks(UUID connectionId);
}
