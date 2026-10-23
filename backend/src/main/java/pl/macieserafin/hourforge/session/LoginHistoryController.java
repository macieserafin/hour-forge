package pl.macieserafin.hourforge.session;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
public class LoginHistoryController {
    private final LoginHistory history;

    public LoginHistoryController(LoginHistory history) {
        this.history = history;
    }

    @GetMapping("/api/account/logins")
    public LoginHistory.History list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return history.list(principal.getUserId());
    }
}
