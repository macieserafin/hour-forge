package pl.macieserafin.hourforge.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.google.GoogleLinkException;
import pl.macieserafin.hourforge.google.GoogleLinkIntent;
import pl.macieserafin.hourforge.google.GoogleLinks;
import pl.macieserafin.hourforge.google.GoogleSignIn;
import pl.macieserafin.hourforge.google.GoogleSignInEnabled;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.session.DeviceSessions;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Linking and unlinking Google from the account page. Exists only while the sign-in with Google is on; without
 * it both addresses answer 404.
 */
@RestController
@RequestMapping("/api/account/google")
@Conditional(GoogleSignInEnabled.class)
public class GoogleLinkController {
    /** {@code currentPassword} is needed when the account has a password. */
    public record GoogleAccountRequest(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword) {
        @Override
        public String toString() {
            return "GoogleAccountRequest[redacted]";
        }
    }

    public record LinkStart(String authorizationUrl) {
    }

    private final GoogleLinks links;
    private final DeviceSessions devices;
    private final Clock clock;

    public GoogleLinkController(GoogleLinks links, DeviceSessions devices, Clock clock) {
        this.links = links;
        this.devices = devices;
        this.clock = clock;
    }

    /** The browser then goes to the returned address with a full page load. */
    @PostMapping("/link")
    public LinkStart link(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody(required = false) GoogleAccountRequest body,
                          HttpServletRequest request) {
        var version = links.prepareLink(principal.getUserId(), body == null ? null : body.currentPassword());
        GoogleLinkIntent.store(request, principal.getUserId(), version, clock.instant());
        return new LinkStart(GoogleSignIn.START_PATH + "?mode=link");
    }

    /** Ends the other sessions and keeps this one with the new credential version, like a password change. */
    @PostMapping("/unlink")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody(required = false) GoogleAccountRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        var version = links.unlink(principal.getUserId(), body == null ? null : body.currentPassword());
        devices.endAllExcept(principal.getUserId(), request.getSession().getId());
        request.changeSessionId();
        var refreshed = new AppUserPrincipal(principal.getUserId(), principal.getUsername(), "", version, principal.isEmailVerified(),
                principal.isBlocked());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(refreshed, null, List.of()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(GoogleLinkException.class)
    ResponseEntity<Map<String, String>> refused(GoogleLinkException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("code", exception.getCode()));
    }
}
