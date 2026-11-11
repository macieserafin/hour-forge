package pl.macieserafin.hourforge.google.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.identity.IdentityProvider;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.identity.UserIdentityRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.RequestRateLimits;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.support.FakeGoogle;
import pl.macieserafin.hourforge.support.FakeGoogle.Account;
import pl.macieserafin.hourforge.support.GoogleSignInTestSupport;
import pl.macieserafin.hourforge.twofactor.Totp;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in and sign-up with Google against {@link FakeGoogle}. */
@ExtendWith(OutputCaptureExtension.class)
class GoogleSignInIT extends GoogleSignInTestSupport {
    private static final String PASSWORD = "Test-password-2026";
    private static final String AUTHORIZATION_REQUEST_ATTRIBUTE =
            "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST";

    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;
    @Autowired RequestRateLimits rateLimits;
    @Autowired ClientRegistrationRepository registrations;
    @Autowired jakarta.persistence.EntityManager entityManager;

    // --- The way to Google and back -------------------------------------------------------------------------------

    @Test
    void theStartSendsTheBrowserToGoogleWithStateNoncePkceAndTheOpenIdScopes() throws Exception {
        var browser = new BrowserSession(mvc);
        var location = start(browser, "mode=login").andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith(GOOGLE.issuer() + "/authorize?");
        var parameters = FakeGoogle.query(URI.create(location).getRawQuery());
        assertThat(parameters).containsEntry("response_type", "code").containsEntry("client_id", FakeGoogle.CLIENT_ID)
                .containsEntry("scope", "openid email profile").containsEntry("code_challenge_method", "S256")
                .containsEntry("redirect_uri", "http://localhost/api/auth/oauth2/callback/google");
        assertThat(parameters.get("state")).hasSizeGreaterThan(20);
        assertThat(parameters.get("nonce")).hasSizeGreaterThan(20);
        assertThat(parameters.get("code_challenge")).matches("[A-Za-z0-9_-]{43}");
        assertThat(registrations.findByRegistrationId("google").getClientSettings().isRequireProofKey()).isTrue();
        // Spring's default addresses stay closed: only the addresses under /api are used.
        browser.perform(get("/oauth2/authorization/google")).andExpect(status().isUnauthorized());
    }

    @Test
    void theAuthorizationRequestWaitsInTheSessionStoredInTheDatabaseAndIsUsedOnce() throws Exception {
        var browser = new BrowserSession(mvc);
        var location = start(browser, "mode=login").andReturn().getResponse().getRedirectedUrl();
        assertThat(jdbc.queryForObject("""
                select count(*) from spring_session_attributes a join spring_session s on s.primary_id = a.session_primary_id
                where s.session_id = ? and a.attribute_name = ?""", Integer.class, browser.sessionId(), AUTHORIZATION_REQUEST_ATTRIBUTE)).isOne();

        var callback = GOOGLE.consent(location);
        var waitingSession = browser.sessionId();
        // Each request reads the session from the database, so the request was serialized and read back.
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        // Signing in gives the session a new id (no fixation through the session that waited for Google).
        assertThat(browser.sessionId()).isNotNull().isNotEqualTo(waitingSession);
        assertThat(sessions.findById(waitingSession)).isNull();
        assertThat(GOOGLE.verifiedProofKeys.get()).isOne();

        // The same answer again (a replay) no longer finds a waiting request.
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
    }

    @Test
    void aCallbackWithAStateFromNoStartIsRefused() throws Exception {
        var browser = new BrowserSession(mvc);
        start(browser, "mode=login");
        browser.perform(get("/api/auth/oauth2/callback/google").param("code", "fake-code-1").param("state", "forged-state"))
                .andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        assertThat(GOOGLE.tokenCalls.get()).isZero();
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anyOtherAddressUnderTheGooglePrefixesIsRefusedWithoutAnError() throws Exception {
        var browser = new BrowserSession(mvc);
        browser.perform(get("/api/auth/oauth2/authorization/github")).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        browser.perform(get("/api/auth/oauth2/authorization/google/extra")).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        // Other methods: a POST is stopped by the CSRF protection before, one with a token by the guard.
        browser.perform(post("/api/auth/oauth2/authorization/google")).andExpect(status().isForbidden());
        browser.send(post("/api/auth/oauth2/authorization/google")).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));

