package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    public TimeEntryController(TimeEntryService entries) {
        this.entries = entries;
    }

    @GetMapping
    public TimeEntryHistoryResponse history(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam UUID teamId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return entries.history(principal.getUserId(), teamId, projectId, from, to, page, size);
    }

    @PostMapping
    public ResponseEntity<TimeEntryResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                   @Valid @RequestBody ManualTimeEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.createManual(principal.getUserId(), request));
    }
}
