package pl.macieserafin.hourforge.dashboard.integration;

import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.project.Project;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardOverviewIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;

    private final LocalDate today = LocalDate.now(ZoneId.of("UTC"));
    private User worker;
    private User colleague;
    private Team alpha;
    private Team beta;
    private Project alphaProject;
    private Project betaProject;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        worker = fixtures.user("overview@example.com", "Overview");
        colleague = fixtures.user("colleague-overview@example.com", "Colleague");
        alpha = fixtures.team("Alpha", "UTC");
        beta = fixtures.team("beta", "UTC");
        fixtures.member(alpha, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(beta, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(alpha, colleague, TeamRole.MEMBER, Instant.now());
        alphaProject = fixtures.project(alpha, "A", null);
        betaProject = fixtures.project(beta, "B", null);
        principal = new AppUserPrincipal(worker.getId(), worker.getEmail(), "unused");
    }

    private void log(User person, Team team, Project project, LocalDate date, int seconds) {
        entries.saveAndFlush(new TimeEntry(person, team, project, EntryType.MANUAL, date, null, null, seconds, null));
    }

    @Test
    void sumsTodayAndTheWeekAcrossAllTeamsAndListsEachTeam() throws Exception {
        log(worker, alpha, alphaProject, today, 3600);
        log(worker, beta, betaProject, today, 1800);
        log(colleague, alpha, alphaProject, today, 9999);
        var monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        log(worker, beta, betaProject, monday, 600);

        mvc.perform(get("/api/dashboard/overview").with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.todaySeconds").value(monday.equals(today) ? 6000 : 5400))
                .andExpect(jsonPath("$.weekSeconds").value(6000))
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[0].teamName").value("Alpha")).andExpect(jsonPath("$.teams[0].weekSeconds").value(3600))
                .andExpect(jsonPath("$.teams[1].teamName").value("beta")).andExpect(jsonPath("$.teams[1].weekSeconds").value(2400));
    }

    @Test
    void leftTeamsDropOutAndArchivedOnesAreFlagged() throws Exception {
        log(worker, alpha, alphaProject, today, 100);
        log(worker, beta, betaProject, today, 200);
        var membership = members.findByTeamIdAndUserId(alpha.getId(), worker.getId()).orElseThrow();
        membership.setLeftAt(Instant.now());
        members.saveAndFlush(membership);
        var frozen = teams.findById(beta.getId()).orElseThrow();
        frozen.setArchivedAt(Instant.now());
        teams.saveAndFlush(frozen);

        mvc.perform(get("/api/dashboard/overview").with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.teams.length()").value(1)).andExpect(jsonPath("$.teams[0].archived").value(true))
                .andExpect(jsonPath("$.weekSeconds").value(200));
    }

    @Test
    void needsSignInAndAKnownWeekStart() throws Exception {
        mvc.perform(get("/api/dashboard/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/overview").param("weekStart", "FRIDAY").with(user(principal))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/activity")).andExpect(status().isUnauthorized());
    }

    @Test
    void activityListsEveryDayOfWholeWeeksWithTheTimeOfAllTeams() throws Exception {
        log(worker, alpha, alphaProject, today, 3600);
        log(worker, beta, betaProject, today, 1800);
        log(worker, beta, betaProject, today.minusDays(40), 7200);
        log(colleague, alpha, alphaProject, today, 9999);
        entries.saveAndFlush(new TimeEntry(worker, alpha, alphaProject, EntryType.MANUAL, null, null, null, 5000, "undated"));

        var json = mvc.perform(get("/api/dashboard/activity").with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(84)).andExpect(jsonPath("$.maxSeconds").value(7200))
                .andReturn().getResponse().getContentAsString();
        var from = LocalDate.parse(JsonPath.<String>read(json, "$.from"));
        var to = LocalDate.parse(JsonPath.<String>read(json, "$.to"));
        assertThat(from.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(to).isEqualTo(from.plusDays(83));
        assertThat(LocalDate.parse(JsonPath.<String>read(json, "$.today"))).isEqualTo(today);
        assertThat(to).isAfterOrEqualTo(today);
        assertThat(JsonPath.<java.util.List<Integer>>read(json, "$.days[?(@.workDate=='" + today + "')].seconds").get(0)).isEqualTo(5400);
        assertThat(JsonPath.<java.util.List<Integer>>read(json, "$.days[?(@.workDate=='" + today.minusDays(40) + "')].seconds").get(0)).isEqualTo(7200);
    }

    @Test
    void activityIncludesTimeFromTeamsThePersonLeftAndFollowsTheWeekStart() throws Exception {
        log(worker, alpha, alphaProject, today, 600);
        var membership = members.findByTeamIdAndUserId(alpha.getId(), worker.getId()).orElseThrow();
        membership.setLeftAt(Instant.now());
        members.saveAndFlush(membership);

        var json = mvc.perform(get("/api/dashboard/activity").param("weeks", "4").param("weekStart", "SUNDAY").with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(28)).andReturn().getResponse().getContentAsString();
        assertThat(LocalDate.parse(JsonPath.<String>read(json, "$.from")).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(JsonPath.<java.util.List<Integer>>read(json, "$.days[?(@.workDate=='" + today + "')].seconds").get(0)).isEqualTo(600);
    }

    @Test
    void activityLimitsTheNumberOfWeeks() throws Exception {
        mvc.perform(get("/api/dashboard/activity").param("weeks", "3").with(user(principal))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/activity").param("weeks", "27").with(user(principal))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/activity").param("weeks", "26").with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(182));
    }
}
