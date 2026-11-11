package pl.macieserafin.hourforge.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.auth.AuthController.MeResponse;
import pl.macieserafin.hourforge.identity.SignInMethodLookup;
import pl.macieserafin.hourforge.operator.OperatorAccess;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.session.DeviceSessions;
import pl.macieserafin.hourforge.session.SessionConfig;

import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService account;
    private final AccountDataService data;
    private final DeviceSessions devices;
    private final OperatorAccess operators;
    private final SignInMethodLookup signInMethods;

    public AccountController(AccountService account, AccountDataService data, DeviceSessions devices, OperatorAccess operators,
                             SignInMethodLookup signInMethods) {
        this.operators = operators;
        this.signInMethods = signInMethods;
        this.account = account;
        this.data = data;
        this.devices = devices;
    }

    @PatchMapping("/profile")
    public MeResponse updateProfile(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @Valid @RequestBody ProfileRequest request) {
        var user = account.updateDisplayName(principal.getUserId(), request.displayName());
        return MeResponse.of(user, operators.isOperator(user), signInMethods);
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @RequestBody PasswordChangeRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse response) {
        int version = account.changePassword(principal.getUserId(), request);
        // The other devices are signed out; removed before the id changes, while the stored row still has the old id.
        devices.endAllExcept(principal.getUserId(), httpRequest.getSession().getId());
        // Keep this device signed in: a fresh session id and a principal carrying the new version.
        httpRequest.changeSessionId();
        var refreshed = new AppUserPrincipal(principal.getUserId(), principal.getUsername(), "", version, principal.isEmailVerified());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(refreshed, null, List.of());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, httpRequest, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<AccountExport> export(@AuthenticationPrincipal AppUserPrincipal principal) {
        var export = data.export(principal.getUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hourforge-export-" + export.exportedAt().toString().substring(0, 10) + ".json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(export);
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody DeleteAccountRequest request,
                                       Authentication authentication, HttpServletRequest httpRequest, HttpServletResponse response) {
        if (request.byEmail()) {
            // The link is sent; the account and this session stay until it is opened.
            data.requestDeletionByEmail(principal.getUserId());
            return ResponseEntity.accepted().build();
        }
        data.deleteAccount(principal.getUserId(), request.currentPassword());
        devices.endAllExcept(principal.getUserId(), null);
        new SecurityContextLogoutHandler().logout(httpRequest, response, authentication);
        new CookieClearingLogoutHandler(SessionConfig.COOKIE_NAME).logout(httpRequest, response, authentication);
        return ResponseEntity.noContent().build();
    }

    /** Public (the link may be opened signed out or in another browser); CSRF as for every POST. Every session ends. */
    @PostMapping("/delete/confirm")
    public ResponseEntity<Void> confirmDeletion(@RequestBody DeletionConfirmation request) {
        var userId = data.confirmDeletion(request.token());
        devices.endAllExcept(userId, null);
        return ResponseEntity.noContent().build();
    }

    public record DeletionConfirmation(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String token) {
        @Override
        public String toString() {
            return "DeletionConfirmation[redacted]";
        }
    }

    @PostMapping("/sign-out-everywhere")
    public ResponseEntity<Void> signOutEverywhere(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  Authentication authentication, HttpServletRequest request,
                                                  HttpServletResponse response) {
        account.signOutEverywhere(principal.getUserId());
        devices.endAllExcept(principal.getUserId(), null);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler(SessionConfig.COOKIE_NAME).logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
