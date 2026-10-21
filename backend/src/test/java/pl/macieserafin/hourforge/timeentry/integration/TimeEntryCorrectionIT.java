package pl.macieserafin.hourforge.timeentry.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimeEntryCorrectionIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TimeEntryRepository entries;

    private final LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    private User owner;
    private User admin;
    private User worker;
    private User peer;
    private User outsider;
    private Team team;
    private Project project;
    private Project other;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("fix-owner@example.com", "Owner");
        admin = fixtures.user("fix-admin@example.com", "Admin");
        worker = fixtures.user("fix-worker@example.com", "Worker");
        peer = fixtures.user("fix-peer@example.com", "Peer");
        outsider = fixtures.user("fix-outsider@example.com", "Outsider");
        team = fixtures.team("Fix team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, peer, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Main", null);
        other = fixtures.project(team, "Other", null);
        base = "/api/teams/" + team.getId();
    }

    private static String body(Project project, LocalDate date, int seconds, String description) {
        return "{\"projectId\":\"" + project.getId() + "\"," + (date == null ? "" : "\"workDate\":\"" + date + "\",")
                + "\"durationSeconds\":" + seconds + ",\"description\":\"" + description + "\"}";
    }

    private TimeEntry saved(User person, int seconds) {
        return entries.saveAndFlush(new TimeEntry(person, team, project, EntryType.MANUAL, day, null, null, seconds, "Original"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "owner"})
    void managersAddAnEntryToAMembersTimeAndTheMemberSeesWhoDidIt(String who) throws Exception {
        var actor = who.equals("admin") ? admin : owner;
        mvc.perform(as(actor, post(base + "/members/" + worker.getId() + "/time-entries")).content(body(project, day, 1800, "Added for you")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(worker.getId().toString()))
                .andExpect(jsonPath("$.entryType").value("MANUAL")).andExpect(jsonPath("$.durationSeconds").value(1800));
        var entry = entries.findAll().stream().filter(e -> e.getUser().getId().equals(worker.getId())).findFirst().orElseThrow();
        assertThat(entry.getLastEditedBy().getId()).isEqualTo(actor.getId());
        assertThat(entry.getLastEditedAt()).isNotNull();
        mvc.perform(as(worker, get("/api/time-entries").param("teamId", team.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].editedByName").value(actor.getDisplayName()));
    }

    @Test
    void managersChangeAndRemoveEntriesAndTheLastPersonToChangeThemIsRecorded() throws Exception {
        var entry = saved(worker, 600);
        mvc.perform(as(admin, patch(base + "/time-entries/" + entry.getId())).content(body(other, day, 900, "Corrected")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationSeconds").value(900))
                .andExpect(jsonPath("$.projectId").value(other.getId().toString())).andExpect(jsonPath("$.description").value("Corrected"));
        mvc.perform(as(worker, get("/api/time-entries").param("teamId", team.getId().toString())))
                .andExpect(jsonPath("$.content[0].editedByName").value("Admin")).andExpect(jsonPath("$.content[0].editedAt").isNotEmpty());
        mvc.perform(as(owner, get(base + "/reports/entries")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].editedByName").value("Admin"));

        // The owner of the entry changes it again: the correction mark gives way to a plain edit.
        mvc.perform(as(worker, patch("/api/time-entries/" + entry.getId())).content(body(other, day, 1200, "Mine again")))
                .andExpect(status().isOk());
        mvc.perform(as(worker, get("/api/time-entries").param("teamId", team.getId().toString())))
                .andExpect(jsonPath("$.content[0].editedByName").doesNotExist()).andExpect(jsonPath("$.content[0].editedAt").isNotEmpty());

        mvc.perform(as(owner, delete(base + "/time-entries/" + entry.getId()))).andExpect(status().isNoContent());
        assertThat(entries.findById(entry.getId())).isEmpty();
    }

    @Test
    void savingWithoutAnyChangeIsNotACorrection() throws Exception {
        var entry = saved(worker, 600);
        mvc.perform(as(admin, patch(base + "/time-entries/" + entry.getId())).content(body(project, day, 600, "Original")))
                .andExpect(status().isOk());
        assertThat(entries.findById(entry.getId()).orElseThrow().getLastEditedBy()).isNull();
    }

    @Test
    void managersListAMembersEntriesWithTheSameFiltersAsTheMember() throws Exception {
        saved(worker, 600);
        saved(peer, 300);
        mvc.perform(as(admin, get(base + "/members/" + worker.getId() + "/time-entries")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].durationSeconds").value(600));
        mvc.perform(as(admin, get(base + "/members/" + worker.getId() + "/time-entries").param("q", "nothing")))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(as(admin, get(base + "/members/" + worker.getId() + "/time-entries").param("size", "0"))).andExpect(status().isBadRequest());
        mvc.perform(as(admin, get(base + "/members/" + outsider.getId() + "/time-entries"))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "create", "update", "delete"})
    void membersAndOutsidersCannotCorrectAnything(String operation) throws Exception {
        var entry = saved(worker, 600);
        for (var denied : new User[]{peer, worker}) {
            mvc.perform(as(denied, request(operation, entry))).andExpect(status().isForbidden());
        }
        mvc.perform(as(outsider, request(operation, entry))).andExpect(status().isNotFound());
        mvc.perform(request(operation, entry).with(csrf())).andExpect(status().isUnauthorized());
        assertThat(entries.findById(entry.getId()).orElseThrow().getDurationSeconds()).isEqualTo(600);
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test
    void entriesOfAnotherTeamCannotBeReachedThroughThisOne() throws Exception {
        var foreign = fixtures.team("Foreign", "UTC");
        fixtures.member(foreign, outsider, TeamRole.OWNER, Instant.now());
        var foreignEntry = entries.saveAndFlush(new TimeEntry(outsider, foreign, fixtures.project(foreign, "F", null), EntryType.MANUAL, day, null, null, 60, null));
        mvc.perform(as(admin, patch(base + "/time-entries/" + foreignEntry.getId())).content(body(project, day, 90, "x"))).andExpect(status().isNotFound());
        mvc.perform(as(admin, delete(base + "/time-entries/" + foreignEntry.getId()))).andExpect(status().isNotFound());
        mvc.perform(as(admin, post(base + "/members/" + outsider.getId() + "/time-entries")).content(body(project, day, 90, "x"))).andExpect(status().isNotFound());
        assertThat(entries.findById(foreignEntry.getId()).orElseThrow().getDurationSeconds()).isEqualTo(60);
    }

    @Test
    void theMembersOwnLimitsAndProjectRulesStillApply() throws Exception {
        var entry = saved(worker, 600);
        var archived = fixtures.project(team, "Old", null);
        archived.setArchivedAt(Instant.now());
        var path = base + "/time-entries/" + entry.getId();
        mvc.perform(as(admin, patch(path)).content(body(project, day, 24 * 3600 + 1, "x"))).andExpect(status().isBadRequest());
        mvc.perform(as(admin, patch(path)).content(body(project, LocalDate.now(ZoneOffset.UTC).plusDays(2), 60, "x"))).andExpect(status().isBadRequest());
        mvc.perform(as(admin, patch(path)).content(body(archived, day, 60, "x"))).andExpect(status().isConflict());
        mvc.perform(as(admin, patch(path)).content("{\"projectId\":\"" + project.getId() + "\",\"durationSeconds\":60,\"jiraIssueId\":\"10001\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(admin, patch(path)).content(body(project, day, 0, "x"))).andExpect(status().isBadRequest());
        mvc.perform(as(admin, patch(path)).content("{\"projectId\":\"" + UUID.randomUUID() + "\",\"durationSeconds\":60}")).andExpect(status().isNotFound());
        assertThat(entries.findById(entry.getId()).orElseThrow().getDurationSeconds()).isEqualTo(600);
    }

    @Test
    void aFormerMembersEntriesCanBeCorrectedButNothingIsAddedToThem() throws Exception {
        var entry = saved(worker, 600);
        var left = members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow();
        left.setLeftAt(Instant.now());
        members.saveAndFlush(left);
        mvc.perform(as(admin, patch(base + "/time-entries/" + entry.getId())).content(body(project, day, 700, "Fixed")))
                .andExpect(status().isOk());
        mvc.perform(as(admin, post(base + "/members/" + worker.getId() + "/time-entries")).content(body(project, day, 60, "x")))
                .andExpect(status().isNotFound());
        mvc.perform(as(admin, get(base + "/members/" + worker.getId() + "/time-entries"))).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void runningTimersAndArchivedTeamsCannotBeCorrected() throws Exception {
        var timer = entries.saveAndFlush(new TimeEntry(worker, team, project, EntryType.TIMER, day,
                Instant.now().minusSeconds(60), null, null, null));
        mvc.perform(as(admin, patch(base + "/time-entries/" + timer.getId())).content(body(project, day, 60, "x"))).andExpect(status().isConflict());
        mvc.perform(as(admin, delete(base + "/time-entries/" + timer.getId()))).andExpect(status().isConflict());
        entries.delete(timer);
        var entry = saved(worker, 600);
        team.setArchivedAt(Instant.now());
        teams.saveAndFlush(team);
        mvc.perform(as(admin, patch(base + "/time-entries/" + entry.getId())).content(body(project, day, 60, "x"))).andExpect(status().isConflict());
        mvc.perform(as(admin, delete(base + "/time-entries/" + entry.getId()))).andExpect(status().isConflict());
        mvc.perform(as(admin, post(base + "/members/" + worker.getId() + "/time-entries")).content(body(project, day, 60, "x"))).andExpect(status().isConflict());
        assertThat(entries.findById(entry.getId()).orElseThrow().getDurationSeconds()).isEqualTo(600);
    }

    private MockHttpServletRequestBuilder request(String operation, TimeEntry entry) {
        return switch (operation) {
            case "list" -> get(base + "/members/" + worker.getId() + "/time-entries");
            case "create" -> post(base + "/members/" + worker.getId() + "/time-entries").content(body(project, day, 60, "x"));
            case "update" -> patch(base + "/time-entries/" + entry.getId()).content(body(project, day, 60, "x"));
            default -> delete(base + "/time-entries/" + entry.getId());
        };
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
