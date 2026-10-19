package pl.macieserafin.hourforge.team.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.invitation.TeamInvite;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamManagementIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamInviteRepository invites;
    @Autowired JdbcTemplate jdbc;

    private User owner;
    private User member;
    private User outsider;
    private User former;
    private Team team;
    private Project project;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("manage-owner@example.com", "Owner");
        member = fixtures.user("manage-member@example.com", "Member");
        outsider = fixtures.user("manage-outsider@example.com", "Outsider");
        former = fixtures.user("manage-former@example.com", "Former");
        team = fixtures.team("Managed team", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        var left = new TeamMember(team, former, TeamRole.MEMBER, Instant.now().minusSeconds(60));
        left.setLeftAt(Instant.now());
        members.saveAndFlush(left);
        project = fixtures.project(team, "Managed project", null);
        base = "/api/teams/" + team.getId();
    }

    @Test
    void ownerEditsNameAndTimezoneWithoutChangingStoredWorkDates() throws Exception {
        var entry = manual(member, LocalDate.now().minusDays(3));
        mvc.perform(as(owner, patch(base)).content("{\"name\":\"  Renamed  \",\"timezone\":\"Asia/Tokyo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo")).andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.archivedAt").isEmpty());
        assertThat(entries.findById(entry.getId()).orElseThrow().getWorkDate()).isEqualTo(entry.getWorkDate());
        for (String body : new String[]{"{\"name\":\" \",\"timezone\":\"UTC\"}", "{\"name\":\"Team\",\"timezone\":\"Mars/Base\"}", "{\"name\":\"Team\"}"}) {
            mvc.perform(as(owner, patch(base)).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(teams.findById(team.getId()).orElseThrow().getName()).isEqualTo("Renamed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "transfer", "archive", "summary", "delete"})
    void onlyTheActiveOwnerCanManageTheTeam(String operation) throws Exception {
        mvc.perform(operation(operation).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(operation(operation).with(user(principal(owner)))).andExpect(operation.equals("summary") ? status().isOk() : status().isForbidden());
        mvc.perform(operation(operation).with(user(principal(member))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(operation(operation).with(user(principal(outsider))).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(operation(operation).with(user(principal(former))).with(csrf())).andExpect(status().isNotFound());
        assertThat(teams.findById(team.getId())).get().extracting(Team::getName, Team::getArchivedAt).containsExactly("Managed team", null);
        assertThat(role(owner)).isEqualTo(TeamRole.OWNER);
    }

    @Test
    void ownershipMovesToAnActiveMemberAndThePreviousOwnerMayLeave() throws Exception {
        mvc.perform(as(owner, post(base + "/transfer-ownership")).content(body(owner.getId()))).andExpect(status().isBadRequest());
        mvc.perform(as(owner, post(base + "/transfer-ownership")).content(body(former.getId()))).andExpect(status().isNotFound());
        mvc.perform(as(owner, post(base + "/transfer-ownership")).content(body(outsider.getId()))).andExpect(status().isNotFound());
        mvc.perform(as(owner, post(base + "/transfer-ownership")).content("{}")).andExpect(status().isBadRequest());
        assertThat(role(owner)).isEqualTo(TeamRole.OWNER);

        mvc.perform(as(owner, post(base + "/transfer-ownership")).content(body(member.getId()))).andExpect(status().isNoContent());
        assertThat(role(owner)).isEqualTo(TeamRole.MEMBER);
        assertThat(role(member)).isEqualTo(TeamRole.OWNER);
        assertThat(activeOwners()).isEqualTo(1);
        mvc.perform(get("/api/teams").with(user(principal(member)))).andExpect(jsonPath("$[0].role").value("OWNER"));
        mvc.perform(as(owner, patch(base)).content("{\"name\":\"Taken back\",\"timezone\":\"UTC\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/leave").with(user(principal(owner))).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void archivingRequiresStoppedTimersIsFinalAndBlocksWritesButNotReading() throws Exception {
        var timer = entries.saveAndFlush(new TimeEntry(member, team, project, EntryType.TIMER, LocalDate.now(),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS), null, null, null));
        mvc.perform(as(owner, post(base + "/archive"))).andExpect(status().isConflict());
        assertThat(teams.findById(team.getId()).orElseThrow().getArchivedAt()).isNull();
        timer.setEndedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        timer.setDurationSeconds(60);
        var entry = manual(member, LocalDate.now());
        entries.flush();

        var archivedAt = com.jayway.jsonpath.JsonPath.<String>read(mvc.perform(as(owner, post(base + "/archive")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.archivedAt");
        assertThat(archivedAt).isNotNull();
        mvc.perform(as(owner, post(base + "/archive"))).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").value(archivedAt));

        mvc.perform(as(owner, patch(base)).content("{\"name\":\"Changed\",\"timezone\":\"UTC\"}")).andExpect(status().isConflict());
        mvc.perform(as(owner, post(base + "/projects")).content("{\"name\":\"New\"}")).andExpect(status().isConflict());
        mvc.perform(as(owner, put(base + "/projects/" + project.getId())).content("{\"name\":\"Changed\"}")).andExpect(status().isConflict());
        mvc.perform(as(owner, post(base + "/invites")).content("{\"expiresAt\":\"" + Instant.now().plusSeconds(3600) + "\"}")).andExpect(status().isConflict());
        mvc.perform(as(member, post("/api/timer/start")).content("{\"teamId\":\"%s\",\"projectId\":\"%s\"}".formatted(team.getId(), project.getId())))
                .andExpect(status().isConflict());
        mvc.perform(as(member, post("/api/time-entries")).content("{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":60}"
                .formatted(team.getId(), project.getId(), LocalDate.now()))).andExpect(status().isConflict());
        mvc.perform(as(member, patch("/api/time-entries/" + entry.getId())).content("{\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":120}"
                .formatted(project.getId(), entry.getWorkDate()))).andExpect(status().isConflict());
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(principal(member))).with(csrf())).andExpect(status().isConflict());
        assertThat(entries.existsById(entry.getId())).isTrue();

        mvc.perform(get("/api/teams").with(user(principal(member)))).andExpect(jsonPath("$[0].archivedAt").value(archivedAt));
        mvc.perform(get(base + "/reports/summary").with(user(principal(owner)))).andExpect(status().isOk());
        mvc.perform(get(base + "/members").with(user(principal(member)))).andExpect(status().isOk());
        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString()).with(user(principal(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(post(base + "/leave").with(user(principal(member))).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void summaryCountsWhatDeletionWouldRemove() throws Exception {
        manual(member, LocalDate.now());
        manual(owner, LocalDate.now());
        mvc.perform(get(base + "/summary").with(user(principal(owner)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMembers").value(2)).andExpect(jsonPath("$.projects").value(1))
                .andExpect(jsonPath("$.timeEntries").value(2)).andExpect(jsonPath("$.activeTimers").value(false));
    }

    @Test
    void deletionRemovesTheTeamAndAllItsDataButRequiresStoppedTimers() throws Exception {
        var other = fixtures.team("Untouched", "UTC");
        fixtures.member(other, owner, TeamRole.OWNER, Instant.now());
        var otherProject = fixtures.project(other, "Untouched project", null);
        var kept = entries.saveAndFlush(new TimeEntry(owner, other, otherProject, EntryType.MANUAL, LocalDate.now(), null, null, 60, null));
        manual(member, LocalDate.now());
        invites.saveAndFlush(new TeamInvite(team, owner, "hash-" + UUID.randomUUID(), null, Instant.now().plusSeconds(3600)));
        var timer = entries.saveAndFlush(new TimeEntry(member, team, project, EntryType.TIMER, LocalDate.now(),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS), null, null, null));
        mvc.perform(delete(base).with(user(principal(owner))).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get(base + "/summary").with(user(principal(owner)))).andExpect(jsonPath("$.activeTimers").value(true));

        timer.setEndedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        timer.setDurationSeconds(60);
        entries.flush();
        mvc.perform(delete(base).with(user(principal(owner))).with(csrf())).andExpect(status().isNoContent());

        for (String table : new String[]{"time_entries", "projects", "team_invites", "team_members"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where team_id = ?", Integer.class, team.getId())).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from teams where id = ?", Integer.class, team.getId())).isZero();
        assertThat(entries.existsById(kept.getId())).isTrue();
        mvc.perform(get("/api/teams").with(user(principal(member)))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(base).with(user(principal(owner))).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void anArchivedTeamCanStillBeDeleted() throws Exception {
        mvc.perform(as(owner, post(base + "/archive"))).andExpect(status().isOk());
        mvc.perform(delete(base).with(user(principal(owner))).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from teams where id = ?", Integer.class, team.getId())).isZero();
    }

    private TimeEntry manual(User author, LocalDate workDate) {
        return entries.saveAndFlush(new TimeEntry(author, team, project, EntryType.MANUAL, workDate, null, null, 60, "Work"));
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        return switch (operation) {
            case "update" -> patch(base).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\",\"timezone\":\"UTC\"}");
            case "transfer" -> post(base + "/transfer-ownership").contentType(MediaType.APPLICATION_JSON).content(body(member.getId()));
            case "archive" -> post(base + "/archive");
            case "summary" -> get(base + "/summary");
            case "delete" -> delete(base);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(principal(actor))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }

    private static String body(UUID userId) {
        return "{\"userId\":\"" + userId + "\"}";
    }

    private TeamRole role(User account) {
        return members.findByTeamIdAndUserId(team.getId(), account.getId()).orElseThrow().getRole();
    }

    private int activeOwners() {
        return jdbc.queryForObject("select count(*) from team_members where team_id = ? and role = 'OWNER' and left_at is null",
                Integer.class, team.getId());
    }

    private static AppUserPrincipal principal(User account) {
        return new AppUserPrincipal(account.getId(), account.getEmail(), "unused");
    }
}
