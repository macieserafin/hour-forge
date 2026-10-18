package pl.macieserafin.hourforge.invitation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.TeamResponse;

import java.util.UUID;

@RestController
public class InvitationController {
    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/api/teams/{teamId}/invites")
    public ResponseEntity<InviteResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                @PathVariable UUID teamId,
                                                @Valid @RequestBody CreateInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitations.create(principal.getUserId(), teamId, request));
    }

    @PostMapping("/api/invites/{token}/accept")
    public TeamResponse accept(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String token) {
        return invitations.accept(principal.getUserId(), token);
    }
}
