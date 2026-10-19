package pl.macieserafin.hourforge.timeentry.integration;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimeEntryIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    private User actor;
    private User other;
    private Team team;
    private Team foreignTeam;
    private Project project;
    private Project foreignProject;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        actor = fixtures.user("time-member@example.com", "Member");
        other = fixtures.user("time-owner@example.com", "Owner");
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
        team = fixtures.team("Time team", "Pacific/Kiritimati");
        foreignTeam = fixtures.team("Foreign team", "UTC");
        fixtures.member(team, actor, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, other, TeamRole.OWNER, Instant.now());
        project = fixtures.project(team, "Work", null);
        foreignProject = fixtures.project(foreignTeam, "Private", null);
    }

    @Test
    void startUsesServerIdentityAndTimeAndActiveCanBeRecovered() throws Exception {
        var before = Instant.now().minusSeconds(1);
        var result = mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"teamId":"%s","projectId":"%s","description":"  Work  ",
                                 "userId":"%s","entryType":"MANUAL","startedAt":"2000-01-01T00:00:00Z",
                                 "durationSeconds":999,"workDate":"2000-01-01"}
                                """.formatted(team.getId(), project.getId(), other.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType").value("TIMER"))
                .andExpect(jsonPath("$.userId").value(actor.getId().toString()))
                .andExpect(jsonPath("$.description").value("Work"))
                .andExpect(jsonPath("$.durationSeconds").isEmpty())
                .andExpect(jsonPath("$.endedAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(result, "$.id"));
        var stored = entries.findById(id).orElseThrow();
        assertThat(stored.getStartedAt()).isBetween(before, Instant.now());
        assertThat(stored.getWorkDate()).isEqualTo(stored.getStartedAt().atZone(ZoneId.of(team.getTimezone())).toLocalDate());
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/timer/active").with(user(principal)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        }
        mvc.perform(get("/api/timer/active").with(user(new AppUserPrincipal(other.getId(), other.getEmail(), "unused"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateStartsAcrossProjectsAndTeamsConflict() throws Exception {
        running(actor, project);
        fixtures.member(foreignTeam, actor, TeamRole.MEMBER, Instant.now());
        for (var target : new Project[]{project, foreignProject}) {
            mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(startBody(target.getTeam().getId(), target.getId())))
                    .andExpect(status().isConflict());
        }
        assertThat(entries.findAll()).filteredOn(e -> e.getUser().getId().equals(actor.getId())).hasSize(1);
    }

    @Test
    void stopComputesWholeSecondsPreservesStartDateAndRejectsRepeatedStop() throws Exception {
        var timer = running(actor, project);
        var started = timer.getStartedAt();
        var date = timer.getWorkDate();
        mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entryId":"%s","description":"  Finished  ","durationSeconds":1,
                                 "endedAt":"2000-01-01T00:00:00Z"}
                                """.formatted(timer.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Finished"));
        assertThat(timer.getStartedAt()).isEqualTo(started);
        assertThat(timer.getWorkDate()).isEqualTo(date);
        assertThat(timer.getDurationSeconds()).isEqualTo((int) Duration.between(started, timer.getEndedAt()).getSeconds());
        assertThat(timer.getDurationSeconds()).isPositive();
        var ended = timer.getEndedAt();
        mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryId\":\"" + timer.getId() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(timer.getEndedAt()).isEqualTo(ended);
        mvc.perform(get("/api/timer/active").with(user(principal))).andExpect(status().isNoContent());
        mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(team.getId(), project.getId())))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryId\":\"" + timer.getId() + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/timer/active").with(user(principal))).andExpect(status().isOk());
    }

    @Test
    void ownerCannotStopAnotherUsersTimerAndManualIsNotATimer() throws Exception {
        var timer = running(other, project);
        var manual = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.MANUAL, LocalDate.now(), null, null, 60, null));
        for (UUID id : new UUID[]{timer.getId(), UUID.randomUUID()}) {
            mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entryId\":\"" + id + "\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryId\":\"" + manual.getId() + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/timer/active").with(user(principal))).andExpect(status().isNoContent());
        mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(team.getId(), project.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void formerMemberCanRecoverAndStopOwnTimerWithoutCreatingNewEntries() throws Exception {
        var timer = running(actor, project);
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(Instant.now());
        members.flush();
        mvc.perform(get("/api/timer/active").with(user(principal))).andExpect(status().isOk());
        mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryId\":\"" + timer.getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Running"));
        mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(team.getId(), project.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualEntryMayCoexistWithActiveTimerAndIgnoresClientIdentityAndTimestamps() throws Exception {
        running(actor, project);
        mvc.perform(post("/api/time-entries").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","projectId":"%s","workDate":"2026-01-05","durationSeconds":3600,
                                 "description":"  Manual work  ","userId":"%s","entryType":"TIMER",
                                 "startedAt":"2026-01-05T00:00:00Z","endedAt":"2026-01-05T01:00:00Z"}
                                """.formatted(team.getId(), project.getId(), other.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(actor.getId().toString()))
                .andExpect(jsonPath("$.entryType").value("MANUAL"))
                .andExpect(jsonPath("$.workDate").value("2026-01-05"))
                .andExpect(jsonPath("$.durationSeconds").value(3600))
                .andExpect(jsonPath("$.startedAt").isEmpty()).andExpect(jsonPath("$.endedAt").isEmpty())
                .andExpect(jsonPath("$.description").value("Manual work"));
        mvc.perform(get("/api/timer/active").with(user(principal))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/timer/start", "/api/time-entries"})
    void newEntriesRequireActiveMembershipAndProjectInTheSameTeam(String path) throws Exception {
        for (String body : new String[]{body(path, foreignTeam.getId(), foreignProject.getId()),
                body(path, team.getId(), foreignProject.getId()), body(path, team.getId(), UUID.randomUUID()),
                body(path, UUID.randomUUID(), project.getId())}) {
            mvc.perform(post(path).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
        }
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(Instant.now());
        members.flush();
        mvc.perform(post(path).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(path, team.getId(), project.getId())))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/timer/start", "/api/time-entries"})
    void archivedProjectsCannotReceiveNewEntries(String path) throws Exception {
        project.setArchivedAt(Instant.now());
        projects.flush();
        mvc.perform(post(path).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(path, team.getId(), project.getId())))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/timer/start", "/api/timer/stop", "/api/time-entries"})
    void authenticationCsrfAndExistingUserAreRequired(String path) throws Exception {
        String body = path.endsWith("stop") ? "{\"entryId\":\"" + UUID.randomUUID() + "\"}" : body(path, team.getId(), project.getId());
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void activeRequiresAuthenticationAndExistingUser() throws Exception {
        mvc.perform(get("/api/timer/active")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/timer/active").with(user(new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused"))))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"teamId\":null}", "{\"teamId\":\"invalid\"}", "{"})
    void rejectsIncompleteRequests(String body) throws Exception {
        for (String path : new String[]{"/api/timer/start", "/api/timer/stop", "/api/time-entries"}) {
            mvc.perform(post(path).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "2147483648"})
    void manualDurationMustBePositiveAndFitDatabaseColumn(String duration) throws Exception {
        mvc.perform(post("/api/time-entries").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"2026-01-01\",\"durationSeconds\":%s}"
                                .formatted(team.getId(), project.getId(), duration)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualWorkDateIsRequiredAndMustBeValid() throws Exception {
        for (String date : new String[]{"null", "\"invalid\"", "\"2026-02-30\""}) {
            mvc.perform(post("/api/time-entries").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":%s,\"durationSeconds\":60}"
                                    .formatted(team.getId(), project.getId(), date)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void invalidStopDurationDoesNotMutateTimer() throws Exception {
        var timer = running(actor, project);
        for (Instant start : new Instant[]{Instant.now().plusSeconds(60), Instant.now().minusSeconds((long) Integer.MAX_VALUE + 60)}) {
            timer.setStartedAt(start);
            entries.flush();
            mvc.perform(post("/api/timer/stop").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entryId\":\"" + timer.getId() + "\"}"))
                    .andExpect(status().isConflict());
            assertThat(timer.getEndedAt()).isNull();
            assertThat(timer.getDurationSeconds()).isNull();
        }
    }

    private TimeEntry running(User user, Project project) {
        return entries.saveAndFlush(new TimeEntry(user, project.getTeam(), project, EntryType.TIMER,
                LocalDate.of(2026, 1, 1), Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS), null, null, "Running"));
    }

    private String startBody(UUID teamId, UUID projectId) {
        return "{\"teamId\":\"" + teamId + "\",\"projectId\":\"" + projectId + "\"}";
    }

    private String body(String path, UUID teamId, UUID projectId) {
        return path.endsWith("start") ? startBody(teamId, projectId)
                : "{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"2026-01-01\",\"durationSeconds\":60}"
                .formatted(teamId, projectId);
    }
}
