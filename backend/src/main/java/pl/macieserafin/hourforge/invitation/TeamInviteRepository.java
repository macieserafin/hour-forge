package pl.macieserafin.hourforge.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, UUID> {
    Page<TeamInvite> findByTeamId(UUID teamId, Pageable pageable);
    Optional<TeamInvite> findByIdAndTeamId(UUID id, UUID teamId);
    Optional<TeamInvite> findByTokenHash(String tokenHash);

    // Projection avoids caching stale invitation state while waiting for the team lock.
    @Query("select invite.team.id from TeamInvite invite where invite.tokenHash = :tokenHash")
    Optional<UUID> findTeamIdByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from TeamInvite invite where invite.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);
}
