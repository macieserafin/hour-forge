package pl.macieserafin.hourforge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.operator.OperatorAccess;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.user.UserRepository;

import java.util.UUID;

@RestController
public class AuthController {
    private final UserRepository users;
    private final OperatorAccess operators;

    public AuthController(UserRepository users, OperatorAccess operators) {
        this.users = users;
        this.operators = operators;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getToken(), csrf.getHeaderName(), csrf.getParameterName());
    }

    @GetMapping({"/me", "/api/me"})
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        var user = users.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return MeResponse.of(user, operators.isOperator(user));
    }

    public record CsrfResponse(String token, String headerName, String parameterName) {
    }

    /** The signed-in person; {@code operator} only decides whether the operator panel is offered. */
    public record MeResponse(UUID id, String email, String displayName, boolean operator, java.time.Instant avatarUpdatedAt) {
        public static MeResponse of(pl.macieserafin.hourforge.user.User user, boolean operator) {
            return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), operator, user.getAvatarUpdatedAt());
        }
    }
}
