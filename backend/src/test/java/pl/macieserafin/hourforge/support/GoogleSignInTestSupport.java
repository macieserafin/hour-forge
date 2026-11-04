package pl.macieserafin.hourforge.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Runs the application with the sign-in with Google switched on, against {@link FakeGoogle}. A browser goes through
 * the real chain: the start address, the consent at the stand-in, and the callback on the same session cookie.
 */
public abstract class GoogleSignInTestSupport extends ApiIntegrationTestSupport {
    protected static final FakeGoogle GOOGLE = new FakeGoogle();
    protected static final String START = "/api/auth/oauth2/authorization/google";
    protected static final String FRONTEND = "http://localhost:5173";

    @Autowired protected MockMvc mvc;

    @DynamicPropertySource
    static void google(DynamicPropertyRegistry registry) {
        registry.add("app.google.client-id", () -> FakeGoogle.CLIENT_ID);
        registry.add("app.google.client-secret", () -> FakeGoogle.CLIENT_SECRET);
        registry.add("app.google.redirect-uri", () -> "http://localhost/api/auth/oauth2/callback/google");
        registry.add("app.google.issuer-uri", GOOGLE::issuer);
    }

    @BeforeEach
    void resetGoogle() {
        GOOGLE.reset();
    }

    /** The start of the sign-in; {@code query} is e.g. {@code mode=login&rememberMe=true}. */
    protected ResultActions start(BrowserSession browser, String query) throws Exception {
        return browser.perform(get(URI.create(START + (query.isEmpty() ? "" : "?" + query))));
    }

    /** The whole sign-in as {@link FakeGoogle#account}: start, consent at the stand-in, callback. Returns the callback. */
    protected ResultActions signInWithGoogle(BrowserSession browser, String query) throws Exception {
        var authorization = start(browser, query).andReturn().getResponse().getRedirectedUrl();
        return browser.perform(get(GOOGLE.consent(authorization)));
    }

    protected ResultActions signInWithGoogle(BrowserSession browser) throws Exception {
        return signInWithGoogle(browser, "mode=login");
    }
}
