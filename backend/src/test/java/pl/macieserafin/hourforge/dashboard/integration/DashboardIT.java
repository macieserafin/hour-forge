package pl.macieserafin.hourforge.dashboard.integration;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.timeentry.*;
import pl.macieserafin.hourforge.user.User;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardIT extends ApiIntegrationTestSupport {
    @Test
    void undatedManualWorkDoesNotEnterDailyOrWeeklyTotals() throws Exception {
        entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.MANUAL, null, null, null, 3600, "Undated"));
        mvc.perform(get("/api/dashboard/summary").with(user(principal)).param("teamId", team.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.todaySeconds").value(0))
                .andExpect(jsonPath("$.weekSeconds").value(0));
    }

    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamMemberRepository members;
    @MockitoBean Clock clock;
    private User actor;
    private Team team;
    private Project project;
    private AppUserPrincipal principal;
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        actor = fixtures.user("dashboard@example.test", "Actor");
        team = fixtures.team("Studio", "UTC");
        fixtures.member(team, actor, TeamRole.MEMBER, NOW);
        project = fixtures.project(team, "Project", null);
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
    }

    private void manual(User owner, Project target, String day, int seconds) {
        entries.saveAndFlush(new TimeEntry(owner, target.getTeam(), target, EntryType.MANUAL,
                LocalDate.parse(day), null, null, seconds, "Work"));
    }

    @Test
    void sumsOnlyOwnCompletedEntriesInTheSelectedWeekIncludingArchivedProjectsWithoutIntegerOverflow() throws Exception {
        manual(actor, project, "2026-10-19", 2_000_000_000);
        entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.TIMER, LocalDate.parse("2026-10-19"),
                NOW.minusSeconds(60), NOW, 2_000_000_000, "Corrected timer"));
        manual(actor, project, "2026-10-25", 60);
        manual(actor, project, "2026-10-18", 600);
        manual(actor, project, "2026-10-26", 600);
        var other = fixtures.user("dashboard-other@example.test", "Other");
        fixtures.member(team, other, TeamRole.OWNER, NOW);
        manual(other, project, "2026-10-19", 900);
        var otherTeam = fixtures.team("Other", "UTC");
        fixtures.member(otherTeam, actor, TeamRole.OWNER, NOW);
        manual(actor, fixtures.project(otherTeam, "Other", null), "2026-10-19", 1000);
        var active = new TimeEntry(actor, team, project, EntryType.TIMER, LocalDate.parse("2026-10-19"), NOW, null, null, null);
        active.setPausedAt(NOW);
        entries.saveAndFlush(active);
        project.setArchivedAt(NOW);
        mvc.perform(get("/api/dashboard/summary").with(user(principal)).param("teamId", team.getId().toString())
                        .param("userId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.todaySeconds").value(4_000_000_000L))
                .andExpect(jsonPath("$.weekSeconds").value(4_000_000_060L))
                .andExpect(jsonPath("$.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-25"));
    }

    @Test
    void returnsZeroForAnEmptyTeamAndRequiresAuthenticationAndValidTeam() throws Exception {
        mvc.perform(get("/api/dashboard/summary").with(user(principal)).param("teamId", team.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.todaySeconds").value(0))
                .andExpect(jsonPath("$.weekSeconds").value(0));
        mvc.perform(get("/api/dashboard/summary").param("teamId", team.getId().toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/summary").with(user(principal))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/summary").with(user(principal)).param("teamId", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/summary").with(user(new AppUserPrincipal(UUID.randomUUID(), "deleted@example.test", "unused")))
                        .param("teamId", team.getId().toString())).andExpect(status().isUnauthorized());
    }

    @Test
    void blocksOtherTeamsAndFormerMembers() throws Exception {
        var foreign = fixtures.team("Foreign", "UTC");
        for (var id : new UUID[]{foreign.getId(), UUID.randomUUID()}) {
            mvc.perform(get("/api/dashboard/summary").with(user(principal)).param("teamId", id.toString()))
                    .andExpect(status().isNotFound());
        }
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(NOW);
        mvc.perform(get("/api/dashboard/summary").with(user(principal)).param("teamId", team.getId().toString()))
                .andExpect(status().isNotFound());
    }
}
