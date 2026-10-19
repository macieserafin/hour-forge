package pl.macieserafin.hourforge.timeentry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.project.*;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.user.*;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TimeEntryModificationTests {
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

    @BeforeEach
    void setup() {
        actor = users.saveAndFlush(new User("modify@example.com", "Actor"));
        principal = new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
        team = teams.saveAndFlush(new Team("Team", "UTC"));
        members.saveAndFlush(new TeamMember(team, actor, TeamRole.MEMBER, Instant.now()));
        project = projects.saveAndFlush(new Project(team, "Project", null));
        entry = entries.saveAndFlush(new TimeEntry(actor, team, project, EntryType.MANUAL,
                LocalDate.parse("2026-01-01"), null, null, 60, "Original"));
    }

    private String body(Project target, String duration) {
        return """
                {"projectId":"%s","workDate":"2026-02-02","durationSeconds":%s,"description":"  Corrected  "}
                """.formatted(target.getId(), duration);
    }

    @Test
    void editsManualEntryAndMovesOnlyWithinTeam() throws Exception {
        var target = projects.saveAndFlush(new Project(team, "Target", null));
        mvc.perform(patch("/api/time-entries/" + entry.getId()).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(target, "120")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Corrected"))
                .andExpect(jsonPath("$.projectId").value(target.getId().toString()))
                .andExpect(jsonPath("$.workDate").value("2026-02-02"))
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
        var target = projects.saveAndFlush(new Project(team, "Archived", null));
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
        var foreignTeam = teams.saveAndFlush(new Team("Foreign", "UTC"));
        var foreign = projects.saveAndFlush(new Project(foreignTeam, "Foreign", null));
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
        var owner = users.saveAndFlush(new User("modify-owner@example.com", "Owner"));
        members.saveAndFlush(new TeamMember(team, owner, TeamRole.OWNER, Instant.now()));
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
