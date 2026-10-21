package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.time.LocalDate;
import java.util.UUID;

/** Time corrections: the owner or an admin of a team reads, adds, changes and removes a member's entries. */
@RestController
@RequestMapping("/api/teams/{teamId}")
public class TimeEntryCorrectionController {
    private final TimeEntryService entries;

    public TimeEntryCorrectionController(TimeEntryService entries) {
        this.entries = entries;
    }

    @GetMapping("/members/{memberId}/time-entries")
    public TimeEntryHistoryResponse history(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
            @PathVariable UUID memberId, @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean undated, @RequestParam(required = false) String q) {
        return entries.memberHistory(principal.getUserId(), teamId, memberId, projectId, from, to, page, size, undated, q);
    }

    @PostMapping("/members/{memberId}/time-entries")
    public ResponseEntity<TimeEntryResponse> create(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
            @PathVariable UUID memberId, @Valid @RequestBody UpdateTimeEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.createFor(principal.getUserId(), teamId, memberId, request));
    }

    @PatchMapping("/time-entries/{entryId}")
    public TimeEntryResponse update(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
            @PathVariable UUID entryId, @Valid @RequestBody UpdateTimeEntryRequest request) {
        return entries.correct(principal.getUserId(), teamId, entryId, request);
    }

    @DeleteMapping("/time-entries/{entryId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId, @PathVariable UUID entryId) {
        entries.deleteFor(principal.getUserId(), teamId, entryId);
        return ResponseEntity.noContent().build();
    }
}
