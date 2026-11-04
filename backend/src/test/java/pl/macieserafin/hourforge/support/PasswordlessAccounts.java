package pl.macieserafin.hourforge.support;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.user.User;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Accounts that sign in only through a linked provider: a confirmed user whose credential row keeps the session
 * version without a password. Until the Google sign-in exists, {@link #signedIn} stands in for its session.
 */
public final class PasswordlessAccounts {
    private PasswordlessAccounts() {
    }

    public static User create(TestData fixtures, UserCredentialRepository credentials, String email, String name) {
        var user = fixtures.user(email, name);
        credentials.saveAndFlush(UserCredential.withoutPassword(user));
        return user;
    }

    /** The principal the application builds for the account, with its current session version, on one request. */
    public static RequestPostProcessor signedIn(UserDetailsService userDetails, User user) {
        return user(userDetails.loadUserByUsername(user.getEmail()));
    }
}
