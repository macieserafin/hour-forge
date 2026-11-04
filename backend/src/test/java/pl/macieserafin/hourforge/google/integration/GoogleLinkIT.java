package pl.macieserafin.hourforge.google.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.google.GoogleLinkIntent;
import pl.macieserafin.hourforge.identity.IdentityProvider;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.identity.UserIdentityRepository;
import pl.macieserafin.hourforge.security.RequestRateLimits;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.support.FakeGoogle;
import pl.macieserafin.hourforge.support.FakeGoogle.Account;
import pl.macieserafin.hourforge.support.GoogleSignInTestSupport;
import pl.macieserafin.hourforge.twofactor.Totp;
import pl.macieserafin.hourforge.user.User;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Linking Google from the account page and unlinking it (A13 K3, flows F4 and F5, D2, D5, D14, C9, C15). */
class GoogleLinkIT extends GoogleSignInTestSupport {
    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "Test-password-2026";
    private static final String LINKED = FRONTEND + "/account?google=LINKED#google";

    @Autowired UserCredentialRepository credentials;
    @Autowired UserIdentityRepository identities;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @SuppressWarnings("rawtypes")
    @Autowired org.springframework.session.SessionRepository sessionStore;
    @Autowired RequestRateLimits rateLimits;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private User owner;
    private BrowserSession browser;

    @BeforeEach
    void signInWithPassword() throws Exception {
        owner = fixtures.user(EMAIL, "Owner");
        credentials.saveAndFlush(new UserCredential(owner, encoder.encode(PASSWORD)));
        browser = new BrowserSession(mvc);
        browser.login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        GOOGLE.account = Account.gmail("sub-owner", "someone.else@gmail.com");
    }

    // --- F4: linking ----------------------------------------------------------------------------------------------

