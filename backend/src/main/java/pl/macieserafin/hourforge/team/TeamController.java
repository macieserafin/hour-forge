package pl.macieserafin.hourforge.team;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.RequestRateLimits;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teams;
    private final TeamMembershipService memberships;
    private final TeamManagementService management;
    private final RequestRateLimits rateLimits;

    public TeamController(TeamService teams, TeamMembershipService memberships, TeamManagementService management,
                          RequestRateLimits rateLimits) {
        this.teams = teams;
        this.memberships = memberships;
        this.management = management;
        this.rateLimits = rateLimits;
    }

    @GetMapping
    public List<TeamResponse> getTeams(@AuthenticationPrincipal AppUserPrincipal principal) {
        return teams.getTeams(principal.getUserId());
    }

    @GetMapping("/{teamId}/members")
    public TeamMembersResponse members(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID teamId, @RequestParam(defaultValue = "false") boolean includeFormer,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return memberships.list(principal.getUserId(), teamId, includeFormer, page, size);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID teamId, @PathVariable UUID userId) {
        memberships.remove(principal.getUserId(), teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId) {
        memberships.leave(principal.getUserId(), teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @Valid @RequestBody CreateTeamRequest request) {
        rateLimits.teamCreation(principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(teams.createTeam(principal.getUserId(), request));
    }

    @PatchMapping("/{teamId}")
    public TeamResponse update(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                               @Valid @RequestBody CreateTeamRequest request) {
        return management.update(principal.getUserId(), teamId, request);
    }

    @PostMapping("/{teamId}/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                                                  @Valid @RequestBody TransferOwnershipRequest request) {
        management.transferOwnership(principal.getUserId(), teamId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/archive")
    public TeamResponse archive(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId) {
        return management.archive(principal.getUserId(), teamId);
    }

    @GetMapping("/{teamId}/summary")
    public TeamSummaryResponse summary(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId) {
        return management.summary(principal.getUserId(), teamId);
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId) {
        management.delete(principal.getUserId(), teamId);
        return ResponseEntity.noContent().build();
    }
}
