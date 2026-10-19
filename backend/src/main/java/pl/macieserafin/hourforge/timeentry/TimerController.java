package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/timer")
public class TimerController {
    private final TimeEntryService entries;

    public TimerController(TimeEntryService entries) {
        this.entries = entries;
    }

    @PostMapping("/start")
    public ResponseEntity<TimeEntryResponse> start(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  @Valid @RequestBody StartTimerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.start(principal.getUserId(), request));
    }

    @PostMapping("/stop")
    public TimeEntryResponse stop(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @Valid @RequestBody StopTimerRequest request) {
        return entries.stop(principal.getUserId(), request);
    }

    @GetMapping("/active")
    public ResponseEntity<TimeEntryResponse> active(@AuthenticationPrincipal AppUserPrincipal principal) {
        return entries.active(principal.getUserId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/pause")
    public TimeEntryResponse pause(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @Valid @RequestBody TimerStateRequest request) {
        return entries.pause(principal.getUserId(), request);
    }

    @PostMapping("/resume")
    public TimeEntryResponse resume(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @Valid @RequestBody TimerStateRequest request) {
        return entries.resume(principal.getUserId(), request);
    }
}
