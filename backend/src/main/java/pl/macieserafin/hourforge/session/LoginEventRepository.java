package pl.macieserafin.hourforge.session;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
    List<LoginEvent> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable page);

    List<LoginEvent> findByUserIdOrderByOccurredAtAsc(UUID userId);

    boolean existsByUserIdAndOutcome(UUID userId, LoginEvent.Outcome outcome);

    /** Whether the person signed in before from the same kind of device (unknown parts count as equal). */
    @Query("select count(e) > 0 from LoginEvent e where e.userId = :userId and e.outcome = pl.macieserafin.hourforge.session.LoginEvent.Outcome.SUCCESS"
            + " and ((:browser is null and e.browser is null) or e.browser = :browser)"
            + " and ((:system is null and e.system is null) or e.system = :system)")
    boolean signedInBeforeFrom(UUID userId, String browser, String system);

    @Modifying
    @Query("delete from LoginEvent e where e.userId = :userId")
    int deleteByUserId(UUID userId);

    @Modifying
    @Query("delete from LoginEvent e where e.occurredAt < :before")
    int deleteOlderThan(Instant before);
}
