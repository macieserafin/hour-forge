package pl.macieserafin.hourforge.timeentry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID>, JpaSpecificationExecutor<TimeEntry> {
    boolean existsByProjectId(UUID projectId);
    @Override
    @EntityGraph(attributePaths = "project")
    Page<TimeEntry> findAll(Specification<TimeEntry> specification, Pageable pageable);

    boolean existsByProjectIdAndEntryTypeAndEndedAtIsNull(UUID projectId, EntryType entryType);

    boolean existsByTeamIdAndEntryTypeAndEndedAtIsNull(UUID teamId, EntryType entryType);

    long countByTeamId(UUID teamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TimeEntry entry where entry.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);

    /** The site stops being used: its links go, the time stays. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TimeEntry entry set entry.jiraConnectionId = null, entry.jiraIssueId = null, entry.jiraIssueKey = null where entry.jiraConnectionId = :connectionId")
    int clearJiraLinks(UUID connectionId);

    /** Running, not paused timers that started early enough to have reached the limit and were not reminded yet. */
    @Query("select e from TimeEntry e join fetch e.user join fetch e.team join fetch e.project where e.entryType = pl.macieserafin.hourforge.timeentry.EntryType.TIMER"
            + " and e.endedAt is null and e.pausedAt is null and e.longTimerRemindedAt is null and e.startedAt <= :startedBefore")
    List<TimeEntry> findDueForLongTimerReminder(java.time.Instant startedBefore);

    Optional<TimeEntry> findByUserIdAndEntryTypeAndEndedAtIsNull(UUID userId, EntryType entryType);

    Optional<TimeEntry> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"team", "project"})
    List<TimeEntry> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);
}
