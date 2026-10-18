package pl.macieserafin.hourforge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.user.UserRepository;

import java.util.UUID;

@RestController
public class AuthController {
    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getToken(), csrf.getHeaderName(), csrf.getParameterName());
    }

    @GetMapping({"/me", "/api/me"})
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        var user = users.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    public record CsrfResponse(String token, String headerName, String parameterName) {
    }

    public record MeResponse(UUID id, String email, String displayName) {
    }
}
