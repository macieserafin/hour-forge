package pl.macieserafin.hourforge.timeentry.integration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntryEditAndCopyIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamRepository teams;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private User worker;
    private User colleague;
    private User owner;
    private Team team;
    private Project project;
    private Project other;
    private AppUserPrincipal principal;
    private AppUserPrincipal ownerPrincipal;

    @BeforeEach
    void setup() {
        worker = fixtures.user("copy@example.com", "Copy");
        colleague = fixtures.user("colleague-copy@example.com", "Colleague");
        owner = fixtures.user("owner-copy@example.com", "Owner");
        team = fixtures.team("Copy team", "UTC");
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, colleague, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        project = fixtures.project(team, "Website", null);
        other = fixtures.project(team, "Other", null);
        principal = new AppUserPrincipal(worker.getId(), worker.getEmail(), "unused");
        ownerPrincipal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
    }

    private TimeEntry manual(User person, Project target, LocalDate date, int seconds, String description) {
        return entries.saveAndFlush(new TimeEntry(person, team, target, EntryType.MANUAL, date, null, null, seconds, description));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, AppUserPrincipal who, String body) throws Exception {
        entityManager.flush();
        return mvc.perform(request.with(user(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String edit(Project target, LocalDate date, int seconds, String description) {
        return "{\"projectId\":\"" + target.getId() + "\",\"workDate\":\"" + date + "\",\"durationSeconds\":" + seconds
                + (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
    }

    @Test
    void editingAnEntryMarksItAndSavingWithoutChangesDoesNot() throws Exception {
        var entry = manual(worker, project, today.minusDays(1), 600, "Original");
        assertThat(entry.getLastEditedAt()).isNull();

        send(patch("/api/time-entries/" + entry.getId()), principal, edit(project, today.minusDays(1), 600, "Original")).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertThat(entries.findById(entry.getId()).orElseThrow().getLastEditedAt()).isNull();

        send(patch("/api/time-entries/" + entry.getId()), principal, edit(project, today.minusDays(1), 900, "Original")).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertThat(entries.findById(entry.getId()).orElseThrow().getLastEditedAt()).isNotNull();
    }

    @Test
    void theMarkAppearsInTheHistoryAndTheTeamReportButNotOnNewEntries() throws Exception {
        var edited = manual(worker, project, today.minusDays(1), 600, "Will change");
        manual(worker, other, today.minusDays(1), 300, "Untouched");
        send(patch("/api/time-entries/" + edited.getId()), principal, edit(project, today.minusDays(2), 600, "Will change")).andExpect(status().isOk());
        entityManager.flush();

        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString()).with(user(principal)))
                .andExpect(jsonPath("$.content[?(@.description=='Will change')].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.description=='Untouched')].editedAt").value((Object) null));
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/entries").param("from", today.minusDays(5).toString()).param("to", today.toString())
                        .with(user(ownerPrincipal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.description=='Will change')].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[?(@.description=='Untouched')].editedAt").value((Object) null));
    }

    @Test
    void copiesAWholeDayAsManualEntriesAndOnlyTheCallersOwn() throws Exception {
        manual(worker, project, today.minusDays(1), 3600, "Morning");
        manual(worker, other, today.minusDays(1), 1800, null);
        manual(colleague, project, today.minusDays(1), 7200, "Not mine");
        entries.saveAndFlush(new TimeEntry(worker, team, project, EntryType.TIMER, today.minusDays(1), Instant.now().minus(30, ChronoUnit.HOURS), Instant.now().minus(29, ChronoUnit.HOURS), 3600, "Timer work"));

        send(post("/api/time-entries/copy-day"), principal, "{\"teamId\":\"" + team.getId() + "\",\"fromDate\":\"" + today.minusDays(1) + "\",\"toDate\":\"" + today + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.copied").value(3)).andExpect(jsonPath("$.totalSeconds").value(9000));
        entityManager.clear();
        var copies = entries.findDayOf(worker.getId(), team.getId(), today);
        assertThat(copies).hasSize(3).allMatch(copy -> copy.getEntryType() == EntryType.MANUAL).allMatch(copy -> copy.getLastEditedAt() == null);
        assertThat(copies).extracting(TimeEntry::getDescription).containsExactlyInAnyOrder("Morning", null, "Timer work");
        assertThat(entries.findDayOf(colleague.getId(), team.getId(), today)).isEmpty();
        assertThat(entries.findDayOf(worker.getId(), team.getId(), today.minusDays(1))).hasSize(3);
    }

    @Test
    void refusesWhatCannotBeCopied() throws Exception {
        var body = "{\"teamId\":\"" + team.getId() + "\",\"fromDate\":\"%s\",\"toDate\":\"%s\"}";
        send(post("/api/time-entries/copy-day"), principal, body.formatted(today.minusDays(1), today)).andExpect(status().isNotFound());
        manual(worker, project, today.minusDays(1), 60, "x");
        send(post("/api/time-entries/copy-day"), principal, body.formatted(today.minusDays(1), today.minusDays(1))).andExpect(status().isBadRequest());
        send(post("/api/time-entries/copy-day"), principal, body.formatted(today.minusDays(1), today.plusDays(1))).andExpect(status().isBadRequest());
        send(post("/api/time-entries/copy-day"), principal, body.formatted(today.minusDays(1), today.minusYears(3))).andExpect(status().isBadRequest());
        send(post("/api/time-entries/copy-day"), principal, "{}").andExpect(status().isBadRequest());
        var stranger = fixtures.user("stranger-copy@example.com", "Stranger");
        send(post("/api/time-entries/copy-day"), new AppUserPrincipal(stranger.getId(), stranger.getEmail(), "unused"), body.formatted(today.minusDays(1), today))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/time-entries/copy-day").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body.formatted(today.minusDays(1), today)))
                .andExpect(status().isForbidden());
        assertThat(entries.findDayOf(worker.getId(), team.getId(), today)).isEmpty();
    }

    @Test
    void anArchivedTeamCannotReceiveCopiesAndTooManyEntriesAreRefusedWholesale() throws Exception {
        for (int i = 0; i < 101; i++) manual(worker, project, today.minusDays(1), 60, "n" + i);
        var body = "{\"teamId\":\"" + team.getId() + "\",\"fromDate\":\"" + today.minusDays(1) + "\",\"toDate\":\"" + today + "\"}";
        send(post("/api/time-entries/copy-day"), principal, body).andExpect(status().isBadRequest());
        assertThat(entries.findDayOf(worker.getId(), team.getId(), today)).isEmpty();

        var frozen = teams.findById(team.getId()).orElseThrow();
        frozen.setArchivedAt(Instant.now());
        teams.saveAndFlush(frozen);
        send(post("/api/time-entries/copy-day"), principal, body).andExpect(status().isConflict());
    }
}
