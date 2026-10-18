package pl.macieserafin.hourforge.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    @Query("""
            select new pl.macieserafin.hourforge.team.TeamResponse(
                team.id, team.name, team.timezone, team.createdAt, member.role)
            from TeamMember member join member.team team
            where member.user.id = :userId and member.leftAt is null
            order by team.createdAt desc, team.id asc
            """)
    List<TeamResponse> findActiveTeamsByUserId(UUID userId);
}
