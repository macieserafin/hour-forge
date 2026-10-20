package pl.macieserafin.hourforge.timeentry.integration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimerChangeIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    @Autowired EntityManager entityManager;

    private User worker;
    private User other;
    private Team team;
    private Project first;
    private Project second;
    private TimeEntry timer;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        worker = fixtures.user("switch@example.com", "Switch");
        other = fixtures.user("other-switch@example.com", "Other");
        principal = new AppUserPrincipal(worker.getId(), worker.getEmail(), "unused");
        team = fixtures.team("Switch team", "UTC");
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, other, TeamRole.MEMBER, Instant.now());
        first = fixtures.project(team, "First", null);
        second = fixtures.project(team, "Second", null);
        timer = entries.saveAndFlush(new TimeEntry(worker, team, first, EntryType.TIMER, LocalDate.now(java.time.ZoneOffset.UTC),
                Instant.now().minusSeconds(300).truncatedTo(ChronoUnit.MICROS), null, null, "Original"));
    }

    private ResultActions call(String path, AppUserPrincipal who, String json) throws Exception {
        entityManager.flush();
        return mvc.perform(post(path).with(user(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String update(Project project, String description) {
        return "{\"entryId\":\"" + timer.getId() + "\",\"projectId\":\"" + project.getId() + "\""
                + (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
    }

    private String switchBody(Project next, String stopNote, String startNote) {
        return "{\"stop\":{\"entryId\":\"" + timer.getId() + "\",\"description\":" + (stopNote == null ? "null" : "\"" + stopNote + "\"")
                + "},\"start\":{\"teamId\":\"" + team.getId() + "\",\"projectId\":\"" + next.getId() + "\",\"description\":\"" + startNote + "\"}}";
    }

    @Test
    void changesTheProjectAndNoteOfARunningTimerWithoutStoppingIt() throws Exception {
        call("/api/timer/update", principal, update(second, "Now on the second")).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(second.getId().toString())).andExpect(jsonPath("$.description").value("Now on the second"))
                .andExpect(jsonPath("$.endedAt").doesNotExist()).andExpect(jsonPath("$.id").value(timer.getId().toString()));
        entityManager.clear();
        var stored = entries.findById(timer.getId()).orElseThrow();
        assertThat(stored.getProject().getId()).isEqualTo(second.getId());
        assertThat(stored.getEndedAt()).isNull();
        assertThat(stored.getStartedAt()).isEqualTo(timer.getStartedAt());
    }

    @Test
    void aMissingNoteKeepsTheOldOneAndABlankNoteClearsIt() throws Exception {
        call("/api/timer/update", principal, update(first, null)).andExpect(jsonPath("$.description").value("Original"));
        call("/api/timer/update", principal, update(first, "   ")).andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void worksOnAPausedTimerToo() throws Exception {
        timer.setPausedAt(Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS));
        entries.saveAndFlush(timer);
        call("/api/timer/update", principal, update(second, null)).andExpect(status().isOk()).andExpect(jsonPath("$.pausedAt").isNotEmpty());
    }

    @Test
    void refusesAnArchivedProjectAnotherTeamsProjectAndSomeoneElsesTimer() throws Exception {
        second.setArchivedAt(Instant.now());
        projects.saveAndFlush(second);
        call("/api/timer/update", principal, update(second, null)).andExpect(status().isConflict());
        var foreignTeam = fixtures.team("Foreign", "UTC");
        var foreign = fixtures.project(foreignTeam, "Foreign project", null);
        call("/api/timer/update", principal, update(foreign, null)).andExpect(status().isNotFound());
        call("/api/timer/update", new AppUserPrincipal(other.getId(), other.getEmail(), "unused"), update(first, "Hijack")).andExpect(status().isNotFound());
        entityManager.clear();
        assertThat(entries.findById(timer.getId()).orElseThrow().getDescription()).isEqualTo("Original");
    }

    @Test
    void aFinishedEntryCannotBeChangedThisWay() throws Exception {
        timer.setEndedAt(Instant.now());
        timer.setDurationSeconds(300);
        entries.saveAndFlush(timer);
        call("/api/timer/update", principal, update(second, null)).andExpect(status().isConflict());
    }

    @Test
    void validatesTheRequestAndNeedsCsrfAndSignIn() throws Exception {
        call("/api/timer/update", principal, "{}").andExpect(status().isBadRequest());
        call("/api/timer/update", principal, update(first, "x".repeat(2001))).andExpect(status().isBadRequest());
        call("/api/timer/update", principal, update(first, null).replace("}", ",\"jiraIssueId\":\"abc\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/timer/update").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(update(first, null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/timer/update").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update(first, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAJiraLinkWhenTheIntegrationIsOff() throws Exception {
        call("/api/timer/update", principal, update(first, null).replace("}", ",\"jiraIssueId\":\"10001\"}")).andExpect(status().isConflict());
    }

    @Test
    void switchingStopsTheOldTimerAndStartsTheNewOneTogether() throws Exception {
        call("/api/timer/switch", principal, switchBody(second, "Done with first", "Starting second")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stopped.id").value(timer.getId().toString())).andExpect(jsonPath("$.stopped.endedAt").isNotEmpty())
                .andExpect(jsonPath("$.stopped.description").value("Done with first"))
                .andExpect(jsonPath("$.started.projectId").value(second.getId().toString())).andExpect(jsonPath("$.started.endedAt").doesNotExist())
                .andExpect(jsonPath("$.started.description").value("Starting second"));
        entityManager.clear();
        assertThat(entries.findByUserIdAndEntryTypeAndEndedAtIsNull(worker.getId(), EntryType.TIMER)).get()
                .extracting(entry -> entry.getProject().getId()).isEqualTo(second.getId());
        var old = entries.findById(timer.getId()).orElseThrow();
        assertThat(old.getDurationSeconds()).isBetween(299, 330);
    }

    @Test
    void ifTheNewTimerCannotStartTheOldOneKeepsRunning() throws Exception {
        second.setArchivedAt(Instant.now());
        projects.saveAndFlush(second);
        call("/api/timer/switch", principal, switchBody(second, "Note", "Next")).andExpect(status().isConflict());
        entityManager.clear();
        var still = entries.findById(timer.getId()).orElseThrow();
        assertThat(still.getEndedAt()).isNull();
        assertThat(still.getDescription()).isEqualTo("Original");
        assertThat(entries.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void switchingWithinTheFirstSecondIsRefusedAndChangesNothing() throws Exception {
        timer.setStartedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        entries.saveAndFlush(timer);
        call("/api/timer/switch", principal, switchBody(second, "x", "y")).andExpect(status().isConflict());
        entityManager.clear();
        assertThat(entries.findById(timer.getId()).orElseThrow().getEndedAt()).isNull();
        assertThat(entries.findByUserIdAndEntryTypeAndEndedAtIsNull(worker.getId(), EntryType.TIMER)).isPresent();
    }

    @Test
    void switchingNeedsTheRunningTimerOfTheCaller() throws Exception {
        call("/api/timer/switch", new AppUserPrincipal(other.getId(), other.getEmail(), "unused"), switchBody(second, "x", "y")).andExpect(status().isNotFound());
        call("/api/timer/switch", principal, "{}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/timer/switch").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(switchBody(second, "x", "y")))
                .andExpect(status().isForbidden());
    }
}
