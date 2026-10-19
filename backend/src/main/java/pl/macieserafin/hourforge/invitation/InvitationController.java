package pl.macieserafin.hourforge.invitation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.RequestRateLimits;
import pl.macieserafin.hourforge.team.TeamResponse;

import java.util.UUID;

@RestController
public class InvitationController {
    @GetMapping("/api/teams/{teamId}/invites")
    public InviteSummaryResponse.Page list(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invitations.list(principal.getUserId(), teamId, page, size);
    }

    @DeleteMapping("/api/teams/{teamId}/invites/{inviteId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                                      @PathVariable UUID inviteId) {
        invitations.revoke(principal.getUserId(), teamId, inviteId);
        return ResponseEntity.noContent().build();
    }
    private final InvitationService invitations;
    private final RequestRateLimits rateLimits;

    public InvitationController(InvitationService invitations, RequestRateLimits rateLimits) {
        this.invitations = invitations;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/api/teams/{teamId}/invites")
    public ResponseEntity<InviteResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                @PathVariable UUID teamId,
                                                @Valid @RequestBody CreateInviteRequest request) {
        rateLimits.invitationCreation(principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(invitations.create(principal.getUserId(), teamId, request));
    }

    @GetMapping("/api/invites/{token}")
    public InvitePreviewResponse preview(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String token) {
        return invitations.preview(principal.getUserId(), token);
    }

    @PostMapping("/api/invites/{token}/accept")
    public TeamResponse accept(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String token) {
        return invitations.accept(principal.getUserId(), token);
    }
}
