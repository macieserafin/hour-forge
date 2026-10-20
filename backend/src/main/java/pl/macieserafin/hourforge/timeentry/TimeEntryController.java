package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/time-entries")
public class TimeEntryController {
    private final TimeEntryService entries;
    private final IssueLinks links;

    public TimeEntryController(TimeEntryService entries, IssueLinks links) {
        this.entries = entries;
        this.links = links;
    }

    @GetMapping
    public TimeEntryHistoryResponse history(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam UUID teamId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean undated,
            @RequestParam(required = false) String q) {
        return entries.history(principal.getUserId(), teamId, projectId, from, to, page, size, undated, q);
    }

    @PatchMapping("/{id}")
    public TimeEntryResponse update(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateTimeEntryRequest request) {
        return entries.update(principal.getUserId(), id, request, links.forUpdate(principal.getUserId(), id, request.jiraIssueId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        entries.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<TimeEntryResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                   @Valid @RequestBody ManualTimeEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.createManual(principal.getUserId(), request,
                links.forNewEntry(principal.getUserId(), request.teamId(), request.jiraIssueId())));
    }
}
