package pl.macieserafin.hourforge.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/account/sessions")
public class SessionController {
    private final DeviceSessions devices;

    public SessionController(DeviceSessions devices) {
        this.devices = devices;
    }

    @GetMapping
    public List<DeviceSessions.DeviceSession> list(@AuthenticationPrincipal AppUserPrincipal principal, HttpServletRequest request) {
        return devices.list(principal.getUserId(), currentSessionId(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> end(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String id,
                                    HttpServletRequest request) {
        devices.end(principal.getUserId(), id, currentSessionId(request));
        return ResponseEntity.noContent().build();
    }

    private static String currentSessionId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? null : session.getId();
    }
}
