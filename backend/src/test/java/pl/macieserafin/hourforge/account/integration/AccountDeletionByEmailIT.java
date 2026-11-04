package pl.macieserafin.hourforge.account.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.identity.IdentityProvider;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.identity.UserIdentityRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.support.PasswordlessAccounts;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Removing an account that has no password (A13-D8, C2): a link by e-mail instead of the password. Runs with the sign-in
 * with Google switched off, because such an account still exists then (C1).
 */
class AccountDeletionByEmailIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "google-only@example.com";
    private static final String LINK = "confirm-account-deletion";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired UserIdentityRepository identities;
    @Autowired UserRepository users;
    @Autowired TeamMemberRepository members;
    @Autowired UserDetailsService userDetails;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void passwordlessAccount() {
        user = PasswordlessAccounts.create(fixtures, credentials, EMAIL, "Google Only");
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "sub-google-only", "google.only@gmail.com", Instant.now()));
    }

    @Test
    void theLinkIsSentTheAccountStaysAndOpeningItRemovesTheAccount() throws Exception {
        requestLink(signedIn()).andExpect(status().isAccepted());

        assertThat(mail.last().to()).isEqualTo(EMAIL);
        assertThat(mail.last().subject()).isEqualTo("Potwierdź usunięcie konta w HourForge");
        var token = mail.lastToken(LINK);
        assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();

        // Opened in another browser, signed out.
        confirm(new BrowserSession(mvc), token).andExpect(status().isNoContent());

        var removed = users.findById(user.getId()).orElseThrow();
        assertThat(removed.getDeletedAt()).isNotNull();
        assertThat(removed.getEmail()).isNotEqualTo(EMAIL);
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-google-only")).isEmpty();
        assertThat(credentials.findById(user.getId())).isEmpty();
        assertThat(mail.last().subject()).isEqualTo("Konto w HourForge zostało usunięte");
        confirm(new BrowserSession(mvc), token).andExpect(status().isBadRequest());
    }

    @Test
    void openingTheLinkEndsEverySessionOfTheAccount() throws Exception {
        // A real session of the account: signed in once with a password that is then removed again.
        var credential = credentials.findById(user.getId()).orElseThrow();
        credential.setPasswordHash(encoder.encode("Test-password-2026"));
        credentials.saveAndFlush(credential);
        var laptop = new BrowserSession(mvc);
        laptop.login(EMAIL, "Test-password-2026").andExpect(status().isNoContent());
        credential.setPasswordHash(null);
        credentials.saveAndFlush(credential);

        laptop.send(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON).content("{\"confirmByEmail\":true}"))
                .andExpect(status().isAccepted());
        laptop.perform(get("/api/me")).andExpect(status().isOk());

        confirm(laptop, mail.lastToken(LINK)).andExpect(status().isNoContent());
        laptop.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anOwnerOfATeamGetsNoLink() throws Exception {
        var team = fixtures.team("Studio", "UTC");
        fixtures.member(team, user, TeamRole.OWNER, Instant.now());
        requestLink(signedIn()).andExpect(status().isConflict());
        assertThat(mail.sent()).isEmpty();
    }

    /** Real commits: the refused removal must roll back the use of the link. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aLinkOpenedWhileOwningATeamKeepsWorkingAfterTheHandOver() throws Exception {
        var owner = PasswordlessAccounts.create(fixtures, credentials, "committed-owner@example.com", "Committed Owner");
        var team = fixtures.team("Committed studio", "UTC");
        try {
            mvc.perform(post("/api/account/delete").with(PasswordlessAccounts.signedIn(userDetails, owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"confirmByEmail\":true}")).andExpect(status().isAccepted());
            var token = mail.lastToken(LINK);
            var membership = fixtures.member(team, owner, TeamRole.OWNER, Instant.now());

            confirm(new BrowserSession(mvc), token).andExpect(status().isConflict());
            assertThat(users.findById(owner.getId()).orElseThrow().getDeletedAt()).isNull();

            membership.setRole(TeamRole.ADMIN);
            members.saveAndFlush(membership);
            confirm(new BrowserSession(mvc), token).andExpect(status().isNoContent());
            assertThat(users.findById(owner.getId()).orElseThrow().getDeletedAt()).isNotNull();
        } finally {
            jdbc.update("delete from team_members where team_id = ?", team.getId());
            jdbc.update("delete from teams where id = ?", team.getId());
            jdbc.update("delete from email_tokens where user_id = ?", owner.getId());
            jdbc.update("delete from login_events where user_id = ?", owner.getId());
            jdbc.update("delete from user_credentials where user_id = ?", owner.getId());
            jdbc.update("delete from users where id = ?", owner.getId());
            // The account of @BeforeEach was committed too here.
            jdbc.update("delete from user_identities where user_id = ?", user.getId());
            jdbc.update("delete from user_credentials where user_id = ?", user.getId());
            jdbc.update("delete from users where id = ?", user.getId());
        }
    }

    @Test
    void anAccountWithAPasswordUsesThePasswordInstead() throws Exception {
        var withPassword = fixtures.user("password@example.com", "Password");
        credentials.saveAndFlush(new UserCredential(withPassword, encoder.encode("Test-password-2026")));
        mvc.perform(post("/api/account/delete").with(PasswordlessAccounts.signedIn(userDetails, withPassword)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmByEmail\":true}"))
                .andExpect(status().isBadRequest());
        assertThat(mail.sent()).isEmpty();
        assertThat(users.findById(withPassword.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void withoutAskingForTheLinkTheAccountIsToldItHasNoPassword() throws Exception {
        mvc.perform(post("/api/account/delete").with(signedIn()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void aNewerLinkOrChangedCredentialsVoidTheOldLink() throws Exception {
        requestLink(signedIn()).andExpect(status().isAccepted());
        var first = mail.lastToken(LINK);
        requestLink(signedIn()).andExpect(status().isAccepted());
        var second = mail.lastToken(LINK);
        confirm(new BrowserSession(mvc), first).andExpect(status().isBadRequest());

        // Signed out everywhere (or a password set through the reset) after the link was sent.
        var credential = credentials.findById(user.getId()).orElseThrow();
        credential.invalidateSessions();
        credentials.saveAndFlush(credential);
        confirm(new BrowserSession(mvc), second).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void linksAreLimitedToThreeAnHour() throws Exception {
        for (int i = 0; i < 3; i++) requestLink(signedIn()).andExpect(status().isAccepted());
        requestLink(signedIn()).andExpect(status().isTooManyRequests());
        assertThat(mail.sent()).hasSize(3);
    }

    @Test
    void theConfirmationNeedsTheCsrfTokenAndAValidToken() throws Exception {
        requestLink(signedIn()).andExpect(status().isAccepted());
        var token = mail.lastToken(LINK);
        mvc.perform(post("/api/account/delete/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isForbidden());
        confirm(new BrowserSession(mvc), "not-a-real-token").andExpect(status().isBadRequest());
        confirm(new BrowserSession(mvc), "").andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();
        // Asking for the link needs a signed-in person.
        new BrowserSession(mvc).send(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON).content("{\"confirmByEmail\":true}"))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor signedIn() {
        return PasswordlessAccounts.signedIn(userDetails, user);
    }

    private ResultActions requestLink(RequestPostProcessor principal) throws Exception {
        return mvc.perform(post("/api/account/delete").with(principal).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmByEmail\":true}"));
    }

    private ResultActions confirm(BrowserSession browser, String token) throws Exception {
        return browser.send(post("/api/account/delete/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }
}
