package pl.macieserafin.hourforge.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select team from Team team where team.id = :id")
    Optional<Team> findLockedById(UUID id);

    java.util.List<Team> findByWeeklyReportEnabledTrueAndArchivedAtIsNull();

    @Modifying(clearAutomatically = true)
    @Query("delete from Team team where team.id = :id")
    int deleteTeamById(UUID id);
}
