package pl.macieserafin.hourforge.team.integration;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamAdminIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TeamMemberRepository members;

    private User owner;
    private User admin;
    private User otherAdmin;
    private User member;
    private User outsider;
    private User former;
    private Team team;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("admin-owner@example.com", "Owner");
        admin = fixtures.user("admin-admin@example.com", "Admin");
        otherAdmin = fixtures.user("admin-other@example.com", "Other admin");
        member = fixtures.user("admin-member@example.com", "Member");
        outsider = fixtures.user("admin-outsider@example.com", "Outsider");
        former = fixtures.user("admin-former@example.com", "Former");
        team = fixtures.team("Admin team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, otherAdmin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        var left = new TeamMember(team, former, TeamRole.MEMBER, Instant.now().minusSeconds(60));
        left.setLeftAt(Instant.now());
        members.saveAndFlush(left);
        base = "/api/teams/" + team.getId();
    }

    @Test
    void ownerPromotesAndDemotesMembersAndTheTeamListShowsTheRole() throws Exception {
        mvc.perform(as(owner, put(base + "/members/" + member.getId() + "/role")).content(role("ADMIN"))).andExpect(status().isNoContent());
        assertThat(roleOf(member)).isEqualTo(TeamRole.ADMIN);
        mvc.perform(get("/api/teams").with(user(principal(member)))).andExpect(jsonPath("$[0].role").value("ADMIN"));
        mvc.perform(get(base + "/members").with(user(principal(owner)))).andExpect(jsonPath("$.content[?(@.role=='ADMIN')]").isNotEmpty());
        mvc.perform(as(owner, put(base + "/members/" + member.getId() + "/role")).content(role("MEMBER"))).andExpect(status().isNoContent());
        assertThat(roleOf(member)).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void rolesChangeOnlyForOtherActiveNonOwnerMembersAndByTheOwner() throws Exception {
        var path = base + "/members/" + member.getId() + "/role";
        mvc.perform(as(owner, put(base + "/members/" + owner.getId() + "/role")).content(role("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(as(owner, put(path)).content(role("OWNER"))).andExpect(status().isBadRequest());
        mvc.perform(as(owner, put(path)).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(as(owner, put(path)).content("{\"role\":\"BOSS\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(owner, put(base + "/members/" + former.getId() + "/role")).content(role("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(as(owner, put(base + "/members/" + outsider.getId() + "/role")).content(role("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(role("ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(user(principal(owner))).contentType(MediaType.APPLICATION_JSON).content(role("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, put(path)).content(role("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, put(base + "/members/" + otherAdmin.getId() + "/role")).content(role("MEMBER"))).andExpect(status().isForbidden());
        mvc.perform(as(member, put(path)).content(role("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(as(outsider, put(path)).content(role("ADMIN"))).andExpect(status().isNotFound());
        assertThat(roleOf(member)).isEqualTo(TeamRole.MEMBER);
        assertThat(roleOf(otherAdmin)).isEqualTo(TeamRole.ADMIN);
    }

    @Test
    void archivedTeamsKeepTheirRoles() throws Exception {
        mvc.perform(as(owner, post(base + "/archive"))).andExpect(status().isOk());
        mvc.perform(as(owner, put(base + "/members/" + member.getId() + "/role")).content(role("ADMIN"))).andExpect(status().isConflict());
        assertThat(roleOf(member)).isEqualTo(TeamRole.MEMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"transfer", "delete", "change-role"})
    void adminsCannotUseOwnerOnlyOperations(String operation) throws Exception {
        var request = switch (operation) {
            case "transfer" -> post(base + "/transfer-ownership").content("{\"userId\":\"" + member.getId() + "\"}");
            case "delete" -> delete(base);
            default -> put(base + "/members/" + member.getId() + "/role").content(role("ADMIN"));
        };
        mvc.perform(as(admin, request)).andExpect(status().isForbidden());
        assertThat(roleOf(admin)).isEqualTo(TeamRole.ADMIN);
        assertThat(roleOf(owner)).isEqualTo(TeamRole.OWNER);
        assertThat(roleOf(member)).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void adminsEditArchiveAndReadTheSummaryOfTheTeam() throws Exception {
        mvc.perform(as(admin, get(base + "/summary"))).andExpect(status().isOk());
        mvc.perform(as(admin, patch(base)).content("{\"name\":\"By admin\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("By admin")).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(as(admin, post(base + "/archive"))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    }

    @Test
    void adminsManageProjectsInvitationsAndReportsButMembersDoNot() throws Exception {
        mvc.perform(as(admin, post(base + "/projects")).content("{\"name\":\"Admin project\"}")).andExpect(status().isCreated());
        mvc.perform(as(member, post(base + "/projects")).content("{\"name\":\"Member project\"}")).andExpect(status().isForbidden());
        mvc.perform(as(admin, post(base + "/invites")).content("{}")).andExpect(status().isCreated());
        mvc.perform(as(admin, get(base + "/invites"))).andExpect(status().isOk());
        mvc.perform(as(member, get(base + "/invites"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get(base + "/reports/members"))).andExpect(status().isOk());
        mvc.perform(as(member, get(base + "/reports/members"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get(base + "/members?includeFormer=true"))).andExpect(status().isOk());
        mvc.perform(as(member, get(base + "/members?includeFormer=true"))).andExpect(status().isForbidden());
    }

    @Test
    void adminsRemoveMembersButOnlyTheOwnerRemovesAdmins() throws Exception {
        mvc.perform(as(admin, delete(base + "/members/" + otherAdmin.getId()))).andExpect(status().isForbidden());
        mvc.perform(as(admin, delete(base + "/members/" + owner.getId()))).andExpect(status().isForbidden());
        assertThat(members.findByTeamIdAndUserId(team.getId(), otherAdmin.getId()).orElseThrow().getLeftAt()).isNull();
        mvc.perform(as(admin, delete(base + "/members/" + member.getId()))).andExpect(status().isNoContent());
        assertThat(members.findByTeamIdAndUserId(team.getId(), member.getId()).orElseThrow().getLeftAt()).isNotNull();
        mvc.perform(as(owner, delete(base + "/members/" + otherAdmin.getId()))).andExpect(status().isNoContent());
        assertThat(members.findByTeamIdAndUserId(team.getId(), otherAdmin.getId()).orElseThrow().getLeftAt()).isNotNull();
    }

    @Test
    void anAdminMayLeaveAndOwnershipCanPassToAnAdmin() throws Exception {
        mvc.perform(as(admin, post(base + "/leave"))).andExpect(status().isNoContent());
        mvc.perform(as(owner, post(base + "/transfer-ownership")).content("{\"userId\":\"" + otherAdmin.getId() + "\"}")).andExpect(status().isNoContent());
        assertThat(roleOf(otherAdmin)).isEqualTo(TeamRole.OWNER);
        assertThat(roleOf(owner)).isEqualTo(TeamRole.MEMBER);
    }

    private static String role(String value) {
        return "{\"role\":\"" + value + "\"}";
    }

    private TeamRole roleOf(User account) {
        return members.findByTeamIdAndUserId(team.getId(), account.getId()).orElseThrow().getRole();
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(principal(actor))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }

    private static AppUserPrincipal principal(User account) {
        return new AppUserPrincipal(account.getId(), account.getEmail(), "unused");
    }
}
