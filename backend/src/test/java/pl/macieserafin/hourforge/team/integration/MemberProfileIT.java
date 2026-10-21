package pl.macieserafin.hourforge.team.integration;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemberProfileIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TeamMemberRepository members;
    @Autowired TeamRepository teams;

    private User owner;
    private User worker;
    private User peer;
    private User outsider;
    private Team team;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("profile-owner@example.com", "Owner");
        worker = fixtures.user("profile-worker@example.com", "Worker");
        peer = fixtures.user("profile-peer@example.com", "Peer");
        outsider = fixtures.user("profile-outsider@example.com", "Outsider");
        team = fixtures.team("Profile team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, peer, TeamRole.MEMBER, Instant.now());
        base = "/api/teams/" + team.getId();
    }

    @Test
    void aPersonDescribesThemselvesAndOnlyManagersAndThePersonSeeIt() throws Exception {
        mvc.perform(as(worker, put(base + "/members/me/profile")).content("{\"jobTitle\":\"  Developer \",\"ftePercent\":50}")).andExpect(status().isNoContent());
        var stored = members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow();
        assertThat(stored.getJobTitle()).isEqualTo("Developer");
        assertThat(stored.getFtePercent()).isEqualTo(50);

        mvc.perform(as(owner, get(base + "/members"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.displayName=='Worker')].jobTitle").value(org.hamcrest.Matchers.contains("Developer")))
                .andExpect(jsonPath("$.content[?(@.displayName=='Worker')].ftePercent").value(org.hamcrest.Matchers.contains(50)));
        mvc.perform(as(worker, get(base + "/members"))).andExpect(jsonPath("$.content[?(@.displayName=='Worker')].jobTitle").value(org.hamcrest.Matchers.contains("Developer")));
        mvc.perform(as(peer, get(base + "/members"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.displayName=='Worker')].jobTitle").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.content[?(@.displayName=='Worker')].ftePercent").value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(as(owner, get(base + "/reports/members"))).andExpect(jsonPath("$.data.content[?(@.displayName=='Worker')].jobTitle").value(org.hamcrest.Matchers.contains("Developer")));
        mvc.perform(as(peer, get(base + "/reports/members"))).andExpect(status().isForbidden());
    }

    @Test
    void theProfileIsValidatedClearedAndLimitedToActiveMembersOfOpenTeams() throws Exception {
        var path = base + "/members/me/profile";
        mvc.perform(as(worker, put(path)).content("{\"ftePercent\":0}")).andExpect(status().isBadRequest());
        mvc.perform(as(worker, put(path)).content("{\"ftePercent\":101}")).andExpect(status().isBadRequest());
        mvc.perform(as(worker, put(path)).content("{\"jobTitle\":\"" + "a".repeat(81) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(outsider, put(path)).content("{\"jobTitle\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(as(worker, put(path)).content("{\"jobTitle\":\"Tester\",\"ftePercent\":100}")).andExpect(status().isNoContent());
        mvc.perform(as(worker, put(path)).content("{\"jobTitle\":\"  \"}")).andExpect(status().isNoContent());
        var stored = members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow();
        assertThat(stored.getJobTitle()).isNull();
        assertThat(stored.getFtePercent()).isNull();

        mvc.perform(as(worker, put(path)).content("{\"jobTitle\":\"Tester\"}")).andExpect(status().isNoContent());
        team.setArchivedAt(Instant.now());
        teams.saveAndFlush(team);
        mvc.perform(as(worker, put(path)).content("{\"jobTitle\":\"Other\"}")).andExpect(status().isConflict());
    }

    @Test
    void leavingOrBeingRemovedClearsTheProfileAndTheAccountExportShowsIt() throws Exception {
        mvc.perform(as(worker, put(base + "/members/me/profile")).content("{\"jobTitle\":\"Developer\",\"ftePercent\":80}")).andExpect(status().isNoContent());
        mvc.perform(as(worker, get("/api/account/export"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].jobTitle").value("Developer")).andExpect(jsonPath("$.teams[0].ftePercent").value(80));
        mvc.perform(as(owner, delete(base + "/members/" + worker.getId()))).andExpect(status().isNoContent());
        var stored = members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow();
        assertThat(stored.getJobTitle()).isNull();
        assertThat(stored.getFtePercent()).isNull();
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
