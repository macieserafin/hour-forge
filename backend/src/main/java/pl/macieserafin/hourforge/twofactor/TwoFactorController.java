package pl.macieserafin.hourforge.twofactor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.util.Map;

@RestController
public class TwoFactorController {
    public record CodeRequest(String code) {
    }

    public record EnableRequest(String code, String currentPassword) {
    }

    public record PasswordRequest(String currentPassword) {
    }

    private final TwoFactorService twoFactor;
    private final TwoFactorSignIn signIn;

    public TwoFactorController(TwoFactorService twoFactor, TwoFactorSignIn signIn) {
        this.twoFactor = twoFactor;
        this.signIn = signIn;
    }

    /** The second step of signing in; open to the session that passed the password step. */
    @PostMapping("/api/auth/two-factor")
    public ResponseEntity<?> answer(@RequestBody CodeRequest request, HttpServletRequest http, HttpServletResponse response) {
        return switch (signIn.answer(http, response, request == null ? null : request.code())) {
            case SIGNED_IN -> ResponseEntity.noContent().build();
            case WRONG_CODE -> ResponseEntity.badRequest().body(Map.of("code", "INVALID_CODE"));
            case START_AGAIN -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "SIGN_IN_AGAIN"));
        };
    }

    @GetMapping("/api/account/two-factor")
    public TwoFactorService.Status status(@AuthenticationPrincipal AppUserPrincipal principal) {
        return twoFactor.status(principal.getUserId());
    }

    @PostMapping("/api/account/two-factor/setup")
    public ResponseEntity<TwoFactorService.Setup> setup(@AuthenticationPrincipal AppUserPrincipal principal) {
        return noStore(twoFactor.startSetup(principal.getUserId()));
    }

    @PostMapping("/api/account/two-factor/enable")
    public ResponseEntity<TwoFactorService.BackupCodes> enable(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody EnableRequest request) {
        return noStore(twoFactor.enable(principal.getUserId(), request.code(), request.currentPassword()));
    }

    @PostMapping("/api/account/two-factor/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody PasswordRequest request) {
        twoFactor.disable(principal.getUserId(), request.currentPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/account/two-factor/backup-codes")
    public ResponseEntity<TwoFactorService.BackupCodes> renew(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody PasswordRequest request) {
        return noStore(twoFactor.renewBackupCodes(principal.getUserId(), request.currentPassword()));
    }

    // Secrets and backup codes must not stay in any cache.
    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
