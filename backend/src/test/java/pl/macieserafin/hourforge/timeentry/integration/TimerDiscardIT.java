package pl.macieserafin.hourforge.timeentry.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimerDiscardIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TeamMemberRepository members;
    @Autowired TimeEntryRepository entries;
    private User actor;
    private Team team;
    private Project project;
    private TimeEntry timer;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        actor = fixtures.user("discard@example.com", "Discard Test");
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
        team = fixtures.team("Discard team", "UTC");
        fixtures.member(team, actor, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Discard project", null);
        timer = timer(actor);
    }

    @Test
    void discardingRemovesRunningTimerWithoutLeavingAnyEntryAndFreesTheTimerSlot() throws Exception {
        discard(body(timer), 204);
        assertThat(entries.findById(timer.getId())).isEmpty();
        assertThat(entries.countByTeamId(team.getId())).isZero();
        discard(body(timer), 404);
        mvc.perform(post("/api/timer/start").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"projectId\":\"%s\"}".formatted(team.getId(), project.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void pausedTimerCanBeDiscarded() throws Exception {
        timer.setPausedAt(timer.getStartedAt().plusSeconds(30));
        entries.flush();
        discard(body(timer), 204);
        assertThat(entries.findById(timer.getId())).isEmpty();
    }

    @Test
    void formerMemberCanStillDiscardOwnTimer() throws Exception {
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(Instant.now());
        members.flush();
        discard(body(timer), 204);
        assertThat(entries.findById(timer.getId())).isEmpty();
    }

    @Test
    void completedTimersAndManualEntriesCannotBeDiscarded() throws Exception {
        var finished = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.TIMER, LocalDate.now(),
                Instant.now().minusSeconds(100), Instant.now().minusSeconds(40), 60, null));
        var manual = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.MANUAL, LocalDate.now(),
                null, null, 60, null));
        discard(body(finished), 409);
        discard(body(manual), 409);
        assertThat(entries.findById(finished.getId())).isPresent();
        assertThat(entries.findById(manual.getId())).isPresent();
        assertThat(entries.findById(timer.getId())).isPresent();
    }

    @Test
    void requiresAuthenticationCsrfOwnershipAndEntryId() throws Exception {
        mvc.perform(post("/api/timer/discard").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(timer)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/timer/discard").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body(timer)))
                .andExpect(status().isForbidden());
        discard("{}", 400);
        discard("{\"entryId\":\"" + UUID.randomUUID() + "\"}", 404);
        var other = fixtures.user("discard-other@example.com", "Other");
        mvc.perform(post("/api/timer/discard").with(user(new AppUserPrincipal(other.getId(), other.getEmail(), "unused")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(timer)))
                .andExpect(status().isNotFound());
        assertThat(entries.findById(timer.getId())).isPresent();
    }

    private TimeEntry timer(User owner) {
        return entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.TIMER, LocalDate.now(),
                Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS), null, null, "Mistake"));
    }

    private String body(TimeEntry entry) { return "{\"entryId\":\"" + entry.getId() + "\"}"; }

    private void discard(String body, int status) throws Exception {
        mvc.perform(post("/api/timer/discard").with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
    }
}
