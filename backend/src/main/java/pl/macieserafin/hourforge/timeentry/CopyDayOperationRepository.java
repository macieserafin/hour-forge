package pl.macieserafin.hourforge.timeentry;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CopyDayOperationRepository extends JpaRepository<CopyDayOperation, CopyDayOperation.Key> {
    default Optional<CopyDayOperation> find(UUID userId, UUID requestId) {
        return findById(new CopyDayOperation.Key(userId, requestId));
    }

    @Modifying(flushAutomatically = true)
    @Query("delete from CopyDayOperation o where o.createdAt < :before")
    int deleteOlderThan(Instant before);
}
