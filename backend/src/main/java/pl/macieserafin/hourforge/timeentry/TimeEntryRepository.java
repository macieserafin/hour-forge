package pl.macieserafin.hourforge.timeentry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    boolean existsByProjectIdAndEntryTypeAndEndedAtIsNull(UUID projectId, EntryType entryType);

    Optional<TimeEntry> findByUserIdAndEntryTypeAndEndedAtIsNull(UUID userId, EntryType entryType);

    Optional<TimeEntry> findByIdAndUserId(UUID id, UUID userId);
}
