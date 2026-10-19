package pl.macieserafin.hourforge.timeentry.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimeEntryHistoryIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    @Autowired JdbcTemplate jdbc;

    private User actor;
    private User other;
    private Team team;
    private Project project;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        actor = fixtures.user("history@example.com", "Reader");
        other = fixtures.user("history-other@example.com", "Other");
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
        team = fixtures.team("History", "Europe/Warsaw");
        fixtures.member(team, actor, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, other, TeamRole.OWNER, Instant.now());
        project = fixtures.project(team, "Project", null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void listsOnlyOwnCompletedEntriesAndIncludesArchivedProjects(boolean paused) throws Exception {
        var manual = manual(actor, project, "2026-10-18");
        project.setArchivedAt(Instant.now());
        var timer = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.TIMER,
                LocalDate.parse("2026-10-19"), Instant.parse("2026-10-19T08:00:00Z"),
                Instant.parse("2026-10-19T09:00:00Z"), 3600, "Finished work"));
        manual(other, project, "2026-10-20");
        var active = new TimeEntry(actor, team, project, EntryType.TIMER, LocalDate.parse("2026-10-20"),
                Instant.now(), null, null, "In progress");
        if (paused) active.setPausedAt(Instant.now());
        entries.saveAndFlush(active);
        var secondTeam = fixtures.team("Another team", "UTC");
        fixtures.member(secondTeam, actor, TeamRole.MEMBER, Instant.now());
        manual(actor, fixtures.project(secondTeam, "Elsewhere", null), "2026-10-21");

        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString())
                        .param("userId", other.getId().toString()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains(timer.getId().toString(), manual.getId().toString())))
                .andExpect(jsonPath("$.content[0].projectName").value("Project"))
                .andExpect(jsonPath("$.content[0].durationSeconds").value(3600))
                .andExpect(jsonPath("$.content[0].description").value("Finished work"))
                .andExpect(jsonPath("$.content[1].entryType").value("MANUAL"))
                .andExpect(jsonPath("$.content[1].endedAt").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void filtersByProjectAndInclusiveDateRange() throws Exception {
        var first = manual(actor, project, "2026-10-01");
        var last = manual(actor, project, "2026-10-19");
        manual(actor, project, "2026-09-30");
        manual(actor, project, "2026-10-20");
        var another = fixtures.project(team, "Another project", null);
        manual(actor, another, "2026-10-10");
        project.setArchivedAt(Instant.now());
        mvc.perform(get("/api/time-entries").with(user(principal))
                        .param("teamId", team.getId().toString()).param("projectId", project.getId().toString())
                        .param("from", "2026-10-01").param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains(last.getId().toString(), first.getId().toString())))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void supportsOpenEndedDateRanges() throws Exception {
        manual(actor, project, "2026-10-01");
        manual(actor, project, "2026-10-19");
        mvc.perform(get("/api/time-entries").with(user(principal))
                        .param("teamId", team.getId().toString()).param("from", "2026-10-19"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].workDate").value("2026-10-19"));
        mvc.perform(get("/api/time-entries").with(user(principal))
                        .param("teamId", team.getId().toString()).param("to", "2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].workDate").value("2026-10-01"));
    }

    @Test
    void paginatesWithDeterministicOrderingWhenDatesAndCreationTimesMatch() throws Exception {
        var first = manual(actor, project, "2026-10-19");
        var second = manual(actor, project, "2026-10-19");
        jdbc.update("UPDATE time_entries SET created_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.from(Instant.parse("2026-10-19T10:00:00Z")), first.getId(), second.getId());
        var ordered = java.util.stream.Stream.of(first.getId(), second.getId())
                .map(UUID::toString).sorted().toList();
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/time-entries").with(user(principal))
                            .param("teamId", team.getId().toString()).param("size", "1").param("page", "" + page))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].id").value(contains(ordered.get(page))))
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
        mvc.perform(get("/api/time-entries").with(user(principal))
                        .param("teamId", team.getId().toString()).param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void returnsEmptyPageForTeamWithoutOwnEntries() throws Exception {
        manual(other, project, "2026-10-19");
        mvc.perform(get("/api/time-entries").with(user(principal)).param("teamId", team.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void ownerAlsoSeesOnlyOwnEntries() throws Exception {
        manual(actor, project, "2026-10-19");
        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString())
                        .with(user(new AppUserPrincipal(other.getId(), other.getEmail(), "unused"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void rejectsMissingForeignAndDepartedMemberships() throws Exception {
        var foreign = fixtures.team("Foreign", "UTC");
        for (var id : new UUID[]{foreign.getId(), UUID.randomUUID()}) {
            mvc.perform(get("/api/time-entries").with(user(principal)).param("teamId", id.toString()))
                    .andExpect(status().isNotFound());
        }
        manual(actor, project, "2026-10-19");
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(Instant.now());
        mvc.perform(get("/api/time-entries").with(user(principal)).param("teamId", team.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsProjectFromAnotherTeamOrMissingProject() throws Exception {
        var anotherTeam = fixtures.team("Another", "UTC");
        var foreign = fixtures.project(anotherTeam, "Foreign", null);
        for (var id : new UUID[]{foreign.getId(), UUID.randomUUID()}) {
            mvc.perform(get("/api/time-entries").with(user(principal))
                            .param("teamId", team.getId().toString()).param("projectId", id.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=2147483647", "page=bad", "size=bad",
            "from=bad", "to=2026-02-30", "projectId=bad", "from=2026-10-20&to=2026-10-01"})
    void rejectsInvalidFilters(String query) throws Exception {
        mvc.perform(get("/api/time-entries?teamId=" + team.getId() + "&" + query).with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresValidTeamIdAndAuthentication() throws Exception {
        mvc.perform(get("/api/time-entries").with(user(principal))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/time-entries").param("teamId", "bad").with(user(principal)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString())
                        .with(user(new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused"))))
                .andExpect(status().isUnauthorized());
    }

    private TimeEntry manual(User owner, Project target, String date) {
        return entries.saveAndFlush(new TimeEntry(owner, target.getTeam(), target, EntryType.MANUAL,
                LocalDate.parse(date), null, null, 120, "Manual work"));
    }
}
