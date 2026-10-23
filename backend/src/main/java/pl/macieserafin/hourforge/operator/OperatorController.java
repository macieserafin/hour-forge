package pl.macieserafin.hourforge.operator;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.util.UUID;

/** The instance operator panel. Every endpoint checks the operator list itself; being signed in is not enough. */
@RestController
@RequestMapping("/api/operator")
public class OperatorController {
    public record BlockRequest(String reason) {
    }

    public record AnnouncementRequest(String message, Announcement.Level level) {
    }

    private final OperatorAccess access;
    private final OperatorService operator;

    public OperatorController(OperatorAccess access, OperatorService operator) {
        this.access = access;
        this.operator = operator;
    }

    @GetMapping("/stats")
    public OperatorService.Stats stats(@AuthenticationPrincipal AppUserPrincipal principal) {
        access.require(principal.getUserId());
        return operator.stats();
    }

    @GetMapping("/accounts")
    public OperatorService.Page<OperatorService.Account> accounts(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                  @RequestParam(required = false) String query,
                                                                  @RequestParam(defaultValue = "0") int page) {
        access.require(principal.getUserId());
        return operator.accounts(query, page);
    }

    @GetMapping("/teams")
    public OperatorService.Page<OperatorService.TeamRow> teams(@AuthenticationPrincipal AppUserPrincipal principal,
                                                               @RequestParam(required = false) String query,
                                                               @RequestParam(defaultValue = "0") int page) {
        access.require(principal.getUserId());
        return operator.teams(query, page);
    }

    @PostMapping("/accounts/{id}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id, @RequestBody BlockRequest request) {
        access.require(principal.getUserId());
        operator.block(principal.getUserId(), id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{id}/unblock")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        access.require(principal.getUserId());
        operator.unblock(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{id}/two-factor/disable")
    public ResponseEntity<Void> turnOffTwoFactor(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        access.require(principal.getUserId());
        operator.turnOffTwoFactor(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        access.require(principal.getUserId());
        operator.deleteAccount(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/teams/{id}")
    public ResponseEntity<Void> deleteTeam(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        access.require(principal.getUserId());
        operator.deleteTeam(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/announcement")
    public OperatorService.AnnouncementView announce(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody AnnouncementRequest request) {
        access.require(principal.getUserId());
        return operator.announce(principal.getUserId(), request.message(), request.level());
    }

    @DeleteMapping("/announcement")
    public ResponseEntity<Void> clearAnnouncement(@AuthenticationPrincipal AppUserPrincipal principal) {
        access.require(principal.getUserId());
        operator.clearAnnouncement(principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
