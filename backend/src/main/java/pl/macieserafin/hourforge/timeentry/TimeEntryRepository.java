package pl.macieserafin.hourforge.timeentry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
}
