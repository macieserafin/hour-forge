package pl.macieserafin.hourforge.invitation.integration;

import java.time.Clock;
import java.time.Instant;
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
import pl.macieserafin.hourforge.user.User;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvitationManagementIT extends ApiIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired InvitationService service;
    @Autowired TeamMembershipService memberships;
    @Autowired TeamInviteRepository invites;
    @Autowired TeamMemberRepository members;
    @MockitoBean Clock clock;
    private Team team;
    private User owner;
    private User worker;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        owner = fixtures.user("management-owner@example.test", "Owner");
        worker = fixtures.user("management-worker@example.test", "Worker");
        team = fixtures.team("Managed invitations", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, NOW.minusSeconds(60));
        fixtures.member(team, worker, TeamRole.MEMBER, NOW.minusSeconds(60));
    }
    private AppUserPrincipal principal(User actor) { return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"); }
    private String path() { return "/api/teams/" + team.getId() + "/invites"; }
    private InviteResponse create(Integer limit) { return service.create(owner.getId(), team.getId(), new CreateInviteRequest(null, limit)); }

    @Test void defaultsAndThirtyDayBoundaryAreEnforcedByTheServer() throws Exception {
        for (String body : new String[]{"{}", "{\"expiresAt\":null,\"maxUses\":null}"}) {
            mvc.perform(post(path()).with(user(principal(owner))).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.maxUses").value(1))
                    .andExpect(jsonPath("$.expiresAt").value(NOW.plusSeconds(7 * 86400).toString()));
        }
        for (long seconds : new long[]{0, -1, 30 * 86400L + 1}) {
            mvc.perform(post(path()).with(user(principal(owner))).with(csrf()).contentType("application/json")
                    .content("{\"expiresAt\":\"" + NOW.plusSeconds(seconds) + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(path()).with(user(principal(owner))).with(csrf()).contentType("application/json")
                .content("{\"expiresAt\":\"" + NOW.plusSeconds(30 * 86400L) + "\",\"maxUses\":20}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.maxUses").value(20));
    }

    @Test void paginatesStatusesWithoutLeakingTokenOrHash() throws Exception {
        var active = create(2);
        var expired = create(1);
        invites.findById(expired.id()).orElseThrow().setExpiresAt(NOW);
        var exhausted = create(1);
        invites.findById(exhausted.id()).orElseThrow().setUsedCount(1);
        var revoked = create(1);
        service.revoke(owner.getId(), team.getId(), revoked.id());
        var otherTeam = fixtures.team("Foreign", "UTC");
        fixtures.member(otherTeam, owner, TeamRole.OWNER, NOW);
        service.create(owner.getId(), otherTeam.getId(), new CreateInviteRequest(null, 1));
        var body = mvc.perform(get(path()).with(user(principal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].status", org.hamcrest.Matchers.containsInAnyOrder("ACTIVE", "EXPIRED", "EXHAUSTED", "REVOKED")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(active.token(), "tokenHash", "invitationUrl", "createdBy");
        var ids = new java.util.ArrayList<String>();
        for (int page = 0; page < 4; page++) {
            var result = mvc.perform(get(path()).with(user(principal(owner))).param("page", String.valueOf(page)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(4)).andReturn().getResponse().getContentAsString();
            ids.add(com.jayway.jsonpath.JsonPath.read(result, "$.content[0].id"));
        }
        assertThat(ids).hasSize(4).doesNotHaveDuplicates();
    }

    @Test void revocationIsIdempotentAndCannotBeBypassedThroughAccept() throws Exception {
        var invite = create(1);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(path() + "/" + invite.id()).with(user(principal(owner))).with(csrf())).andExpect(status().isNoContent());
        }
        assertThat(invites.findById(invite.id()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
        mvc.perform(post("/api/invites/" + invite.token() + "/accept").with(user(principal(worker))).with(csrf()))
                .andExpect(status().isGone());
        mvc.perform(get("/api/invites/" + invite.token()).with(user(principal(worker))))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test void removedMembersNeedAStrictlyNewerInviteWhileVoluntaryLeaversCanReuseOldLinks() throws Exception {
        var old = create(10);
        memberships.remove(owner.getId(), team.getId(), worker.getId());
        assertThat(members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getRemovedAt()).isEqualTo(NOW);
        mvc.perform(get("/api/invites/" + old.token()).with(user(principal(worker))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW_INVITATION_REQUIRED"));
        mvc.perform(post("/api/invites/" + old.token() + "/accept").with(user(principal(worker))).with(csrf())).andExpect(status().isGone());
        assertThat(invites.findById(old.id()).orElseThrow().getUsedCount()).isZero();
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        var fresh = create(10);
        service.accept(worker.getId(), fresh.token());
        assertThat(members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getRemovedAt()).isNull();
        memberships.leave(worker.getId(), team.getId());
        service.accept(worker.getId(), old.token());
        assertThat(members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().getLeftAt()).isNull();
    }

    @Test void listAndRevokeRequireCurrentOwnershipCsrfAndTeamScopedIdentifiers() throws Exception {
        var invite = create(1);
        var foreignTeam = fixtures.team("Foreign owned team", "UTC");
        fixtures.member(foreignTeam, owner, TeamRole.OWNER, NOW);
        var foreign = service.create(owner.getId(), foreignTeam.getId(), new CreateInviteRequest(null, 1));
        mvc.perform(delete(path() + "/" + foreign.id()).with(user(principal(owner))).with(csrf())).andExpect(status().isNotFound());
        assertThat(invites.findById(foreign.id()).orElseThrow().getRevokedAt()).isNull();
        var deleted = new AppUserPrincipal(UUID.randomUUID(), "deleted@example.test", "unused");
        mvc.perform(get(path()).with(user(deleted))).andExpect(status().isUnauthorized());
        mvc.perform(delete(path() + "/" + invite.id()).with(user(deleted)).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).with(user(principal(worker)))).andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/" + invite.id()).with(user(principal(worker))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/" + invite.id()).with(user(principal(owner)))).andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/" + UUID.randomUUID()).with(user(principal(owner))).with(csrf())).andExpect(status().isNotFound());
        for (String[] params : new String[][]{{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"}}) {
            mvc.perform(get(path()).with(user(principal(owner))).param(params[0], params[1])).andExpect(status().isBadRequest());
        }
        team.setArchivedAt(NOW);
        mvc.perform(get(path()).with(user(principal(owner)))).andExpect(status().isOk());
        mvc.perform(delete(path() + "/" + invite.id()).with(user(principal(owner))).with(csrf())).andExpect(status().isConflict());
        members.findByTeamIdAndUserId(team.getId(), owner.getId()).orElseThrow().setLeftAt(NOW);
        mvc.perform(get(path()).with(user(principal(owner)))).andExpect(status().isNotFound());
    }

    @Test void legacyUnlimitedInvitationsAtTheCounterBoundaryAreExhaustedWithoutOverflow() throws Exception {
        var invite = create(1);
        var stored = invites.findById(invite.id()).orElseThrow();
        stored.setMaxUses(null);
        stored.setUsedCount(Integer.MAX_VALUE);
        var guest = fixtures.user("legacy-invite-guest@example.test", "Guest");
        mvc.perform(get("/api/invites/" + invite.token()).with(user(principal(guest))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXHAUSTED"));
        mvc.perform(post("/api/invites/" + invite.token() + "/accept").with(user(principal(guest))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(stored.getUsedCount()).isEqualTo(Integer.MAX_VALUE);
    }
}
