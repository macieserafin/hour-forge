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
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID>, JpaSpecificationExecutor<TimeEntry> {
    @Override
    @EntityGraph(attributePaths = "project")
    Page<TimeEntry> findAll(Specification<TimeEntry> specification, Pageable pageable);

    boolean existsByProjectIdAndEntryTypeAndEndedAtIsNull(UUID projectId, EntryType entryType);

    boolean existsByTeamIdAndEntryTypeAndEndedAtIsNull(UUID teamId, EntryType entryType);

    long countByTeamId(UUID teamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TimeEntry entry where entry.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);

    Optional<TimeEntry> findByUserIdAndEntryTypeAndEndedAtIsNull(UUID userId, EntryType entryType);

    Optional<TimeEntry> findByIdAndUserId(UUID id, UUID userId);
}
