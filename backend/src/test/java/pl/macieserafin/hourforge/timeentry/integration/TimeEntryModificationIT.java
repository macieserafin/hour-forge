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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimeEntryModificationIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    private User actor;
    private Team team;
    private Project project;
    private TimeEntry entry;
    private AppUserPrincipal principal;
    private LocalDate today;
    private LocalDate correctedDate;

    @BeforeEach
    void setup() {
        today = LocalDate.now(ZoneOffset.UTC);
        correctedDate = today.minusDays(10);
        actor = fixtures.user("modify@example.com", "Actor");
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
        team = fixtures.team("Team", "UTC");
        fixtures.member(team, actor, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Project", null);
        entry = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.MANUAL,
                today.minusDays(30), null, null, 60, "Original"));
    }

    private String body(Project target, String duration) {
        return """
                {"projectId":"%s","workDate":"%s","durationSeconds":%s,"description":"  Corrected  "}
                """.formatted(target.getId(), correctedDate, duration);
    }

    @Test
    void editsManualEntryAndMovesOnlyWithinTeam() throws Exception {
        var target = fixtures.project(team, "Target", null);
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(target, "120")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Corrected"))
                .andExpect(jsonPath("$.projectId").value(target.getId().toString()))
                .andExpect(jsonPath("$.workDate").value(correctedDate.toString()))
                .andExpect(jsonPath("$.durationSeconds").value(120));
        assertThat(entry.getEntryType()).isEqualTo(EntryType.MANUAL);
        assertThat(entry.getStartedAt()).isNull();
    }

    @Test
    void timerCorrectionPreservesOriginalTimestampsAndType() throws Exception {
        var start = Instant.parse("2026-01-01T08:00:00Z");
        entry.setEntryType(EntryType.TIMER);
        entry.setStartedAt(start);
        entry.setEndedAt(start.plusSeconds(60));
        entries.flush();
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, "1800")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entryType").value("TIMER"))
                .andExpect(jsonPath("$.startedAt").value(start.toString()))
                .andExpect(jsonPath("$.endedAt").value(start.plusSeconds(60).toString()));
    }

    @Test
    void allowsCorrectionOnCurrentArchivedProjectButNotMovingIntoAnotherArchivedProject() throws Exception {
        project.setArchivedAt(Instant.now());
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, "120")))
                .andExpect(status().isOk());
        var target = fixtures.project(team, "Archived", null);
        target.setArchivedAt(Instant.now());
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(target, "120")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(entries.existsById(entry.getId())).isFalse();
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsForeignProject() throws Exception {
        var foreignTeam = fixtures.team("Foreign", "UTC");
        var foreign = fixtures.project(foreignTeam, "Foreign", null);
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(foreign, "120")))
                .andExpect(status().isNotFound());
        assertThat(entry.getProject().getId()).isEqualTo(project.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsActiveAndPausedTimers(boolean paused) throws Exception {
        entry.setEntryType(EntryType.TIMER);
        entry.setStartedAt(Instant.now().minusSeconds(60));
        entry.setDurationSeconds(null);
        if (paused) entry.setPausedAt(Instant.now());
        entries.flush();
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, "120")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsOwnerEditingOthersEntriesAndDepartedMember() throws Exception {
        var owner = fixtures.user("modify-owner@example.com", "Owner");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        var ownerPrincipal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, "120")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(ownerPrincipal)).with(csrf()))
                .andExpect(status().isNotFound());
        members.findByTeamIdAndUserId(team.getId(), actor.getId()).orElseThrow().setLeftAt(Instant.now());
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, "120")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "2147483648", "1.5"})
    void rejectsInvalidDuration(String duration) throws Exception {
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, duration)))
                .andExpect(status().isBadRequest());
        assertThat(entry.getDurationSeconds()).isEqualTo(60);
    }

    @Test
    void descriptionLengthIsValidatedAfterTrimming() throws Exception {
        String request = "{\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":120,\"description\":\"%s\"}";
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request.formatted(project.getId(), correctedDate, "d".repeat(2001))))
                .andExpect(status().isBadRequest());
        assertThat(entry.getDescription()).isEqualTo("Original");
        assertThat(entry.getDurationSeconds()).isEqualTo(60);
        String maximal = "d".repeat(2000);
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request.formatted(project.getId(), correctedDate, "  " + maximal + "  ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(maximal));
    }

    @Test
    void changedValuesMustFitLimitsWhileUnchangedOldValuesMayBeKept() throws Exception {
        var oldDate = today.minusYears(3);
        var old = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.TIMER, oldDate,
                Instant.now().minusSeconds(200_000), Instant.now().minusSeconds(92_000), 108_000, "Forgotten"));
        mvc.perform(correction(old, oldDate, 108_000, "Only text")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Only text"));
        mvc.perform(correction(old, oldDate, 90_000, null)).andExpect(status().isBadRequest());
        mvc.perform(correction(old, oldDate.plusDays(1), 108_000, null)).andExpect(status().isBadRequest());
        assertThat(old.getDurationSeconds()).isEqualTo(108_000);
        assertThat(old.getWorkDate()).isEqualTo(oldDate);

        mvc.perform(correction(entry, today.plusDays(1), 60, null)).andExpect(status().isBadRequest());
        mvc.perform(correction(entry, today, 86_401, null)).andExpect(status().isBadRequest());
        mvc.perform(correction(entry, today, 86_400, null)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder correction(TimeEntry target, LocalDate workDate, int seconds, String description) {
        return patch("/api/time-entries/" + target.getId()).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":%d,\"description\":%s}"
                        .formatted(project.getId(), workDate, seconds, description == null ? "null" : "\"" + description + "\""));
    }

    @Test
    void requiresAuthenticationCsrfAndCompleteInput() throws Exception {
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/time-entries/" + entry.getId()).with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/time-entries/" + UUID.randomUUID()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(project, "120")))
                .andExpect(status().isNotFound());
    }
}
