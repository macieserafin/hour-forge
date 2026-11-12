package pl.macieserafin.hourforge.logging.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.logging.SecurityEvents;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security event log records what happened in real requests, without addresses, and nothing that was refused.
 * Not in a test transaction: events are written only after a commit, so these requests must really commit.
 */
@AutoConfigureMockMvc
class SecurityEventsIT extends IntegrationTestSupport {
    private static final String PASSWORD = "Test-password-2026";
    private final String email = "events-" + UUID.randomUUID() + "@example.com";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityEvents.LOGGER);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private User person;
    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdTeams = new ArrayList<>();

    @BeforeEach
    void setUp() {
        person = fixtures.user(email, "Events Test");
        createdUsers.add(person.getId());
        credentials.saveAndFlush(new UserCredential(person, encoder.encode(PASSWORD)));
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(logged);
        createdTeams.forEach(team -> {
            jdbc.update("delete from team_members where team_id = ?", team);
            jdbc.update("delete from teams where id = ?", team);
        });
        createdUsers.forEach(user -> {
            jdbc.update("delete from spring_session where principal_name = ?", user.toString());
            jdbc.update("delete from login_events where user_id = ?", user);
            jdbc.update("delete from user_credentials where user_id = ?", user);
            jdbc.update("delete from user_preferences where user_id = ?", user);
            jdbc.update("delete from users where id = ?", user);
        });
    }

    @Test
    void signInsAreRecordedWithoutTheAddress() throws Exception {
        var session = new BrowserSession(mvc);
        signIn(session, "Wrong-password-2026").andExpect(status().isUnauthorized());
        signIn(session, PASSWORD).andExpect(status().isNoContent());
        session.perform(post("/api/auth/logout").header("X-CSRF-TOKEN", csrfToken(session))).andExpect(status().isNoContent());

        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).satisfiesExactly(
                failed -> assertThat(failed).contains("event=sign-in.failed", "step=password").doesNotContain("actor="),
                succeeded -> assertThat(succeeded).contains("event=sign-in.succeeded", "actor=" + person.getId(), "method=PASSWORD"),
                signedOut -> assertThat(signedOut).contains("event=sign-out", "actor=" + person.getId()));
        assertThat(logged.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(email, PASSWORD));
    }

    @Test
    void aMemberRemovalIsRecordedAndARefusedOneIsNot() throws Exception {
        var team = fixtures.team("Events team", "UTC");
        createdTeams.add(team.getId());
        var owner = person;
        var member = fixtures.user("member-" + email, "Member");
        createdUsers.add(member.getId());
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());

        mvc.perform(delete("/api/teams/" + team.getId() + "/members/" + owner.getId())
                .with(user(principal(member))).with(csrf())).andExpect(status().is4xxClientError());
        assertThat(logged.list).isEmpty();

        mvc.perform(delete("/api/teams/" + team.getId() + "/members/" + member.getId())
                .with(user(principal(owner))).with(csrf())).andExpect(status().isNoContent());
        assertThat(logged.list).singleElement().extracting(ILoggingEvent::getFormattedMessage).asString()
                .contains("event=team.member-removed", "actor=" + owner.getId(), "team=" + team.getId(), "user=" + member.getId());
    }

    private static AppUserPrincipal principal(User actor) {
        return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
    }

    private org.springframework.test.web.servlet.ResultActions signIn(BrowserSession session, String password) throws Exception {
        return session.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-CSRF-TOKEN", csrfToken(session)).param("email", email).param("password", password));
    }

    private static String csrfToken(BrowserSession session) throws Exception {
        var response = session.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.token");
    }
}
