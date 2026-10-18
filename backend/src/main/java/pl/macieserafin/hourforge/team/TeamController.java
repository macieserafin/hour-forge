package pl.macieserafin.hourforge.team;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teams;

    public TeamController(TeamService teams) {
        this.teams = teams;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teams.createTeam(principal.getUserId(), request));
    }
}
