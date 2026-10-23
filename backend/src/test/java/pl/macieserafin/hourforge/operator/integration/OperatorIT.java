package pl.macieserafin.hourforge.operator.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.twofactor.Totp;
import pl.macieserafin.hourforge.user.User;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperatorIT extends ApiIntegrationTestSupport {
    // Listed in app.operator.emails of the test profile.
    private static final String OPERATOR_EMAIL = "operator@example.test";
    private static final String PASSWORD = "Test-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired TimeEntryRepository entries;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private User operatorUser;
    private User someone;
    private BrowserSession operator;
    private BrowserSession someoneBrowser;

    @BeforeEach
    void signIn() throws Exception {
        operatorUser = person(OPERATOR_EMAIL, "Operator");
        someone = person("someone@example.test", "Someone");
        operator = new BrowserSession(mvc);
        operator.login(OPERATOR_EMAIL, PASSWORD).andExpect(status().isNoContent());
        someoneBrowser = new BrowserSession(mvc);
        someoneBrowser.login("someone@example.test", PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void onlyListedAccountsAreOperatorsAndOthersGetNothing() throws Exception {
        operator.perform(get("/api/me")).andExpect(jsonPath("$.operator").value(true));
        someoneBrowser.perform(get("/api/me")).andExpect(jsonPath("$.operator").value(false));

        for (var request : new MockHttpServletRequestBuilder[] {get("/api/operator/stats"), get("/api/operator/accounts"), get("/api/operator/teams")}) {
            someoneBrowser.perform(request).andExpect(status().isForbidden());
        }
        someoneBrowser.send(post("/api/operator/accounts/" + operatorUser.getId() + "/block").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"no\"}")).andExpect(status().isForbidden());
        someoneBrowser.send(put("/api/operator/announcement").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hi\",\"level\":\"INFO\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/operator/stats")).andExpect(status().isUnauthorized());
        operator.perform(delete("/api/operator/announcement")).andExpect(status().isForbidden());
    }

    @Test
    void anUnconfirmedListedAddressIsNotAnOperator() throws Exception {
        jdbc.update("update users set email_verified_at = null where id = ?", operatorUser.getId());
        entityManager.clear();
        operator.perform(get("/api/operator/stats")).andExpect(status().isForbidden());
    }

    @Test
    void showsUsageAndFindsAccountsAndTeams() throws Exception {
        var team = fixtures.team("Abuse Team_1", "UTC");
        fixtures.member(team, someone, TeamRole.OWNER, Instant.now());

        operator.perform(get("/api/operator/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.teams").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeLastWeek").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
        operator.perform(get("/api/operator/accounts").param("query", "SOMEONE@example"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value("someone@example.test"))
                .andExpect(jsonPath("$.items[0].ownedTeams").value(1))
                .andExpect(jsonPath("$.items[0].twoFactor").value(false));
        // LIKE wildcards in the query are taken literally.
        operator.perform(get("/api/operator/accounts").param("query", "%")).andExpect(jsonPath("$.items", hasSize(0)));
        operator.perform(get("/api/operator/teams").param("query", "abuse team_1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].ownerEmail").value("someone@example.test"))
                .andExpect(jsonPath("$.items[0].members").value(1));
        operator.perform(get("/api/operator/teams").param("query", "abuse team%1")).andExpect(jsonPath("$.items", hasSize(0)));
        operator.perform(get("/api/operator/accounts").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void blockingEndsTheSessionsAndRefusesSignInUntilLifted() throws Exception {
        block(someone, "").andExpect(status().isBadRequest());
        block(operatorUser, "Testing").andExpect(status().isBadRequest());
        block(someone, "Spam invitations").andExpect(status().isNoContent());

        someoneBrowser.perform(get("/api/me")).andExpect(status().isUnauthorized());
        new BrowserSession(mvc).login("someone@example.test", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        // A wrong password does not reveal the block.
        new BrowserSession(mvc).login("someone@example.test", "wrong-password-value").andExpect(status().isUnauthorized());
        operator.perform(get("/api/operator/accounts").param("query", "someone@"))
                .andExpect(jsonPath("$.items[0].blockedReason").value("Spam invitations"));

        operator.send(post("/api/operator/accounts/" + someone.getId() + "/unblock")).andExpect(status().isNoContent());
        new BrowserSession(mvc).login("someone@example.test", PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void canTurnOffTwoStepSignInForSomeoneWhoLostTheirPhone() throws Exception {
        var setup = someoneBrowser.send(post("/api/account/two-factor/setup")).andReturn().getResponse().getContentAsString();
        String secret = com.jayway.jsonpath.JsonPath.read(setup, "$.secret");
        someoneBrowser.send(post("/api/account/two-factor/enable").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + Totp.code(secret, Totp.step(Instant.now())) + "\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        new BrowserSession(mvc).login("someone@example.test", PASSWORD).andExpect(status().isAccepted());

        operator.send(post("/api/operator/accounts/" + someone.getId() + "/two-factor/disable")).andExpect(status().isNoContent());

        new BrowserSession(mvc).login("someone@example.test", PASSWORD).andExpect(status().isNoContent());
        assertThat(mail.last().to()).isEqualTo("someone@example.test");
        assertThat(mail.last().subject()).isEqualTo("Wyłączono logowanie dwuetapowe w HourForge");
        operator.send(post("/api/operator/accounts/" + someone.getId() + "/two-factor/disable")).andExpect(status().isConflict());
    }

    @Test
    void removesAnAbusiveTeamEvenWithARunningTimerAndThenItsOwner() throws Exception {
        var team = fixtures.team("Spam", "UTC");
        fixtures.member(team, someone, TeamRole.OWNER, Instant.now());
        var project = fixtures.project(team, "Spam project", null);
        entries.saveAndFlush(new TimeEntry(someone, team, project, EntryType.TIMER, LocalDate.now(), Instant.now(), null, null, null));

        operator.send(delete("/api/operator/accounts/" + someone.getId())).andExpect(status().isConflict());
        operator.send(delete("/api/operator/teams/" + team.getId())).andExpect(status().isNoContent());
        operator.send(delete("/api/operator/accounts/" + someone.getId())).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from teams where id = ?", Integer.class, team.getId())).isZero();
        assertThat(jdbc.queryForObject("select deleted_at is not null from users where id = ?", Boolean.class, someone.getId())).isTrue();
        someoneBrowser.perform(get("/api/me")).andExpect(status().isUnauthorized());
        operator.send(delete("/api/operator/accounts/" + someone.getId())).andExpect(status().isConflict());
        operator.send(delete("/api/operator/accounts/" + operatorUser.getId())).andExpect(status().isBadRequest());
        operator.send(delete("/api/operator/teams/" + team.getId())).andExpect(status().isNotFound());
    }

    @Test
    void theNoticeIsShownToEveryoneUntilRemoved() throws Exception {
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.announcement").value(nullValue()));
        announce("   ", "INFO").andExpect(status().isBadRequest());
        announce("x".repeat(501), "INFO").andExpect(status().isBadRequest());

        announce("  Prace serwisowe w sobotę 22:00–23:00.  ", "WARNING").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Prace serwisowe w sobotę 22:00–23:00."));
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.announcement.message").value("Prace serwisowe w sobotę 22:00–23:00."))
                .andExpect(jsonPath("$.announcement.level").value("WARNING"));
        announce("Nowa wersja", "INFO").andExpect(status().isOk());
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.announcement.message").value("Nowa wersja"));

        operator.send(delete("/api/operator/announcement")).andExpect(status().isNoContent());
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.announcement").value(nullValue()));
    }

    private User person(String email, String name) {
        var user = fixtures.user(email, name);
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        return user;
    }

    private ResultActions block(User user, String reason) throws Exception {
        return operator.send(post("/api/operator/accounts/" + user.getId() + "/block").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions announce(String message, String level) throws Exception {
        return operator.send(put("/api/operator/announcement").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\",\"level\":\"" + level + "\"}"));
    }
}
