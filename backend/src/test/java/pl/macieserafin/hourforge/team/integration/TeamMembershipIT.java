package pl.macieserafin.hourforge.team.integration;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.invitation.*;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.timeentry.*;
import pl.macieserafin.hourforge.user.User;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamMembershipIT extends ApiIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired TeamMemberRepository members;
    @Autowired TimeEntryRepository entries;
    @Autowired InvitationService invitations;
    @MockitoBean Clock clock;
    private User owner;
    private User worker;
    private User outsider;
    private Team team;
    private pl.macieserafin.hourforge.project.Project project;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        owner = fixtures.user("membership-owner@example.test", "Owner");
        worker = fixtures.user("membership-worker@example.test", "Worker");
        outsider = fixtures.user("membership-outsider@example.test", "Outsider");
        team = fixtures.team("Studio", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, NOW.minusSeconds(3600));
        fixtures.member(team, worker, TeamRole.MEMBER, NOW.minusSeconds(3600));
        project = fixtures.project(team, "Work", null);
    }

    private AppUserPrincipal principal(User actor) { return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"); }
    private String path() { return "/api/teams/" + team.getId(); }

    @Test void activeMembersCanReadAPagedListWithoutCredentialsOrForeignPeople() throws Exception {
        fixtures.member(fixtures.team("Foreign", "UTC"), outsider, TeamRole.OWNER, NOW);
        mvc.perform(get(path() + "/members").with(user(principal(worker))).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.content[0].role").value("OWNER"))
                .andExpect(jsonPath("$.content[0].joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(path() + "/members").with(user(principal(worker))).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(worker.getId().toString()));
    }

    @Test void removalPreservesHistoryAndBlocksAccessToTheEntireTeam() throws Exception {
        var saved = entries.saveAndFlush(new TimeEntry(worker, team, project, EntryType.MANUAL,
                LocalDate.parse("2026-10-19"), null, null, 900, "Historical work"));
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(owner))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getLeftAt()).isEqualTo(NOW);
        assertThat(entries.existsById(saved.getId())).isTrue();
        mvc.perform(get(path() + "/members").with(user(principal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(path() + "/members").with(user(principal(owner))).param("includeFormer", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].leftAt").value(NOW.toString()));
        mvc.perform(get(path() + "/reports/summary").with(user(principal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.durationSeconds").value(900));
        mvc.perform(get(path() + "/projects").with(user(principal(worker)))).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/members").with(user(principal(worker)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/time-entries").with(user(principal(worker))).param("teamId", team.getId().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/teams").with(user(principal(worker))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(owner))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test void membersMayLeaveAndRejoinThroughAValidInvitation() throws Exception {
        var originalId = members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getId();
        var invite = invitations.create(owner.getId(), team.getId(), new CreateInviteRequest(NOW.plusSeconds(3600), 2));
        mvc.perform(post(path() + "/leave").with(user(principal(worker))).with(csrf())).andExpect(status().isNoContent());
        assertThat(members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getLeftAt()).isEqualTo(NOW);
        invitations.accept(worker.getId(), invite.token());
        var rejoined = members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow();
        assertThat(rejoined.getId()).isEqualTo(originalId);
        assertThat(rejoined.getJoinedAt()).isEqualTo(NOW);
        assertThat(rejoined.getLeftAt()).isNull();
        mvc.perform(get(path() + "/members").with(user(principal(worker)))).andExpect(status().isOk());
    }

    @Test void aRemovedUserCanStopTheirPausedTimerButCannotResumeOrWriteNewTime() throws Exception {
        var timer = new TimeEntry(worker, team, project, EntryType.TIMER, LocalDate.parse("2026-10-19"),
                NOW.minusSeconds(300), null, null, "Running");
        timer.setPausedAt(NOW.minusSeconds(60));
        entries.saveAndFlush(timer);
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(owner))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/timer/resume").with(user(principal(worker))).with(csrf()).contentType("application/json")
                .content("{\"entryId\":\"" + timer.getId() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/time-entries").with(user(principal(worker))).with(csrf()).contentType("application/json")
                .content("{\"teamId\":\"" + team.getId() + "\",\"projectId\":\"" + project.getId() + "\",\"workDate\":\"2026-10-19\",\"durationSeconds\":60}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/timer/stop").with(user(principal(worker))).with(csrf()).contentType("application/json")
                .content("{\"entryId\":\"" + timer.getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationSeconds").value(240));
    }

    @Test void protectsOwnerAndRequiresOwnerPrivilegesForRemovalAndHistoricalLists() throws Exception {
        mvc.perform(post(path() + "/leave").with(user(principal(owner))).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete(path() + "/members/" + owner.getId()).with(user(principal(owner))).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete(path() + "/members/" + owner.getId()).with(user(principal(worker))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(worker))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(path() + "/members").with(user(principal(worker))).param("includeFormer", "true")).andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/members/" + outsider.getId()).with(user(principal(owner))).with(csrf())).andExpect(status().isNotFound());
        assertThat(members.findByTeamIdAndUserId(team.getId(), owner.getId()).orElseThrow().getLeftAt()).isNull();
    }

    @Test void rejectsUnauthenticatedForeignFormerAndDeletedActorsAndInvalidPagination() throws Exception {
        mvc.perform(get(path() + "/members")).andExpect(status().isUnauthorized());
        mvc.perform(get(path() + "/members").with(user(principal(outsider)))).andExpect(status().isNotFound());
        mvc.perform(post(path() + "/leave").with(user(principal(outsider))).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(outsider))).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/members").with(user(new AppUserPrincipal(UUID.randomUUID(), "deleted@example.test", "unused"))))
                .andExpect(status().isUnauthorized());
        for (String[] pagination : new String[][]{{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"}}) {
            mvc.perform(get(path() + "/members").with(user(principal(owner))).param(pagination[0], pagination[1])).andExpect(status().isBadRequest());
        }
        members.findByTeamIdAndUserId(team.getId(), owner.getId()).orElseThrow().setLeftAt(NOW);
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(owner))).with(csrf())).andExpect(status().isNotFound());
    }

    @Test void changesRequireCsrfAndNeverDeleteMembersOnRejectedRequests() throws Exception {
        mvc.perform(delete(path() + "/members/" + worker.getId()).with(user(principal(owner)))).andExpect(status().isForbidden());
        mvc.perform(post(path() + "/leave").with(user(principal(worker)))).andExpect(status().isForbidden());
        assertThat(members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getLeftAt()).isNull();
    }
}
