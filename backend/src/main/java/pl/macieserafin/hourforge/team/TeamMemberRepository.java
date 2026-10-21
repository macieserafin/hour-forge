package pl.macieserafin.hourforge.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    @Query("select member.team.id from TeamMember member where member.user.id = :userId and member.leftAt is null order by member.team.id")
    List<UUID> findActiveTeamIds(UUID userId);

    @Query("select member from TeamMember member join fetch member.team where member.user.id = :userId and member.leftAt is null")
    List<TeamMember> findActiveByUserId(UUID userId);

    @Query("select member from TeamMember member join fetch member.team where member.user.id = :userId order by member.joinedAt asc, member.id asc")
    List<TeamMember> findAllByUserId(UUID userId);

    @Query("select member from TeamMember member join fetch member.user where member.team.id = :teamId and member.leftAt is null")
    List<TeamMember> findActiveByTeamId(UUID teamId);

    long countByTeamIdAndLeftAtIsNull(UUID teamId);

    @Modifying
    @Query("delete from TeamMember member where member.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);

    @Query("""
            select new pl.macieserafin.hourforge.team.TeamMemberResponse(
                member.user.id, member.user.displayName, member.role, member.joinedAt, member.leftAt,
                member.jobTitle, member.ftePercent)
            from TeamMember member
            where member.team.id = :teamId and (:includeFormer = true or member.leftAt is null)
            order by member.role desc, member.user.displayName asc, member.user.id asc
            """)
    Page<TeamMemberResponse> findMembers(UUID teamId, boolean includeFormer, Pageable pageable);

    @Query("""
            select new pl.macieserafin.hourforge.team.TeamResponse(
                team.id, team.name, team.timezone, team.createdAt, member.role, team.archivedAt, team.activeTimersVisible,
                team.requireDescription, team.requireTagOrIssue, team.weekStart, team.weeklyReportEnabled)
            from TeamMember member join member.team team
            where member.user.id = :userId and member.leftAt is null
            order by team.createdAt desc, team.id asc
            """)
    List<TeamResponse> findActiveTeamsByUserId(UUID userId);
}
