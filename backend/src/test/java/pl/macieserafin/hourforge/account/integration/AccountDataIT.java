package pl.macieserafin.hourforge.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.macieserafin.hourforge.account.AccountExport;
import pl.macieserafin.hourforge.support.BrowserSession;
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
    @Autowired pl.macieserafin.hourforge.preferences.UserPreferencesRepository preferenceRows;

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
        session.perform(get("/api/account/export"))
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
        for (int i = 0; i < 5; i++) session.perform(get("/api/account/export")).andExpect(status().isOk());
        session.perform(get("/api/account/export")).andExpect(status().isTooManyRequests());
    }

    @Test
    void deletingAnonymizesTheAccountButKeepsTheTimeEntriesInTheTeam() throws Exception {
        manual(user, team, project, LocalDate.parse("2026-10-18"), 900, "Kept work");
        entries.saveAndFlush(new TimeEntry(user, team, project, EntryType.TIMER, LocalDate.parse("2026-10-19"),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS), null, null, "Running"));
        var invite = invites.saveAndFlush(new TeamInvite(team, user, "h".repeat(64), 1, Instant.now().plus(1, ChronoUnit.DAYS)));
        preferenceRows.saveAndFlush(new pl.macieserafin.hourforge.preferences.UserPreferences(user.getId()));
        var session = signIn();
        mail.clear();

        delete(session, PASSWORD).andExpect(status().isNoContent());
        em.clear();
        assertThat(preferenceRows.findById(user.getId())).isEmpty();

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
        session.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(EMAIL);
        assertThat(mail.last().body()).doesNotContain(PASSWORD);

        login(EMAIL, PASSWORD, new BrowserSession(mvc)).andExpect(status().isUnauthorized());
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
        session.perform(get("/api/me")).andExpect(status().isOk());
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
        session.perform(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());
        postDelete(session, "{}").andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    private TimeEntry manual(User owner, Team inTeam, Project inProject, LocalDate date, int seconds, String description) {
        return entries.saveAndFlush(new TimeEntry(owner, inTeam, inProject, EntryType.MANUAL, date, null, null, seconds, description));
    }

    private ResultActions delete(BrowserSession session, String password) throws Exception {
        return postDelete(session, "{\"currentPassword\":\"" + password + "\"}");
    }

    private ResultActions postDelete(BrowserSession session, String body) throws Exception {
        return session.send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/account/delete")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private BrowserSession signIn() throws Exception {
        var session = new BrowserSession(mvc);
        login(EMAIL, PASSWORD, session).andExpect(status().isNoContent());
        return session;
    }

    private ResultActions login(String email, String password, BrowserSession session) throws Exception {
        return session.login(email, password);
    }

    @Autowired pl.macieserafin.hourforge.entrytemplate.EntryTemplateRepository templateRows;
    @Autowired pl.macieserafin.hourforge.tag.TagRepository tagRows;
    @Autowired pl.macieserafin.hourforge.jira.JiraUserAuthorizationRepository jiraGrants;
    @Autowired pl.macieserafin.hourforge.twofactor.UserTwoFactorRepository twoFactorRows;
    @Autowired pl.macieserafin.hourforge.twofactor.BackupCodeRepository backupCodeRows;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void exportAlsoCoversTemplatesJiraConsentTwoStepStateAndCorrectionMarksButNoSecrets() throws Exception {
        var session = signIn();
        var review = tagRows.saveAndFlush(new pl.macieserafin.hourforge.tag.Tag(team, "Review", null));
        var template = new pl.macieserafin.hourforge.entrytemplate.EntryTemplate(user, team);
        template.apply("Weekly review", project.getId(), "Look at the board", review, null);
        templateRows.saveAndFlush(template);
        jiraGrants.saveAndFlush(new pl.macieserafin.hourforge.jira.JiraUserAuthorization(user, "cloud-1", "ENC-ACCESS-SECRET",
                "ENC-REFRESH-SECRET", Instant.now().plusSeconds(3600), "read:jira-work", "v1"));
        twoFactorRows.saveAndFlush(new pl.macieserafin.hourforge.twofactor.UserTwoFactor(user.getId(), "TOTP-SECRET-VALUE", Instant.now()));
        jdbc.update("update user_two_factor set enabled_at = now() where user_id = ?", user.getId());
        backupCodeRows.saveAndFlush(new pl.macieserafin.hourforge.twofactor.BackupCode(user.getId(), "BACKUP-HASH-VALUE"));
        var corrected = manual(user, team, project, LocalDate.parse("2026-10-18"), 900, "Corrected by the owner");
        corrected.setLastEditedAt(Instant.now());
        corrected.setLastEditedBy(colleague);
        entries.saveAndFlush(corrected);
        em.clear();

        session.perform(get("/api/account/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates.length()").value(1))
                .andExpect(jsonPath("$.templates[0].name").value("Weekly review"))
                .andExpect(jsonPath("$.templates[0].tagName").value("Review"))
                .andExpect(jsonPath("$.templates[0].teamName").value("Studio"))
                .andExpect(jsonPath("$.jiraAuthorizations.length()").value(1))
                .andExpect(jsonPath("$.jiraAuthorizations[0].cloudId").value("cloud-1"))
                .andExpect(jsonPath("$.twoFactor.enabled").value(true))
                .andExpect(jsonPath("$.twoFactor.backupCodesLeft").value(1))
                .andExpect(jsonPath("$.timeEntries[?(@.description=='Corrected by the owner')].lastEditedBy").value("Colleague"))
                .andExpect(jsonPath("$.timeEntries[?(@.description=='Corrected by the owner')].lastEditedAt").isNotEmpty())
                .andExpect(content().string(not(containsString("ENC-ACCESS-SECRET"))))
                .andExpect(content().string(not(containsString("ENC-REFRESH-SECRET"))))
                .andExpect(content().string(not(containsString("TOTP-SECRET-VALUE"))))
                .andExpect(content().string(not(containsString("BACKUP-HASH-VALUE"))))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Autowired org.springframework.security.core.userdetails.UserDetailsService userDetails;
    @Autowired pl.macieserafin.hourforge.identity.UserIdentityRepository identities;

    @Test
    void exportShowsHowThePersonSignsInWithTheGoogleIdentifierAndTheSignInMethod() throws Exception {
        var session = signIn();
        session.perform(get("/api/account/export")).andExpect(status().isOk())
                .andExpect(jsonPath("$.signInMethods.password").value(true))
                .andExpect(jsonPath("$.signInMethods.google").value((Object) null))
                .andExpect(jsonPath("$.logins[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.logins[0].method").value("PASSWORD"));

        var linkedAt = Instant.parse("2026-11-01T08:30:00Z");
        var identity = identities.saveAndFlush(new pl.macieserafin.hourforge.identity.UserIdentity(user,
                pl.macieserafin.hourforge.identity.IdentityProvider.GOOGLE, "1098765432123456789", "data.person@gmail.com", linkedAt));
        identity.recordUse(Instant.parse("2026-11-02T09:00:00Z"), "data.person@gmail.com");
        identities.flush();
        session.perform(get("/api/account/export")).andExpect(status().isOk())
                .andExpect(jsonPath("$.signInMethods.password").value(true))
                .andExpect(jsonPath("$.signInMethods.google.email").value("data.person@gmail.com"))
                .andExpect(jsonPath("$.signInMethods.google.linkedAt").value("2026-11-01T08:30:00Z"))
                .andExpect(jsonPath("$.signInMethods.google.lastUsedAt").value("2026-11-02T09:00:00Z"))
                .andExpect(jsonPath("$.signInMethods.google.subject").value("1098765432123456789"));
    }

    @Test
    void anAccountWithoutAPasswordExportsItAndIsNotDeletedWithAPassword() throws Exception {
        var passwordless = pl.macieserafin.hourforge.support.PasswordlessAccounts.create(fixtures, credentials, "google-only@example.com", "Google only");
        var signedIn = pl.macieserafin.hourforge.support.PasswordlessAccounts.signedIn(userDetails, passwordless);
        mvc.perform(get("/api/account/export").with(signedIn)).andExpect(status().isOk())
                .andExpect(jsonPath("$.signInMethods.password").value(false));
        mvc.perform(post("/api/account/delete").with(signedIn).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
        assertThat(users.findById(passwordless.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void deletingTheAccountRemovesTheLinkedGoogleAccountSoItCannotSignInAgain() throws Exception {
        identities.saveAndFlush(new pl.macieserafin.hourforge.identity.UserIdentity(user,
                pl.macieserafin.hourforge.identity.IdentityProvider.GOOGLE, "1098765432123456789", "data.person@gmail.com", Instant.now()));
        delete(signIn(), PASSWORD).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from user_identities where user_id = ?", Integer.class, user.getId())).isZero();
        assertThat(identities.findByProviderAndSubject(pl.macieserafin.hourforge.identity.IdentityProvider.GOOGLE, "1098765432123456789")).isEmpty();
    }

    @Test
    void theOperatorAndTheRemovalOfInactiveAccountsCanDeleteAnAccountWithoutAPassword() {
        var passwordless = pl.macieserafin.hourforge.support.PasswordlessAccounts.create(fixtures, credentials, "google-only@example.com", "Google only");
        identities.saveAndFlush(new pl.macieserafin.hourforge.identity.UserIdentity(passwordless,
                pl.macieserafin.hourforge.identity.IdentityProvider.GOOGLE, "google-only-sub", null, Instant.now()));
        accountData.deleteWithoutPassword(passwordless.getId());
        assertThat(users.findById(passwordless.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(credentials.findById(passwordless.getId())).isEmpty();
        assertThat(identities.findByProviderAndSubject(pl.macieserafin.hourforge.identity.IdentityProvider.GOOGLE, "google-only-sub")).isEmpty();
    }

    @Autowired pl.macieserafin.hourforge.account.AccountDataService accountData;
    @Autowired tools.jackson.databind.ObjectMapper json;

    /**
     * The entries are written from a database cursor rather than from the entities: the copy must still say exactly what
     * the entities say, and carry every field of the export records (a field added later must not be left out).
     */
    @Test
    void theStreamedCopyMatchesTheEntitiesAndHasEveryFieldOfTheRecords() throws Exception {
        var tag = tagRows.saveAndFlush(new pl.macieserafin.hourforge.tag.Tag(team, "Review",
                pl.macieserafin.hourforge.project.ProjectColor.values()[0]));
        // A finished timer: only timers carry a pause.
        var detailed = new TimeEntry(user, team, project, EntryType.TIMER, LocalDate.parse("2026-10-17"),
                Instant.parse("2026-10-17T08:00:00Z"), Instant.parse("2026-10-17T08:30:01.500Z"), 1800, "Tagged and corrected");
        detailed.setTag(tag);
        detailed.setLastEditedBy(colleague);
        detailed.setLastEditedAt(Instant.parse("2026-10-18T08:00:00.123456Z"));
        detailed.setPausedDurationMicros(1_500_000);
        entries.saveAndFlush(detailed);
        manual(user, team, project, null, 300, "Undated \"quoted\" work");

        var body = signIn().perform(get("/api/account/export")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var copy = json.readTree(body);

        assertThat(copy.propertyNames()).containsExactly(java.util.Arrays.stream(AccountExport.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        var expected = entries.findByUserIdOrderByCreatedAtAscIdAsc(user.getId()).stream()
                .map(entry -> new AccountExport.Entry(entry.getId(), entry.getTeam().getId(), entry.getTeam().getName(),
                        entry.getProject().getId(), entry.getProject().getName(), entry.getEntryType().name(),
                        entry.getWorkDate(), entry.getStartedAt(), entry.getEndedAt(), entry.getDurationSeconds(),
                        entry.getPausedDurationMicros() / 1_000_000.0, entry.getDescription(), entry.getCreatedAt(),
                        entry.getUpdatedAt(), entry.getJiraIssueKey(),
                        entry.getTag() == null ? null : entry.getTag().getName(), entry.getLastEditedAt(),
                        entry.getLastEditedBy() == null ? null : entry.getLastEditedBy().getDisplayName())).toList();
        assertThat(copy.get("timeEntries")).isEqualTo(json.valueToTree(expected));
        var tagged = copy.get("timeEntries").get(0);
        assertThat(tagged.propertyNames()).containsExactly(java.util.Arrays.stream(AccountExport.Entry.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        assertThat(tagged.get("tagName").asString()).isEqualTo("Review");
        assertThat(tagged.get("lastEditedBy").asString()).isEqualTo("Colleague");
        assertThat(tagged.get("pausedSeconds").asDouble()).isEqualTo(1.5);
    }
}
