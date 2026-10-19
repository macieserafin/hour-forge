package pl.macieserafin.hourforge.timeentry.integration;

import java.time.Instant;
import java.time.LocalDate;
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
import pl.macieserafin.hourforge.timeentry.StopTimerRequest;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.timeentry.TimeEntryService;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimerPauseIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    @Autowired TimeEntryService service;
    private User actor;
    private Team team;
    private Project project;
    private TimeEntry timer;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        actor = fixtures.user("pause@example.com", "Pause Test");
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
        team = fixtures.team("Pause team", "UTC");
        fixtures.member(team, actor, TeamRole.OWNER, Instant.now());
        project = fixtures.project(team, "Pause project", null);
        timer = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.TIMER, LocalDate.now(),
                Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS), null, null, "Original"));
    }

    @Test
    void pausePersistsDescriptionAndCanBeRecoveredWhileKeepingGlobalTimerSlot() throws Exception {
        action("pause", "{\"entryId\":\"%s\",\"description\":\" Draft \"}".formatted(timer.getId()), 200);
        var pausedAt = timer.getPausedAt();
        assertThat(pausedAt).isNotNull();
        mvc.perform(get("/api/timer/active").with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pausedAt").value(pausedAt.toString()))
                .andExpect(jsonPath("$.description").value("Draft"))
                .andExpect(jsonPath("$.durationSeconds").isEmpty());
        action("pause", body(), 409);
        assertThat(timer.getPausedAt()).isEqualTo(pausedAt);
        mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"projectId\":\"%s\"}".formatted(team.getId(), project.getId())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/teams/" + team.getId() + "/projects/" + project.getId() + "/archive")
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void finishingDuringPauseExcludesCurrentAndEarlierBreaksExactly() throws Exception {
        timer.setPausedAt(timer.getStartedAt().plusSeconds(30));
        timer.setPausedDurationMicros(10_000_000);
        entries.flush();
        action("stop", body(), 200);
        assertThat(timer.getDurationSeconds()).isEqualTo(20);
        assertThat(timer.getPausedAt()).isNull();
        assertThat(timer.getPausedDurationMicros()).isGreaterThanOrEqualTo(100_000_000);
        assertThat(timer.getEndedAt()).isAfter(timer.getStartedAt().plusSeconds(100));
        action("resume", body(), 409);
        action("pause", body(), 409);
    }

    @Test
    void repeatedPauseResumeCyclesAccumulateBreaksWithoutChangingEntryOrDate() throws Exception {
        timer.setPausedAt(timer.getStartedAt().plusSeconds(30));
        entries.flush();
        var workDate = timer.getWorkDate();
        action("resume", body(), 200);
        long firstPause = timer.getPausedDurationMicros();
        assertThat(firstPause).isGreaterThanOrEqualTo(90_000_000);
        assertThat(timer.getPausedAt()).isNull();
        action("resume", body(), 409);
        action("pause", body(), 200);
        // Simulate a further 5-second break without sleeping or altering the counted work.
        timer.setStartedAt(timer.getStartedAt().minusSeconds(5));
        timer.setPausedAt(timer.getPausedAt().minusSeconds(5));
        entries.flush();
        action("resume", body(), 200);
        assertThat(timer.getPausedDurationMicros()).isGreaterThanOrEqualTo(firstPause + 5_000_000);
        var finished = service.stop(actor.getId(), new StopTimerRequest(timer.getId(), null));
        assertThat(finished.durationSeconds()).isBetween(30, 35);
        assertThat(finished.workDate()).isEqualTo(workDate);
        assertThat(finished.id()).isEqualTo(timer.getId());
    }

    @Test
    void fractionalBreaksAreNotRoundedAwayBeforeFinalDurationCalculation() throws Exception {
        timer.setPausedAt(timer.getStartedAt().plusSeconds(30).plusMillis(500));
        timer.setPausedDurationMicros(750_000);
        entries.flush();
        action("stop", body(), 200);
        assertThat(timer.getDurationSeconds()).isEqualTo(29);
    }

    @Test
    void formerMemberMayFinishButCannotResumeWork() throws Exception {
        action("pause", body(), 200);
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(Instant.now());
        members.flush();
        action("resume", body(), 404);
        assertThat(timer.getPausedAt()).isNotNull();
        action("stop", body(), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pause", "resume"})
    void actionsRequireAuthenticationCsrfOwnershipAndEntryId(String action) throws Exception {
        mvc.perform(post("/api/timer/" + action).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/timer/" + action).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden());
        action(action, "{}", 400);
        action(action, "{\"entryId\":\"" + UUID.randomUUID() + "\"}", 404);
        var other = fixtures.user("other-pause@example.com", "Other");
        mvc.perform(post("/api/timer/" + action).with(user(new AppUserPrincipal(other.getId(), other.getEmail(), "unused")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNotFound());
        var manual = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.MANUAL, LocalDate.now(), null, null, 60, null));
        action(action, "{\"entryId\":\"" + manual.getId() + "\"}", 409);
    }

    private String body() { return "{\"entryId\":\"" + timer.getId() + "\"}"; }

    private void action(String action, String body, int status) throws Exception {
        mvc.perform(post("/api/timer/" + action).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
    }
}
