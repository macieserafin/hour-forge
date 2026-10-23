package pl.macieserafin.hourforge.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.session.SessionConfig;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * One browser in MockMvc. Sessions are stored by Spring Session and found by their cookie (a MockHttpSession is
 * ignored), so this keeps the session cookie between requests the way a real browser does.
 */
public final class BrowserSession {
    private final MockMvc mvc;
    private Cookie cookie;
    private String userAgent;
    private String address;

    public BrowserSession(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Sends this user agent and client address with every request, like one device would. */
    public BrowserSession device(String userAgent, String address) {
        this.userAgent = userAgent;
        this.address = address;
        return this;
    }

    public ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        if (cookie != null) request.cookie(cookie);
        if (userAgent != null) request.header("User-Agent", userAgent);
        if (address != null) request.with(mock -> { mock.setRemoteAddr(address); return mock; });
        var result = mvc.perform(request);
        var issued = result.andReturn().getResponse().getCookie(SessionConfig.COOKIE_NAME);
        if (issued != null) cookie = issued.getMaxAge() == 0 || issued.getValue().isEmpty() ? null : issued;
        return result;
    }

    public String csrfToken() throws Exception {
        return JsonPath.read(perform(get("/api/auth/csrf")).andReturn().getResponse().getContentAsString(), "$.token");
    }

    /** Signs in through the real login endpoint with a real CSRF token, as the frontend does. */
    public ResultActions login(String email, String password) throws Exception {
        return login(email, password, false);
    }

    public ResultActions login(String email, String password, boolean rememberMe) throws Exception {
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-CSRF-TOKEN", csrfToken()).param("email", email).param("password", password);
        if (rememberMe) request.param("rememberMe", "true");
        return perform(request);
    }

    /** A state-changing request with this browser's CSRF token. */
    public ResultActions send(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return perform(request.header("X-CSRF-TOKEN", csrfToken()));
    }

    /** The id of the current session, or null when the browser holds none. */
    public String sessionId() {
        return cookie == null ? null : new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    public Cookie cookie() {
        return cookie;
    }
}
