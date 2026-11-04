package pl.macieserafin.hourforge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.identity.SignInMethodLookup;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.operator.OperatorAccess;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

@RestController
public class AuthController {
    private final UserRepository users;
    private final OperatorAccess operators;
    private final SignInMethodLookup signInMethods;

    public AuthController(UserRepository users, OperatorAccess operators, SignInMethodLookup signInMethods) {
        this.users = users;
        this.operators = operators;
        this.signInMethods = signInMethods;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getToken(), csrf.getHeaderName(), csrf.getParameterName());
    }

    @GetMapping({"/me", "/api/me"})
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        var user = users.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return MeResponse.of(user, operators.isOperator(user), signInMethods);
    }

    public record CsrfResponse(String token, String headerName, String parameterName) {
    }

    /**
     * The signed-in person; {@code operator} only decides whether the operator panel is offered. {@code hasPassword}
     * is false for an account that signs in only through Google; {@code google} is null without a linked account.
     */
    public record MeResponse(UUID id, String email, String displayName, boolean operator, Instant avatarUpdatedAt,
                             boolean hasPassword, GoogleLink google) {
        public static MeResponse of(pl.macieserafin.hourforge.user.User user, boolean operator, SignInMethodLookup signInMethods) {
            return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), operator, user.getAvatarUpdatedAt(),
                    signInMethods.hasPassword(user.getId()), signInMethods.google(user.getId()).map(GoogleLink::of).orElse(null));
        }
    }

    /** The linked Google account as shown in the account settings; {@code linked} is always true when present. */
    public record GoogleLink(boolean linked, String email, Instant linkedAt) {
        static GoogleLink of(UserIdentity identity) {
            return new GoogleLink(true, identity.getEmail(), identity.getLinkedAt());
        }
    }
}
