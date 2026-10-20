package pl.macieserafin.hourforge.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.invitation.TeamInvite;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDataIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "data@example.com";
    private static final String PASSWORD = "Current-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired TeamMemberRepository members;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamInviteRepository invites;
    @Autowired jakarta.persistence.EntityManager em;

    private User user;
    private User colleague;
    private Team team;
    private Project project;

    @BeforeEach
    void setup() {
        user = fixtures.user(EMAIL, "Data Test");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        colleague = fixtures.user("colleague@example.com", "Colleague");
        team = fixtures.team("Studio", "Europe/Warsaw");
        fixtures.member(team, colleague, TeamRole.OWNER, Instant.now());
        fixtures.member(team, user, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Website", null);
    }

    @Test
    void exportContainsOwnProfileMembershipsAndEntriesFromEveryTeamIncludingFormerOnes() throws Exception {
        var former = fixtures.team("Old team", "UTC");
        fixtures.member(former, colleague, TeamRole.OWNER, Instant.now());
        var left = fixtures.member(former, user, TeamRole.MEMBER, Instant.now().minus(30, ChronoUnit.DAYS));
        left.setLeftAt(Instant.now().minus(1, ChronoUnit.DAYS));
        members.flush();
        var oldProject = fixtures.project(former, "Legacy", null);
        manual(user, former, oldProject, LocalDate.parse("2026-01-10"), 600, "Old work");
        manual(user, team, project, LocalDate.parse("2026-10-18"), 900, "Dated work");
        manual(user, team, project, null, 300, "Undated work");
        manual(colleague, team, project, LocalDate.parse("2026-10-18"), 1200, "Not mine");
        entries.saveAndFlush(new TimeEntry(user, team, project, EntryType.TIMER, LocalDate.parse("2026-10-19"),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS), null, null, "Running"));

        var session = signIn();
        mvc.perform(get("/api/account/export").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"hourforge-export-")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.profile.email").value(EMAIL))
                .andExpect(jsonPath("$.profile.displayName").value("Data Test"))
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[?(@.teamName=='Old team')].leftAt").isNotEmpty())
                .andExpect(jsonPath("$.timeEntries.length()").value(4))
                .andExpect(jsonPath("$.timeEntries[?(@.description=='Old work')].teamName").value("Old team"))
                .andExpect(jsonPath("$.timeEntries[?(@.description=='Undated work')].workDate").value((Object) null))
                .andExpect(jsonPath("$.timeEntries[?(@.description=='Running')].endedAt").value((Object) null))
                .andExpect(jsonPath("$.timeEntries[?(@.description=='Not mine')]").isEmpty())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("colleague@example.com"))));
    }

    @Test
    void exportNeedsAuthenticationAndIsLimited() throws Exception {
        mvc.perform(get("/api/account/export")).andExpect(status().isUnauthorized());
        var session = signIn();
        for (int i = 0; i < 5; i++) mvc.perform(get("/api/account/export").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/account/export").session(session)).andExpect(status().isTooManyRequests());
    }

    @Test
    void deletingAnonymizesTheAccountButKeepsTheTimeEntriesInTheTeam() throws Exception {
        manual(user, team, project, LocalDate.parse("2026-10-18"), 900, "Kept work");
        entries.saveAndFlush(new TimeEntry(user, team, project, EntryType.TIMER, LocalDate.parse("2026-10-19"),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS), null, null, "Running"));
        var invite = invites.saveAndFlush(new TeamInvite(team, user, "h".repeat(64), 1, Instant.now().plus(1, ChronoUnit.DAYS)));
        var session = signIn();
        mail.clear();

        delete(session, PASSWORD).andExpect(status().isNoContent());

        var gone = users.findById(user.getId()).orElseThrow();
        assertThat(gone.getEmail()).isEqualTo("deleted-" + user.getId() + "@deleted.invalid");
        assertThat(gone.getDisplayName()).isEqualTo("Użytkownik usunięty");
        assertThat(gone.getDeletedAt()).isNotNull();
        assertThat(credentials.findById(user.getId())).isEmpty();
        assertThat(entries.findByUserIdOrderByCreatedAtAscIdAsc(user.getId())).extracting(TimeEntry::getDescription)
                .containsExactly("Kept work");
        assertThat(members.findByTeamIdAndUserId(team.getId(), user.getId()).orElseThrow().getLeftAt()).isNotNull();
        em.flush();
        em.clear();
        assertThat(invites.findById(invite.getId()).orElseThrow().getRevokedAt()).isNotNull();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(EMAIL);
        assertThat(mail.last().body()).doesNotContain(PASSWORD);

        login(EMAIL, PASSWORD, new MockHttpSession()).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Again\",\"email\":\"" + EMAIL + "\",\"password\":\"Brand-new-password-2026\"}"))
                .andExpect(status().isCreated());
        assertThat(users.findByEmailIgnoreCase(EMAIL)).isPresent();
    }

    @Test
    void ownersMustHandOverOrDeleteTheirTeamsFirst() throws Exception {
        var owned = fixtures.team("Mine", "UTC");
        fixtures.member(owned, user, TeamRole.OWNER, Instant.now());
        var session = signIn();
        delete(session, PASSWORD).andExpect(status().isConflict());
        var same = users.findById(user.getId()).orElseThrow();
        assertThat(same.getEmail()).isEqualTo(EMAIL);
        assertThat(same.getDeletedAt()).isNull();
        assertThat(credentials.findById(user.getId())).isPresent();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void anOwnerWhoHandedTheTeamOverCanDelete() throws Exception {
        var session = signIn();
        var owned = fixtures.team("Handed over", "UTC");
        fixtures.member(owned, colleague, TeamRole.OWNER, Instant.now());
        fixtures.member(owned, user, TeamRole.MEMBER, Instant.now());
        delete(session, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void theCurrentPasswordIsRequiredAndWrongAnswersAreLimited() throws Exception {
        var session = signIn();
        for (int i = 0; i < 5; i++) delete(session, "wrong-password-value").andExpect(status().isBadRequest());
        delete(session, PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void deletionNeedsAuthenticationCsrfAndAPassword() throws Exception {
        mvc.perform(post("/api/account/delete").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        var session = signIn();
        mvc.perform(post("/api/account/delete").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());
        postDelete(session, "{}").andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    private TimeEntry manual(User owner, Team inTeam, Project inProject, LocalDate date, int seconds, String description) {
        return entries.saveAndFlush(new TimeEntry(owner, inTeam, inProject, EntryType.MANUAL, date, null, null, seconds, description));
    }

    private ResultActions delete(MockHttpSession session, String password) throws Exception {
        return postDelete(session, "{\"currentPassword\":\"" + password + "\"}");
    }

    private ResultActions postDelete(MockHttpSession session, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/account/delete")
                .session(session).header("X-CSRF-TOKEN", csrfToken(session)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpSession signIn() throws Exception {
        var session = new MockHttpSession();
        login(EMAIL, PASSWORD, session).andExpect(status().isNoContent());
        return session;
    }

    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login").session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).header("X-CSRF-TOKEN", csrfToken(session))
                .param("email", email).param("password", password));
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString(), "$.token");
    }
}
