package pl.macieserafin.hourforge.report.integration;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.report.ReportGrouping;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.timeentry.*;
import pl.macieserafin.hourforge.user.User;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportIT extends ApiIntegrationTestSupport {
    @Test
    void undatedReportsUseTheSameScopeForTotalsGroupsAndPagedEntries() throws Exception {
        dataset();
        for (int seconds : new int[]{60, 120}) {
            entries.saveAndFlush(new TimeEntry(worker, team, archived, EntryType.MANUAL, null, null, null, seconds, "Undated"));
        }
        var foreign = fixtures.team("Foreign undated", "UTC");
        entries.saveAndFlush(new TimeEntry(worker, foreign, fixtures.project(foreign, "Other", null), EntryType.MANUAL,
                null, null, null, 999, null));
        mvc.perform(request("summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(450));
        mvc.perform(request("summary").param("undated", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(180))
                .andExpect(jsonPath("$.totals.entryCount").value(2)).andExpect(jsonPath("$.context.filter.from").isEmpty());
        for (ReportGrouping grouping : ReportGrouping.values()) {
            mvc.perform(request("breakdown").param("undated", "true").param("groupBy", grouping.name()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].durationSeconds").value(180));
        }
        for (int page = 0; page < 2; page++) {
            mvc.perform(request("entries").param("undated", "true").param("sort", "DURATION").param("direction", "ASC")
                            .param("page", String.valueOf(page)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].durationSeconds").value(60 + page * 60))
                    .andExpect(jsonPath("$.data.hasNext").value(page == 0));
        }
        for (String endpoint : List.of("summary", "breakdown", "entries")) {
            mvc.perform(request(endpoint).param("undated", "true").param("from", "2026-10-19").param("to", "2026-10-25"))
                    .andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("undated", "true").param("projectIds", project.getId().toString()))
                    .andExpect(status().isOk());
            mvc.perform(request(endpoint).param("undated", "true").with(user(principal(worker))))
                    .andExpect(status().isNotFound());
        }
    }

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamMemberRepository members;
    @MockitoBean Clock clock;
    private User owner;
    private User worker;
    private Team team;
    private Project project;
    private Project archived;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        owner = fixtures.user("report-owner@example.test", "Owner");
        worker = fixtures.user("report-worker@example.test", "Worker");
        team = fixtures.team("Studio", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, NOW.minusSeconds(10000));
        fixtures.member(team, worker, TeamRole.MEMBER, NOW.minusSeconds(10000));
        project = fixtures.project(team, "Current", "Project description");
        archived = fixtures.project(team, "Archived", null);
        archived.setArchivedAt(NOW);
    }

    private AppUserPrincipal principal(User actor) {
        return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
    }

    @Test
    void reportRangeIsLimitedTo366Days() throws Exception {
        for (String endpoint : List.of("summary", "breakdown", "entries", "active-timers")) {
            mvc.perform(request(endpoint).param("from", "2025-10-20").param("to", "2026-10-20")).andExpect(status().isOk());
            mvc.perform(request(endpoint).param("from", "2025-10-19").param("to", "2026-10-20")).andExpect(status().isBadRequest());
        }
    }

    @Test
    void descriptionSearchAppliesToTotalsGroupsAndEntriesIgnoringCaseAndTakingWildcardsLiterally() throws Exception {
        dataset();
        entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.MANUAL, LocalDate.parse("2026-10-21"),
                null, null, 45, "Rate 100% done_ok"));
        mvc.perform(request("summary").param("q", "TIMER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.durationSeconds").value(240)).andExpect(jsonPath("$.totals.entryCount").value(1))
                .andExpect(jsonPath("$.context.filter.q").value("TIMER"));
        mvc.perform(request("summary").param("q", "  detailed ")).andExpect(jsonPath("$.totals.durationSeconds").value(210));
        mvc.perform(request("summary").param("q", "%")).andExpect(jsonPath("$.totals.durationSeconds").value(45));
        mvc.perform(request("summary").param("q", "_ok")).andExpect(jsonPath("$.totals.durationSeconds").value(45));
        mvc.perform(request("summary").param("q", "d_n")).andExpect(jsonPath("$.totals.entryCount").value(0));
        mvc.perform(request("summary").param("q", "   ")).andExpect(jsonPath("$.totals.durationSeconds").value(495));
        mvc.perform(request("breakdown").param("q", "timer").param("groupBy", "MEMBER"))
                .andExpect(jsonPath("$.data.content", hasSize(1))).andExpect(jsonPath("$.data.content[0].displayName").value("Worker"));
        mvc.perform(request("entries").param("q", "work").param("sort", "DURATION").param("direction", "ASC"))
                .andExpect(jsonPath("$.data.content[*].description", everyItem(containsStringIgnoringCase("work"))));
        mvc.perform(request("entries").param("q", "Foreign")).andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void theLiveViewIsHiddenWhenTheTeamTurnsItOffWhileOtherReportsKeepWorking() throws Exception {
        dataset();
        mvc.perform(request("active-timers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1))).andExpect(jsonPath("$.data.content[0].entry.displayName").value("Owner"));
        team.setActiveTimersVisible(false);
        mvc.perform(request("active-timers")).andExpect(status().isForbidden());
        mvc.perform(request("summary")).andExpect(status().isOk());
        mvc.perform(request("entries")).andExpect(status().isOk());
        team.setActiveTimersVisible(true);
        mvc.perform(request("active-timers")).andExpect(status().isOk());
    }

    @Test
    void descriptionSearchIsLimitedTo200Characters() throws Exception {
        for (String endpoint : List.of("summary", "breakdown", "entries")) {
            mvc.perform(request(endpoint).param("q", "x".repeat(200))).andExpect(status().isOk());
            mvc.perform(request(endpoint).param("q", "x".repeat(201))).andExpect(status().isBadRequest());
        }
    }

    private MockHttpServletRequestBuilder request(String endpoint) {
        var request = get("/api/teams/" + team.getId() + "/reports/" + endpoint).with(user(principal(owner)));
        return request;
    }

    private TimeEntry manual(User actor, Project target, String date, int seconds) {
        return entries.saveAndFlush(new TimeEntry(actor, target.getTeam(), target, EntryType.MANUAL,
                LocalDate.parse(date), null, null, seconds, "Detailed work"));
    }

    private void dataset() {
        manual(owner, project, "2026-10-19", 60);
        manual(worker, archived, "2026-10-20", 120);
        manual(worker, project, "2026-10-25", 30);
        manual(worker, project, "2026-10-18", 1000);
        manual(worker, project, "2026-10-26", 1000);
        entries.saveAndFlush(new TimeEntry(worker, team, project, EntryType.TIMER,
                LocalDate.parse("2026-10-19"), NOW.minusSeconds(300), NOW, 240, "Timer work"));
        entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.TIMER,
                LocalDate.parse("2026-10-19"), NOW.minusSeconds(100), null, null, "In progress"));
        var foreign = fixtures.team("Foreign", "UTC");
        manual(worker, fixtures.project(foreign, "Foreign project", null), "2026-10-19", 9000);
        members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().setLeftAt(NOW);
    }

    @Test
    void sumsCompletedWorkAcrossMembersIncludingFormerMembersAndArchivedProjects() throws Exception {
        dataset();
        mvc.perform(request("summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.durationSeconds").value(450))
                .andExpect(jsonPath("$.totals.entryCount").value(4))
                .andExpect(jsonPath("$.context.teamName").value("Studio"))
                .andExpect(jsonPath("$.context.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.context.filter.from").value("2026-10-19"))
                .andExpect(jsonPath("$.context.filter.to").value("2026-10-25"));
    }

    @ParameterizedTest
    @EnumSource(ReportGrouping.class)
    void groupsInTheDatabaseWithoutLosingWorkOrMixingTeams(ReportGrouping grouping) throws Exception {
        dataset();
        Integer[] durations = switch (grouping) {
            case DAY, DAY_PROJECT -> new Integer[]{300, 120, 30};
            case MEMBER -> new Integer[]{60, 390};
            case PROJECT -> new Integer[]{330, 120};
            case MEMBER_PROJECT -> new Integer[]{60, 270, 120};
            case DAY_MEMBER -> new Integer[]{60, 240, 120, 30};
            case TAG, JIRA_ISSUE -> new Integer[]{450};
        };
        mvc.perform(request("breakdown").param("groupBy", grouping.name()).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value(grouping.name()))
                .andExpect(jsonPath("$.data.content", hasSize(durations.length)))
                .andExpect(jsonPath("$.data.content[*].durationSeconds", containsInAnyOrder(durations)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void usesTheSameCombinedFiltersForSummaryBreakdownAndDetails() throws Exception {
        dataset();
        for (String endpoint : List.of("summary", "breakdown", "entries")) {
            var result = mvc.perform(request(endpoint).param("from", "2026-10-19").param("to", "2026-10-20")
                    .param("userIds", worker.getId().toString()).param("projectIds", archived.getId().toString())
                    .param("entryType", "MANUAL"))
                    .andExpect(status().isOk());
            if (endpoint.equals("summary")) {
                result.andExpect(jsonPath("$.totals.durationSeconds").value(120)).andExpect(jsonPath("$.totals.entryCount").value(1));
            } else {
                result.andExpect(jsonPath("$.data.content", hasSize(1)))
                        .andExpect(jsonPath("$.data.content[0].durationSeconds").value(120));
            }
        }
    }

    @Test
    void supportsMultipleAndDuplicateFilterIdsAndLargeSums() throws Exception {
        manual(owner, project, "2026-10-19", 2_000_000_000);
        manual(worker, archived, "2026-10-19", 2_000_000_000);
        mvc.perform(request("summary").param("userIds", owner.getId().toString(), worker.getId().toString(), owner.getId().toString())
                        .param("projectIds", project.getId().toString(), archived.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(4_000_000_000L));
        mvc.perform(request("breakdown").param("groupBy", "DAY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].durationSeconds").value(4_000_000_000L));
    }

    @Test
    void returnsDetailedEntriesSortedAndPagedWithStableTies() throws Exception {
        dataset();
        mvc.perform(request("entries").param("sort", "DURATION").param("direction", "ASC").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[*].durationSeconds", contains(30, 60)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(request("entries").param("sort", "DURATION").param("direction", "ASC").param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[*].durationSeconds", contains(120, 240)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content[0].displayName").value("Worker"))
                .andExpect(jsonPath("$.data.content[0].projectName").value("Archived"))
                .andExpect(jsonPath("$.data.content[0].projectArchivedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].description").value("Detailed work"))
                .andExpect(jsonPath("$.data.content[1].startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].endedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].updatedAt").isNotEmpty());
        mvc.perform(request("breakdown").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void paginationDoesNotLoseEntriesOrGroupsWithEqualSortValuesAndDuplicateNames() throws Exception {
        var sameName = fixtures.project(team, project.getName(), null);
        manual(owner, project, "2026-10-19", 60);
        manual(owner, sameName, "2026-10-19", 60);
        manual(worker, project, "2026-10-19", 60);
        for (String sort : List.of("WORK_DATE", "DURATION", "CREATED_AT")) {
            for (String direction : List.of("ASC", "DESC")) {
                var full = mvc.perform(request("entries").param("sort", sort).param("direction", direction))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                List<String> fullIds = com.jayway.jsonpath.JsonPath.read(full, "$.data.content[*].id");
                var pagedIds = new ArrayList<String>();
                for (int page = 0; page < 3; page++) {
                    var body = mvc.perform(request("entries").param("sort", sort).param("direction", direction)
                                    .param("page", Integer.toString(page)).param("size", "1"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(page < 2))
                            .andReturn().getResponse().getContentAsString();
                    pagedIds.addAll(com.jayway.jsonpath.JsonPath.<List<String>>read(body, "$.data.content[*].id"));
                }
                assertThat(pagedIds).containsExactlyElementsOf(fullIds).doesNotHaveDuplicates();
            }
        }
        mvc.perform(request("breakdown").param("groupBy", "PROJECT").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(request("breakdown").param("groupBy", "PROJECT").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void countsWorkDateInsteadOfCreationOrTimerTimestampsAndFiltersTimerEntries() throws Exception {
        manual(worker, archived, "2000-01-01", 60);
        entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.TIMER,
                LocalDate.parse("2000-01-01"), NOW.minusSeconds(200), NOW, 100, "Corrected historical timer"));
        mvc.perform(request("summary").param("from", "2000-01-01").param("to", "2000-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(160));
        mvc.perform(request("entries").param("from", "2000-01-01").param("to", "2000-01-01").param("entryType", "TIMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].durationSeconds").value(100));
        mvc.perform(request("summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(0));
    }

    @Test
    void exposesHistoricalContextAndPagesFilterOptions() throws Exception {
        dataset();
        mvc.perform(request("members").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].userId").value(worker.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].leftAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());
        mvc.perform(request("projects").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("Archived"))
                .andExpect(jsonPath("$.data.content[0].archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void separatesRunningAndPausedTimersFromSavedTotalsIncludingOldSessions() throws Exception {
        var running = new TimeEntry(owner, team, project, EntryType.TIMER, LocalDate.parse("2026-10-01"), NOW.minusSeconds(200), null, null, "Running");
        running.setPausedDurationMicros(20_500_000);
        entries.saveAndFlush(running);
        var paused = new TimeEntry(worker, team, project, EntryType.TIMER, LocalDate.parse("2026-10-01"), NOW.minusSeconds(300), null, null, "Paused");
        paused.setPausedAt(NOW.minusSeconds(60));
        paused.setPausedDurationMicros(10_000_000);
        entries.saveAndFlush(paused);
        mvc.perform(request("active-timers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asOf").value("2026-10-19T10:00:00Z"))
                .andExpect(jsonPath("$.data.content[*].elapsedSeconds", containsInAnyOrder(179, 230)))
                .andExpect(jsonPath("$.data.content[0].entry.pausedDurationMicros").exists());
        mvc.perform(request("summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(0));
        mvc.perform(request("active-timers").param("userIds", worker.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)));
        mvc.perform(request("active-timers").param("entryType", "MANUAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
        mvc.perform(request("active-timers").param("from", "2026-10-19").param("to", "2026-10-25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
        mvc.perform(request("active-timers").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "breakdown", "entries", "active-timers", "members", "projects"})
    void enforcesOwnerAccessOnEveryEndpoint(String endpoint) throws Exception {
        mvc.perform(request(endpoint).with(user(principal(worker)))).andExpect(status().isForbidden());
        var outsider = fixtures.user("outsider@example.test", "Outsider");
        fixtures.member(fixtures.team("Other owned team", "UTC"), outsider, TeamRole.OWNER, NOW);
        mvc.perform(request(endpoint).with(user(principal(outsider)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/" + endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(request(endpoint).with(user(new AppUserPrincipal(UUID.randomUUID(), "deleted@example.test", "unused"))))
                .andExpect(status().isUnauthorized());
        members.findByTeamIdAndUserId(team.getId(), owner.getId()).orElseThrow().setLeftAt(NOW);
        mvc.perform(request(endpoint)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsForeignFilterIdsAndMalformedInputs() throws Exception {
        var foreign = fixtures.team("Other", "UTC");
        var foreignProject = fixtures.project(foreign, "Other project", null);
        var stranger = fixtures.user("stranger@example.test", "Stranger");
        for (String endpoint : List.of("summary", "breakdown", "entries", "active-timers")) {
            mvc.perform(request(endpoint).param("projectIds", foreignProject.getId().toString())).andExpect(status().isNotFound());
            mvc.perform(request(endpoint).param("userIds", stranger.getId().toString())).andExpect(status().isNotFound());
            mvc.perform(request(endpoint).param("from", "2026-10-20").param("to", "2026-10-19")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("from", "2026-10-19")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("from", "invalid").param("to", "2026-10-19")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("entryType", "INVALID")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("userIds", "invalid")).andExpect(status().isBadRequest());
        }
        for (String endpoint : List.of("breakdown", "entries", "active-timers", "members", "projects")) {
            mvc.perform(request(endpoint).param("page", "-1")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("size", "101")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("size", "0")).andExpect(status().isBadRequest());
            mvc.perform(request(endpoint).param("page", "2147483647")).andExpect(status().isBadRequest());
        }
        mvc.perform(request("breakdown").param("groupBy", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(request("entries").param("sort", "description")).andExpect(status().isBadRequest());
        mvc.perform(request("entries").param("direction", "INVALID")).andExpect(status().isBadRequest());
    }

    @Test
    void emptyPeriodsReturnZeroAndEmptyPages() throws Exception {
        mvc.perform(request("summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.durationSeconds").value(0)).andExpect(jsonPath("$.totals.entryCount").value(0));
        for (String endpoint : List.of("entries", "breakdown", "active-timers")) {
            mvc.perform(request(endpoint)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }
    }
}
