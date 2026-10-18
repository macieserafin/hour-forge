package pl.macieserafin.hourforge.timeentry;

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
@RequestMapping("/api/time-entries")
public class TimeEntryController {
    private final TimeEntryService entries;

    public TimeEntryController(TimeEntryService entries) {
        this.entries = entries;
    }

    @PostMapping
    public ResponseEntity<TimeEntryResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                   @Valid @RequestBody ManualTimeEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.createManual(principal.getUserId(), request));
    }
}
