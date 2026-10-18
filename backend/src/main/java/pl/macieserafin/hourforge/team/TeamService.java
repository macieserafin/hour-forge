package pl.macieserafin.hourforge.team;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class TeamService {
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;

    public TeamService(TeamRepository teams, TeamMemberRepository members, UserRepository users) {
        this.teams = teams;
        this.members = members;
        this.users = users;
    }

    @Transactional
    public TeamResponse createTeam(UUID currentUserId, CreateTeamRequest request) {
        var user = users.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var team = teams.saveAndFlush(new Team(request.name(), request.timezone()));
        members.save(new TeamMember(team, user, TeamRole.OWNER, Instant.now()));
        return new TeamResponse(team.getId(), team.getName(), team.getTimezone(), team.getCreatedAt(), TeamRole.OWNER);
    }
}