    @Test
    void theOwnerLinksAGoogleAccountWithAnotherAddressStaysSignedInAndCanThenSignInWithIt() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value("/api/auth/oauth2/authorization/google?mode=link"));
        var before = browser.sessionId();

        followLink(browser).andExpect(redirectedUrl(LINKED));

        assertThat(browser.sessionId()).isNotEqualTo(before);
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.google.linked").value(true)).andExpect(jsonPath("$.google.email").value("someone.else@gmail.com"));
        var identity = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-owner").orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(owner.getId());
        assertThat(mail.last().to()).isEqualTo(EMAIL);
        assertThat(mail.last().subject()).isEqualTo("Połączono konto Google z HourForge");

        var phone = new BrowserSession(mvc);
        signInWithGoogle(phone).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        phone.perform(get("/api/me")).andExpect(jsonPath("$.id").value(owner.getId().toString()));
    }

    @Test
    void linkingNeedsTheRightPasswordAndSharesItsLimit() throws Exception {
        requestLink(browser, null).andExpect(status().isBadRequest());
        for (int i = 0; i < 4; i++) requestLink(browser, "Wrong-password-2026").andExpect(status().isBadRequest());
        requestLink(browser, "Wrong-password-2026").andExpect(status().isBadRequest());
        requestLink(browser, PASSWORD).andExpect(status().isTooManyRequests());

        start(browser, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
        assertThat(GOOGLE.authorizations()).isEmpty();
    }

    @Test
    void linkingNeedsASignedInPersonAndTheCsrfToken() throws Exception {
        new BrowserSession(mvc).send(post("/api/account/google/link").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        browser.perform(post("/api/account/google/link").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void anAccountThatAlreadyHasGoogleCannotStartAnotherLink() throws Exception {
        identities.saveAndFlush(new UserIdentity(owner, IdentityProvider.GOOGLE, "sub-existing", null, Instant.now()));
        requestLink(browser, PASSWORD).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_ALREADY_LINKED_TO_YOU"));
    }

    @Test
    void withoutAnIntentOfThisSessionNothingLeavesForGoogle() throws Exception {
        start(browser, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
        // Another browser, signed in or not, cannot use the intent given to this one.
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        start(new BrowserSession(mvc), "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
        var laptop = new BrowserSession(mvc);
        laptop.login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        start(laptop, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
        assertThat(GOOGLE.authorizations()).isEmpty();
    }

    @Test
    void anIntentIsValidForTenMinutesAndAnExpiredOneStillSignsThePersonBackIn() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        expireIntent(browser);
        start(browser, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));

        requestLink(browser, PASSWORD).andExpect(status().isOk());
        var authorization = start(browser, "mode=link").andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        expireIntent(browser);
        browser.perform(get(GOOGLE.consent(authorization))).andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));

        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId().toString()));
    }

    @Test
    void anIntentIsUsedOnce() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        var authorization = start(browser, "mode=link").andReturn().getResponse().getRedirectedUrl();
        var callback = GOOGLE.consent(authorization);
        browser.perform(get(callback)).andExpect(redirectedUrl(LINKED));

        // The replay finds no waiting request, so it cannot even tell it was a link; the person stays signed in.
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        start(browser, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
        browser.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void aGoogleAccountOfSomebodyElseIsNotTaken() throws Exception {
        var other = fixtures.user("other@example.com", "Other");
        identities.saveAndFlush(new UserIdentity(other, IdentityProvider.GOOGLE, "sub-owner", "someone.else@gmail.com", Instant.now()));

        linkWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/account?google=GOOGLE_ALREADY_LINKED#google"));

        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-owner").orElseThrow().getUser().getId()).isEqualTo(other.getId());
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId().toString()));
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void anAccountLinkedInTheMeantimeKeepsItsGoogleAccount() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        identities.saveAndFlush(new UserIdentity(owner, IdentityProvider.GOOGLE, "sub-meantime", null, Instant.now()));

        followLink(browser).andExpect(redirectedUrl(FRONTEND + "/account?google=GOOGLE_ALREADY_LINKED_TO_YOU#google"));
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-owner")).isEmpty();
    }

    @Test
    void changedCredentialsVoidTheIntentAndEndTheSession() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        var authorization = start(browser, "mode=link").andReturn().getResponse().getRedirectedUrl();
        // Signed out everywhere from another device in the meantime.
        var credential = credentials.findById(owner.getId()).orElseThrow();
        credential.invalidateSessions();
        credentials.saveAndFlush(credential);

        browser.perform(get(GOOGLE.consent(authorization))).andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));

        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancellingAtGoogleReturnsToTheAccountSignedInAndUsesTheIntentUp() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        GOOGLE.fault = FakeGoogle.Fault.CANCEL_CONSENT;

        followLink(browser).andExpect(redirectedUrl(FRONTEND + "/account?google=GOOGLE_CANCELLED#google"));

        browser.perform(get("/api/me")).andExpect(status().isOk());
        start(browser, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
    }

    @Test
    void aBrokenIdTokenLinksNothing() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        GOOGLE.fault = FakeGoogle.Fault.WRONG_AUDIENCE;
        followLink(browser).andExpect(redirectedUrl(FRONTEND + "/account?google=GOOGLE_FAILED#google"));
        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void aLimitedReturnFromALinkGoesBackToTheAccountPage() throws Exception {
        browser.device(null, "192.0.2.15");
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        var authorization = start(browser, "mode=link").andReturn().getResponse().getRedirectedUrl();
        for (int i = 0; i < 29; i++) rateLimits.googleSignIn("192.0.2.15");

        browser.perform(get(GOOGLE.consent(authorization))).andExpect(redirectedUrl(FRONTEND + "/account?google=GOOGLE_FAILED#google"));
        browser.perform(get("/api/me")).andExpect(status().isOk());
    }

    // --- A shared browser: a new sign-in ends what the previous person started (review K3 D5) ----------------------

    @Test
    void anAbandonedLinkOfThePreviousPersonNeitherLinksNorReplacesAPasswordSignIn() throws Exception {
        var callback = abandonedLink();
        var next = passwordAccount("next@example.com");

        browser.login("next@example.com", PASSWORD).andExpect(status().isNoContent());
        assertThat(intentOf(browser)).isNull();
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));

        assertThat(GOOGLE.tokenCalls.get()).isZero();
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-owner")).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(next.getId().toString()));
        start(browser, "mode=link").andExpect(redirectedUrl(FRONTEND + "/account?google=LINK_EXPIRED#google"));
    }

    @Test
    void anAbandonedLinkOfThePreviousPersonEndsWhenSomebodySignsInWithGoogle() throws Exception {
        var callback = abandonedLink();
        GOOGLE.account = Account.gmail("sub-next", "next.person@gmail.com");

        signInWithGoogle(browser).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        assertThat(intentOf(browser)).isNull();
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));

        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("next.person@gmail.com"));
    }

    @Test
    void anAbandonedLinkOfThePreviousPersonEndsAtTheFirstStepOfATwoStepSignIn() throws Exception {
        var callback = abandonedLink();
        var next = passwordAccount("two-step-next@example.com");
        var secret = turnOnTwoFactor("two-step-next@example.com");

        browser.login("two-step-next@example.com", PASSWORD).andExpect(status().isAccepted());
        assertThat(intentOf(browser)).isNull();
        browser.perform(get(callback)).andExpect(redirectedUrl(FRONTEND + "/login?google=GOOGLE_FAILED"));
        browser.send(post("/api/auth/two-factor").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.code(secret, Totp.step(Instant.now()) + 1) + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-owner")).isEmpty();
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(next.getId().toString()));
    }

    @Test
    void theSecondStepAlsoEndsALinkStartedWhileTheCodeWasAwaited() throws Exception {
        var next = passwordAccount("waiting@example.com");
        var secret = turnOnTwoFactor("waiting@example.com");
        browser.login("waiting@example.com", PASSWORD).andExpect(status().isAccepted());
        // Not reachable through the API while signed out; placed directly, as a left-over would be.
        storeIntent(browser, new GoogleLinkIntent(owner.getId(), 0, Instant.now().plusSeconds(600)));

        browser.send(post("/api/auth/two-factor").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.code(secret, Totp.step(Instant.now()) + 1) + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(intentOf(browser)).isNull();
        browser.perform(get("/api/me")).andExpect(jsonPath("$.id").value(next.getId().toString()));
    }

    // --- F5: unlinking --------------------------------------------------------------------------------------------

    @Test
    void unlinkingNeedsThePasswordEndsTheOtherSessionsAndKeepsThisOne() throws Exception {
        linkWithGoogle(browser).andExpect(redirectedUrl(LINKED));
        var phone = new BrowserSession(mvc);
        signInWithGoogle(phone).andExpect(redirectedUrl(FRONTEND + "/dashboard"));
        var version = credentials.findSessionVersion(owner.getId()).orElseThrow();

        unlink(browser, "Wrong-password-2026").andExpect(status().isBadRequest());
        unlink(browser, null).andExpect(status().isBadRequest());
        unlink(browser, PASSWORD).andExpect(status().isNoContent());

        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isEmpty();
        assertThat(credentials.findSessionVersion(owner.getId())).contains(version + 1);
        browser.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.google").doesNotExist());
        phone.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertThat(mail.last().subject()).isEqualTo("Odłączono konto Google od HourForge");
        unlink(browser, PASSWORD).andExpect(status().isNotFound());
    }

    @Test
    void wrongPasswordsForUnlinkingAreLimited() throws Exception {
        linkWithGoogle(browser).andExpect(redirectedUrl(LINKED));
        for (int i = 0; i < 5; i++) unlink(browser, "Wrong-password-2026").andExpect(status().isBadRequest());
        unlink(browser, PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(identities.findByUserIdAndProvider(owner.getId(), IdentityProvider.GOOGLE)).isPresent();
    }

    @Test
    void theLastWayToSignInCannotBeRemoved() throws Exception {
        GOOGLE.account = Account.gmail("sub-only-google", "only.google@gmail.com");
        var onlyGoogle = new BrowserSession(mvc);
        signInWithGoogle(onlyGoogle).andExpect(redirectedUrl(FRONTEND + "/dashboard"));

        unlink(onlyGoogle, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_SIGN_IN_METHOD"));
        requestLink(onlyGoogle, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_ALREADY_LINKED_TO_YOU"));
        onlyGoogle.perform(get("/api/me")).andExpect(jsonPath("$.google.linked").value(true));
    }

    @Test
    void unlinkingNeedsTheCsrfToken() throws Exception {
        browser.perform(post("/api/account/google/unlink").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());
    }

    // --- Helpers --------------------------------------------------------------------------------------------------

    private ResultActions requestLink(BrowserSession session, String password) throws Exception {
        return session.send(post("/api/account/google/link").contentType(MediaType.APPLICATION_JSON)
                .content(password == null ? "{}" : "{\"currentPassword\":\"" + password + "\"}"));
    }

    /** From the returned address through the consent at the stand-in back to the callback. */
    private ResultActions followLink(BrowserSession session) throws Exception {
        var authorization = start(session, "mode=link").andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        return session.perform(get(GOOGLE.consent(authorization)));
    }

    private ResultActions linkWithGoogle(BrowserSession session) throws Exception {
        var url = JsonPath.<String>read(requestLink(session, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.authorizationUrl");
        var authorization = session.perform(get(URI.create(url))).andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        return session.perform(get(GOOGLE.consent(authorization)));
    }

    private ResultActions unlink(BrowserSession session, String password) throws Exception {
        return session.send(post("/api/account/google/unlink").contentType(MediaType.APPLICATION_JSON)
                .content(password == null ? "{}" : "{\"currentPassword\":\"" + password + "\"}"));
    }

    /** The owner asks to link, leaves for Google and never comes back: the answer waits to be replayed. */
    private URI abandonedLink() throws Exception {
        requestLink(browser, PASSWORD).andExpect(status().isOk());
        var authorization = start(browser, "mode=link").andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        return GOOGLE.consent(authorization);
    }

    private User passwordAccount(String email) {
        var user = fixtures.user(email, "Next");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        return user;
    }

    private String turnOnTwoFactor(String email) throws Exception {
        var setup = new BrowserSession(mvc);
        setup.login(email, PASSWORD).andExpect(status().isNoContent());
        String secret = JsonPath.read(setup.send(post("/api/account/two-factor/setup")).andReturn().getResponse().getContentAsString(), "$.secret");
        setup.send(post("/api/account/two-factor/enable").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + Totp.code(secret, Totp.step(Instant.now())) + "\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        return secret;
    }

    private GoogleLinkIntent intentOf(BrowserSession session) {
        var stored = (org.springframework.session.Session) sessionStore.findById(session.sessionId());
        return stored.getAttribute(GoogleLinkIntent.class.getName());
    }

    @SuppressWarnings("unchecked")
    private void storeIntent(BrowserSession session, GoogleLinkIntent intent) {
        var stored = (org.springframework.session.Session) sessionStore.findById(session.sessionId());
        stored.setAttribute(GoogleLinkIntent.class.getName(), intent);
        sessionStore.save(stored);
    }

    @SuppressWarnings("unchecked")
    private void expireIntent(BrowserSession session) {
        var stored = (org.springframework.session.Session) sessionStore.findById(session.sessionId());
        GoogleLinkIntent intent = stored.getAttribute(GoogleLinkIntent.class.getName());
        stored.setAttribute(GoogleLinkIntent.class.getName(), new GoogleLinkIntent(intent.userId(), intent.sessionVersion(), Instant.now().minusSeconds(1)));
        sessionStore.save(stored);
    }
}