        // A real answer from Google sent to another callback suffix is refused before Spring reads it.
        var callback = GOOGLE.consent(start(browser, "mode=login").andReturn().getResponse().getRedirectedUrl());
        browser.perform(get(URI.create("/api/auth/oauth2/callback/other?" + callback.getRawQuery())))
                .andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        assertThat(GOOGLE.tokenCalls.get()).isZero();
        // The waiting request was left alone and the right callback still works.
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
    }

    @Test
    void addressesUnderTheGooglePrefixesCountTowardsTheLimit() throws Exception {
        var browser = new BrowserSession(mvc).device(null, "198.51.100.9");
        for (int i = 0; i < 30; i++) browser.perform(get("/api/auth/oauth2/callback/other")).andExpect(status().isFound());
        browser.perform(get("/api/auth/oauth2/callback/other")).andExpect(header().exists("Retry-After"));
    }

    // --- A new account ----------------------------------------------------------------------------------------

    @Test
    void aNewGmailAccountIsCreatedConfirmedWithoutAPasswordAndSignedIn() throws Exception {
        GOOGLE.account = new Account("sub-new-1", "New.Person@gmail.com", true, null, "  Nowa Osoba  ");
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser).andExpect(status().isFound()).andExpect(redirectedUrl(FRONTEND + "/dashboard"))
                .andExpect(header().string("Cache-Control", "no-store"));

        var user = users.findByEmailIgnoreCase("new.person@gmail.com").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("new.person@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("Nowa Osoba");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(credentials.findById(user.getId()).orElseThrow().hasPassword()).isFalse();
        var identity = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-new-1").orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(user.getId());
        assertThat(identity.getEmail()).isEqualTo("new.person@gmail.com");
        assertThat(identity.getLastUsedAt()).isNotNull();

        browser.perform(get("/api/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.person@gmail.com"))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.google.linked").value(true));
        assertThat(mail.sent()).isEmpty();
        entityManager.flush();
        assertThat(jdbc.queryForList("select method from login_events where user_id = ?", String.class, user.getId())).containsExactly("GOOGLE");
    }

    @Test
    void aWorkspaceAccountIsConfirmedAndTheNameFallsBackToTheAddress() throws Exception {
        GOOGLE.account = new Account("sub-workspace", "anna@firma.example", true, "firma.example", null);
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        var user = users.findByEmailIgnoreCase("anna@firma.example").orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getDisplayName()).isEqualTo("anna");
    }

    @Test
    void anAddressGoogleDoesNotVouchForCreatesAnUnconfirmedAccountThatGetsOurLink() throws Exception {
        GOOGLE.account = new Account("sub-other-domain", "jan@poczta.example", true, null, "Jan");
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/login?google=EMAIL_NOT_VERIFIED"));

        var user = users.findByEmailIgnoreCase("jan@poczta.example").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(identities.findByUserIdAndProvider(user.getId(), IdentityProvider.GOOGLE)).isPresent();
        assertThat(mail.last().to()).isEqualTo("jan@poczta.example");
        assertThat(mail.last().subject()).isEqualTo("Potwierdź adres e-mail w HourForge");
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());

        // Back again before the link was opened, still not signed in.
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/login?google=EMAIL_NOT_VERIFIED"));
    }

    @Test
    void emailVerifiedSentAsTextIsUnderstood() throws Exception {
        GOOGLE.account = new Account("sub-text-flag", "text.flag@gmail.com", "true", null, "Text");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        assertThat(users.findByEmailIgnoreCase("text.flag@gmail.com")).isPresent();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"unverified@gmail.com,false", "unverified@gmail.com,null", "null,true"})
    void noAccountIsCreatedWithoutAnAddressGoogleVerified(String email, Boolean verified) throws Exception {
        GOOGLE.account = new Account("sub-unverified", email, verified, null, "Someone");
        var before = users.count();
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_EMAIL_UNVERIFIED"));

        assertThat(users.count()).isEqualTo(before);
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-unverified")).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void newAccountsShareTheRegistrationLimitOfTheAddress() throws Exception {
        for (int i = 0; i < 5; i++) rateLimits.registration("203.0.113.40");
        GOOGLE.account = Account.gmail("sub-limited", "limited@gmail.com");

        signInWithGoogle(new BrowserSession(mvc).device(null, "203.0.113.40"))
                .andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));

        assertThat(users.findByEmailIgnoreCase("limited@gmail.com")).isEmpty();
        signInWithGoogle(new BrowserSession(mvc).device(null, "203.0.113.41")).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
    }

    // --- Coming back ------------------------------------------------------------------------------------------

    @Test
    void comingBackFindsTheAccountByTheSubjectOnlyAndKeepsTheLocalAddress() throws Exception {
        GOOGLE.account = Account.gmail("sub-returning", "returning@gmail.com");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        var user = users.findByEmailIgnoreCase("returning@gmail.com").orElseThrow();
        var firstUse = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-returning").orElseThrow().getLastUsedAt();
        var accounts = users.count();

        // The address changed at Google: the same sub still signs in to the same account.
        GOOGLE.account = Account.gmail("sub-returning", "renamed@gmail.com");
        var browser = new BrowserSession(mvc);
        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/dashboard"));

        assertThat(users.count()).isEqualTo(accounts);
        browser.perform(get("/api/me")).andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("returning@gmail.com"))
                .andExpect(jsonPath("$.google.email").value("renamed@gmail.com"));
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-returning").orElseThrow().getLastUsedAt())
                .isAfterOrEqualTo(firstUse);
    }

    @Test
    void theSubjectIsComparedExactly() throws Exception {
        GOOGLE.account = Account.gmail("Sub-Case", "case@gmail.com");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));

        // Another Google account whose sub differs only in case, with the same address: not the same person.
        GOOGLE.account = Account.gmail("sub-case", "case@gmail.com");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_ACCOUNT_EXISTS"));
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-case")).isEmpty();
    }

    // --- An address that already has an account --------------------------------------------------------------

    @Test
    void anAddressWithAPasswordAccountIsNeitherLinkedNorTakenAndItsOwnerIsTold() throws Exception {
        var owner = passwordAccount("Owner@Gmail.com", "Owner");
        GOOGLE.account = Account.gmail("sub-intruder", "owner@gmail.com");
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser, "mode=login&returnTo=%2Finvite%2Fabc_DEF-123")
                .andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_ACCOUNT_EXISTS&returnTo=%2Finvite%2Fabc_DEF-123"));

        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-intruder")).isEmpty();
        assertThat(mail.last().to()).isEqualTo("owner@gmail.com");
        assertThat(mail.last().subject()).isEqualTo("Ktoś próbował zalogować się przez Google na Twój adres");
        assertThat(mail.last().body()).contains("/account#google").doesNotContain("sub-intruder");
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
        new BrowserSession(mvc).login("owner@gmail.com", PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void anAddressAlreadyLinkedToAnotherGoogleAccountIsNotTakenEither() throws Exception {
        GOOGLE.account = Account.gmail("sub-first", "shared@gmail.com");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/dashboard"));

        GOOGLE.account = Account.gmail("sub-second", "shared@gmail.com");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_ACCOUNT_EXISTS"));
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-second")).isEmpty();
    }

    // --- The order of the checks ---------------------------------------------------------------------------------

    @Test
    void aBlockedAccountIsToldSoAndNotSignedIn() throws Exception {
        var user = linkedAccount("blocked@gmail.com", "sub-blocked", true);
        block(user);
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/login?google=ACCOUNT_BLOCKED"));

        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertThat(sessions.findByPrincipalName(user.getId().toString())).isEmpty();
    }

    @Test
    void aBlockedAccountIsToldSoBeforeItsUnconfirmedAddress() throws Exception {
        var user = linkedAccount("blocked-unconfirmed@example.com", "sub-blocked-unconfirmed", false);
        block(user);
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/login?google=ACCOUNT_BLOCKED"));
    }

    @Test
    void aLinkedAccountWithAnUnconfirmedAddressIsAskedToConfirmIt() throws Exception {
        var user = linkedAccount("unconfirmed@example.com", "sub-unconfirmed", false);
        var browser = new BrowserSession(mvc);
        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/login?google=EMAIL_NOT_VERIFIED"));
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertThat(sessions.findByPrincipalName(user.getId().toString())).isEmpty();
    }

    // --- Two-step sign-in -------------------------------------------------------------------------------------

    @Test
    void anAccountWithTwoStepSignInAsksForTheCodeAfterGoogleAndKeepsTheReturnAddress() throws Exception {
        var user = passwordAccount("two-step@gmail.com", "Two Step");
        var secret = turnOnTwoFactor("two-step@gmail.com");
        link(user, "sub-two-step");
        GOOGLE.account = Account.gmail("sub-two-step", "two-step@gmail.com");
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser, "mode=login&rememberMe=true&returnTo=%2Finvite%2Fteam-invite")
                .andExpect(redirectedUrl(FRONTEND + "/login?step=two-factor&returnTo=%2Finvite%2Fteam-invite"));
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());

        answer(browser, "000000").andExpect(status().isBadRequest());
        var response = answer(browser, Totp.code(secret, Totp.step(Instant.now()) + 1)).andExpect(status().isNoContent())
                .andReturn().getResponse();

        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId().toString()));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("SESSION=") && cookie.contains("Max-Age=2592000"));
        // Both the wrong code and the sign-in are recorded as made with Google.
        entityManager.flush();
        assertThat(jdbc.queryForList("select outcome || ':' || method from login_events where user_id = ? order by occurred_at desc limit 2",
                String.class, user.getId())).containsExactly("SUCCESS:GOOGLE", "FAILED:GOOGLE");
    }

    // --- Where the browser goes -----------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "%2Finvite%2Fabc_DEF-123, /invite/abc_DEF-123",
            "%2Fprojects, /projects",
            "%2Ftime%3Fdate%3D2026-11-04, /time?date=2026-11-04",
            "https%3A%2F%2Fevil.example%2Fdashboard, /dashboard",
            "%2F%2Fevil.example, /dashboard",
            "%2F%5Cevil.example, /dashboard",
            "%2Faccount, /dashboard",
            "%2Finvite%2F..%2Foperator, /dashboard",
            "%2Fdashboard%23x, /dashboard"})
    void theReturnAddressIsCheckedOnTheServer(String returnTo, String expected) throws Exception {
        GOOGLE.account = Account.gmail("sub-return-" + Math.abs(returnTo.hashCode()), "return" + Math.abs(returnTo.hashCode()) + "@gmail.com");
        signInWithGoogle(new BrowserSession(mvc), "mode=login&returnTo=" + returnTo).andExpect(redirectedUrl(FRONTEND + expected));
    }

    @Test
    void rememberMeKeepsTheSessionForThirtyDays() throws Exception {
        GOOGLE.account = Account.gmail("sub-remember", "remember@gmail.com");
        var browser = new BrowserSession(mvc);
        var response = signInWithGoogle(browser, "mode=login&rememberMe=true").andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("SESSION=") && cookie.contains("Max-Age=2592000"));
        assertThat(sessions.findById(browser.sessionId()).getMaxInactiveInterval()).isEqualTo(Duration.ofDays(30));

        var forgetful = new BrowserSession(mvc);
        var plain = signInWithGoogle(forgetful).andReturn().getResponse();
        assertThat(plain.getHeaders("Set-Cookie")).noneMatch(cookie -> cookie.contains("Max-Age="));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mode=link", "mode=other", "mode=LOGIN"})
    void unknownModesAndALinkWithoutItsIntentDoNotLeaveForGoogle(String query) throws Exception {
        var expected = query.equals("mode=link") ? "/account?google=LINK_EXPIRED#google" : "/login?google=GOOGLE_FAILED";
        start(new BrowserSession(mvc), query).andExpect(redirectedUrl(FRONTEND + expected));
        assertThat(GOOGLE.authorizations()).isEmpty();
    }

    // --- Cancelled or broken ----------------------------------------------------------------------------------

    @Test
    void cancellingAtGoogleSaysSo() throws Exception {
        GOOGLE.fault = FakeGoogle.Fault.CANCEL_CONSENT;
        var browser = new BrowserSession(mvc);
        signInWithGoogle(browser, "mode=login&returnTo=%2Fprojects")
                .andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_CANCELLED&returnTo=%2Fprojects"));
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = FakeGoogle.Fault.class, names = {"WRONG_AUDIENCE", "WRONG_ISSUER", "UNKNOWN_KEY", "WRONG_NONCE", "NO_NONCE",
            "EXPIRED", "ACCESS_TOKEN_TYPE", "TOKEN_SERVER_ERROR", "PROVIDER_ERROR"})
    void anIdTokenThatFailsAnyCheckOrAFailingGoogleSignsNobodyIn(FakeGoogle.Fault fault) throws Exception {
        GOOGLE.fault = fault;
        GOOGLE.account = Account.gmail("sub-fault", "fault@gmail.com");
        var before = users.count();
        var browser = new BrowserSession(mvc);

        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));

        assertThat(users.count()).isEqualTo(before);
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-fault")).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    // --- The session afterwards -----------------------------------------------------------------------------------

    @Test
    void theSessionHoldsTheAppPrincipalWithTheCredentialVersionAndNoGoogleToken() throws Exception {
        GOOGLE.account = Account.gmail("sub-session", "session@gmail.com");
        var browser = new BrowserSession(mvc);
        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        var user = users.findByEmailIgnoreCase("session@gmail.com").orElseThrow();

        SecurityContext context = sessions.findById(browser.sessionId()).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOfSatisfying(AppUserPrincipal.class, principal -> {
            assertThat(principal.getUserId()).isEqualTo(user.getId());
            assertThat(principal.getSessionVersion()).isZero();
            assertThat(principal.getPassword()).isNull();
        });
        assertThat(sessions.findByPrincipalName(user.getId().toString())).containsOnlyKeys(browser.sessionId());
        var stored = jdbc.queryForList("""
                select a.attribute_name, a.attribute_bytes from spring_session_attributes a
                join spring_session s on s.primary_id = a.session_primary_id where s.session_id = ?""", browser.sessionId());
        assertThat(stored).extracting(row -> row.get("attribute_name")).doesNotContain(AUTHORIZATION_REQUEST_ATTRIBUTE);
        var bytes = stored.stream().map(row -> new String((byte[]) row.get("attribute_bytes"), StandardCharsets.ISO_8859_1)).toList();
        assertThat(GOOGLE.issuedSecrets()).hasSize(3).allSatisfy(secret -> assertThat(bytes).noneMatch(value -> value.contains(secret)));
        assertThat(bytes).noneMatch(value -> value.contains("OidcUser") || value.contains("OAuth2AuthenticationToken"));
    }

    @Test
    void aGoogleSessionIsListedAmongTheDevicesAndEndsWithSignOutEverywhere() throws Exception {
        GOOGLE.account = Account.gmail("sub-devices", "devices@gmail.com");
        var browser = new BrowserSession(mvc).device("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0", "83.24.117.9");
        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/dashboard"));

        browser.perform(get("/api/account/sessions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
        browser.send(post("/api/account/sign-out-everywhere")).andExpect(status().isNoContent());
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aChangedCredentialVersionEndsTheGoogleSession() throws Exception {
        GOOGLE.account = Account.gmail("sub-version", "version@gmail.com");
        var browser = new BrowserSession(mvc);
        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        var user = users.findByEmailIgnoreCase("version@gmail.com").orElseThrow();

        jdbc.update("update user_credentials set session_version = session_version + 1 where user_id = ?", user.getId());

        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void usingTheAppAfterGoogleRecordsTheDayOfLastActivity() throws Exception {
        GOOGLE.account = Account.gmail("sub-active", "active@gmail.com");
        var browser = new BrowserSession(mvc);
        signInWithGoogle(browser);
        var user = users.findByEmailIgnoreCase("active@gmail.com").orElseThrow();
        jdbc.update("update users set last_active_at = null where id = ?", user.getId());

        browser.perform(get("/api/me")).andExpect(status().isOk());

        var recorded = jdbc.queryForObject("select last_active_at from users where id = ?", Timestamp.class, user.getId()).toInstant();
        assertThat(recorded).isEqualTo(Instant.now().truncatedTo(ChronoUnit.DAYS));
    }

    @Test
    void aSecondDeviceIsAnnouncedByEmailLikeAfterAPassword() throws Exception {
        GOOGLE.account = Account.gmail("sub-new-device", "new-device@gmail.com");
        signInWithGoogle(new BrowserSession(mvc).device("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0", "83.24.117.9"));
        assertThat(mail.sent()).isEmpty();

        signInWithGoogle(new BrowserSession(mvc).device("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "83.24.117.10"));
        assertThat(mail.last().subject()).isEqualTo("Nowe logowanie na Twoje konto HourForge");
    }

    /**
     * The return from Google replaces the CSRF token and stores the new one at once, so the first requests
     * for it in parallel all succeed. Real commits: the parallel requests run outside the test transaction.
     */
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void theReturnFromGoogleReplacesTheCsrfTokenAndParallelFirstRequestsForItSucceed() throws Exception {
        GOOGLE.account = Account.gmail("sub-csrf", "csrf.parallel@gmail.com");
        var browser = new BrowserSession(mvc);
        try {
            var before = browser.csrfToken();
            signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
            // One ordinary request first, as in CsrfConcurrencyIT: it once added the activity day to the session itself.
            // Nothing that checks a token may run before the parallel requests: a check stores a missing token itself.
            browser.perform(get("/api/me")).andExpect(status().isOk());

            var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
            var tokens = new java.util.concurrent.CopyOnWriteArrayList<String>();
            var statuses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            var start = new java.util.concurrent.CountDownLatch(1);
            try {
                for (int i = 0; i < 8; i++) {
                    statuses.add(pool.submit(() -> {
                        start.await();
                        try {
                            var response = mvc.perform(get("/api/auth/csrf").cookie(browser.cookie())).andReturn().getResponse();
                            if (response.getStatus() == 200) tokens.add(JsonPath.read(response.getContentAsString(), "$.token"));
                            return response.getStatus();
                        } catch (Exception exception) {
                            return 500;
                        }
                    }));
                }
                start.countDown();
                for (var status : statuses) assertThat(status.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            } finally {
                pool.shutdownNow();
            }
            assertThat(tokens).hasSize(8);
            browser.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/account/profile")
                    .header("X-CSRF-TOKEN", before).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Old token\"}"))
                    .andExpect(status().isForbidden());
            for (var token : tokens) {
                browser.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/account/profile")
                        .header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New token\"}"))
                        .andExpect(status().isOk());
            }
        } finally {
            users.findByEmailIgnoreCase("csrf.parallel@gmail.com").ifPresent(user -> {
                jdbc.update("delete from spring_session where principal_name = ?", user.getId().toString());
                jdbc.update("delete from user_identities where user_id = ?", user.getId());
                jdbc.update("delete from login_events where user_id = ?", user.getId());
                jdbc.update("delete from email_tokens where user_id = ?", user.getId());
                jdbc.update("delete from user_credentials where user_id = ?", user.getId());
                jdbc.update("delete from users where id = ?", user.getId());
            });
        }
    }

    @Test
    void thePasswordSignInIsUnchangedWhileGoogleIsOn() throws Exception {
        passwordAccount("password@example.com", "Password");
        new BrowserSession(mvc).login("password@example.com", PASSWORD).andExpect(status().isNoContent());
        new BrowserSession(mvc).login("password@example.com", "Wrong-password-2026").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.googleSignIn").value(true));
    }

    // --- Limits and logs ------------------------------------------------------------------------------------------

    @Test
    void startsAndReturnsAreLimitedPerAddress() throws Exception {
        var browser = new BrowserSession(mvc).device(null, "198.51.100.7");
        for (int i = 0; i < 30; i++) start(browser, "mode=login").andExpect(status().isFound());

        start(browser, "mode=login").andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"))
                .andExpect(header().exists("Retry-After"));
        browser.perform(get("/api/auth/oauth2/callback/google").param("code", "c").param("state", "s"))
                .andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        assertThat(GOOGLE.authorizations()).isEmpty();

        start(new BrowserSession(mvc).device(null, "198.51.100.8"), "mode=login").andExpect(status().isFound());
    }

    @Test
    void neitherTheCodeNorTheStateNorAnyTokenReachesTheLog(CapturedOutput output) throws Exception {
        GOOGLE.account = Account.gmail("sub-logs", "logs@gmail.com");
        var browser = new BrowserSession(mvc);
        var authorization = start(browser, "mode=login").andReturn().getResponse().getRedirectedUrl();
        var state = FakeGoogle.query(URI.create(authorization).getRawQuery()).get("state");
        browser.perform(get(GOOGLE.consent(authorization))).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        GOOGLE.fault = FakeGoogle.Fault.WRONG_NONCE;
        signInWithGoogle(new BrowserSession(mvc)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));

        assertThat(output.getAll()).contains("Sign-in with Google did not finish: invalid_nonce");
        assertThat(GOOGLE.issuedSecrets()).isNotEmpty().allSatisfy(secret -> assertThat(output.getAll()).doesNotContain(secret));
        assertThat(output.getAll()).doesNotContain(state).doesNotContain(FakeGoogle.CLIENT_SECRET);
    }

    // --- Helpers --------------------------------------------------------------------------------------------------

    private User passwordAccount(String email, String name) {
        var user = fixtures.user(email.toLowerCase(), name);
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        return user;
    }

    /** An account made earlier by Google: no password, linked to {@code subject}. */
    private User linkedAccount(String email, String subject, boolean confirmed) {
        var user = fixtures.user(email, "Linked");
        if (!confirmed) {
            user.setEmailVerifiedAt(null);
            user = users.saveAndFlush(user);
        }
        credentials.saveAndFlush(UserCredential.withoutPassword(user));
        link(user, subject);
        GOOGLE.account = Account.gmail(subject, email);
        return user;
    }

    private void block(User user) {
        user.block(Instant.now(), "spam");
        users.saveAndFlush(user);
    }

    private void link(User user, String subject) {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, subject, user.getEmail(), Instant.now()));
    }

    private String turnOnTwoFactor(String email) throws Exception {
        var owner = new BrowserSession(mvc);
        owner.login(email, PASSWORD).andExpect(status().isNoContent());
        String secret = JsonPath.read(owner.send(post("/api/account/two-factor/setup")).andReturn().getResponse().getContentAsString(), "$.secret");
        owner.send(post("/api/account/two-factor/enable").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + Totp.code(secret, Totp.step(Instant.now())) + "\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mail.clear();
        return secret;
    }

    private ResultActions answer(BrowserSession browser, String code) throws Exception {
        return browser.send(post("/api/auth/two-factor").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }
}
