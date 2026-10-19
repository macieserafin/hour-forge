package pl.macieserafin.hourforge.project.integration;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
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
import pl.macieserafin.hourforge.project.ProjectRepository;
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
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;

    private User owner;
    private User member;
    private User outsider;
    private User former;
    private Team team;
    private Team otherTeam;
    private Project project;
    private Project otherProject;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("project-owner@example.com", "Owner");
        member = fixtures.user("project-member@example.com", "Member");
        outsider = fixtures.user("project-outsider@example.com", "Other owner");
        former = fixtures.user("project-former@example.com", "Former owner");
        team = fixtures.team("Projects team", "UTC");
        otherTeam = fixtures.team("Other team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        fixtures.member(otherTeam, outsider, TeamRole.OWNER, Instant.now());
        var left = new TeamMember(team, former, TeamRole.OWNER, Instant.now().minusSeconds(60));
        left.setLeftAt(Instant.now());
        members.saveAndFlush(left);
        project = fixtures.project(team, "Existing", "Original description");
        otherProject = fixtures.project(otherTeam, "Private project", null);
        base = "/api/teams/" + team.getId() + "/projects";
    }

    @Test
    void ownerCreatesProjectInPathTeamAndReceivesDto() throws Exception {
        var response = mvc.perform(post(base).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"  New project  ","description":"  Description  ",
                                 "teamId":"%s","archivedAt":"2026-01-01T00:00:00Z"}
                                """.formatted(otherTeam.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(team.getId().toString()))
                .andExpect(jsonPath("$.name").value("New project"))
                .andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.archivedAt").isEmpty())
                .andReturn().getResponse();
        UUID id = UUID.fromString(JsonPath.read(response.getContentAsString(), "$.id"));
        assertThat(projects.findById(id).orElseThrow().getTeam().getId()).isEqualTo(team.getId());
    }

    @Test
    void memberListsOnlyTeamActiveProjectsAndCanIncludeArchivedOnes() throws Exception {
        var archived = fixtures.project(team, "Archived", null);
        archived.setArchivedAt(Instant.now());
        projects.flush();
        mvc.perform(get(base).with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(project.getId().toString()));
        mvc.perform(get(base).param("includeArchived", "true").with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(archived.getId().toString()))
                .andExpect(jsonPath("$[0].archivedAt").isNotEmpty());
        mvc.perform(get(base + "/" + archived.getId()).with(user(principal(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Archived"));
    }

    @Test
    void memberCanReadDetailsAndEmptyTeamReturnsEmptyList() throws Exception {
        mvc.perform(get(base + "/" + project.getId()).with(user(principal(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Original description"));
        projects.delete(otherProject);
        projects.flush();
        mvc.perform(get("/api/teams/" + otherTeam.getId() + "/projects").with(user(principal(outsider))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void ownerEditsNameAndCanClearOptionalDescription() throws Exception {
        mvc.perform(put(base + "/" + project.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Updated \",\"description\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.createdAt").value(project.getCreatedAt().toString()));
        assertThat(projects.findById(project.getId()).orElseThrow().getName()).isEqualTo("Updated");
        assertThat(project.getTeam().getId()).isEqualTo(team.getId());
    }

    @Test
    void archiveIsIdempotentAndPreservesCompletedTimeEntries() throws Exception {
        var entry = entries.saveAndFlush(new TimeEntry(member, team, project, EntryType.MANUAL,
                LocalDate.now(), null, null, 3600, "Completed work"));
        mvc.perform(post(base + "/" + project.getId() + "/archive").with(user(principal(owner))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());
        var archivedAt = projects.findById(project.getId()).orElseThrow().getArchivedAt();
        mvc.perform(post(base + "/" + project.getId() + "/archive").with(user(principal(owner))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").value(archivedAt.toString()));
        assertThat(entries.findById(entry.getId())).isPresent();
        assertThat(entries.findById(entry.getId()).orElseThrow().getDurationSeconds()).isEqualTo(3600);
        mvc.perform(put(base + "/" + project.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
                .andExpect(status().isConflict());
        assertThat(project.getName()).isEqualTo("Existing");
    }

    @Test
    void activeTimerBlocksArchiveUntilItIsStopped() throws Exception {
        var timer = entries.saveAndFlush(new TimeEntry(member, team, project, EntryType.TIMER,
                LocalDate.now(), Instant.now().minusSeconds(60), null, null, "Running work"));
        mvc.perform(post(base + "/" + project.getId() + "/archive").with(user(principal(owner))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(project.getArchivedAt()).isNull();
        assertThat(timer.getEndedAt()).isNull();
        timer.setEndedAt(Instant.now());
        timer.setDurationSeconds(60);
        entries.flush();
        mvc.perform(post(base + "/" + project.getId() + "/archive").with(user(principal(owner))).with(csrf()))
                .andExpect(status().isOk());
        assertThat(entries.findById(timer.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "get", "create", "update", "archive"})
    void outsidersFormerOwnersAndUnknownTeamsAreHidden(String operation) throws Exception {
        for (var actor : new User[]{outsider, former}) {
            mvc.perform(request(operation, base, project.getId()).with(user(principal(actor))).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(request(operation, "/api/teams/" + UUID.randomUUID() + "/projects", project.getId())
                        .with(user(principal(owner))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "archive"})
    void membersCannotWrite(String operation) throws Exception {
        long count = projects.count();
        mvc.perform(request(operation, base, project.getId()).with(user(principal(member))).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(projects.count()).isEqualTo(count);
        assertThat(project.getName()).isEqualTo("Existing");
        assertThat(project.getArchivedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "update", "archive"})
    void projectMustBelongToPathTeamAndMustExist(String operation) throws Exception {
        for (UUID id : new UUID[]{otherProject.getId(), UUID.randomUUID()}) {
            mvc.perform(request(operation, base, id).with(user(principal(owner))).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        assertThat(otherProject.getName()).isEqualTo("Private project");
        assertThat(otherProject.getArchivedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "get", "create", "update", "archive"})
    void authenticationIsRequiredAndDeletedUsersCannotAccess(String operation) throws Exception {
        mvc.perform(request(operation, base, project.getId()).with(csrf())).andExpect(status().isUnauthorized());
        var deleted = new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused");
        mvc.perform(request(operation, base, project.getId()).with(user(deleted)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "archive"})
    void csrfIsRequiredForWrites(String operation) throws Exception {
        mvc.perform(request(operation, base, project.getId()).with(user(principal(owner))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"  \"}", "{", "{\"name\":{}}"})
    void invalidCreateAndUpdateDoNotChangeProjects(String body) throws Exception {
        long count = projects.count();
        for (var request : new MockHttpServletRequestBuilder[]{post(base), put(base + "/" + project.getId())}) {
            mvc.perform(request.with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(projects.count()).isEqualTo(count);
        assertThat(project.getName()).isEqualTo("Existing");
    }

    @Test
    void nameLengthIsValidatedAndDescriptionIsOptional() throws Exception {
        for (var request : new MockHttpServletRequestBuilder[]{post(base), put(base + "/" + project.getId())}) {
            mvc.perform(request.with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + "a".repeat(256) + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(base).with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(255) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").isEmpty());
    }

    private AppUserPrincipal principal(User actor) {
        return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused-in-request-test");
    }

    private MockHttpServletRequestBuilder request(String operation, String path, UUID id) {
        return switch (operation) {
            case "list" -> get(path);
            case "get" -> get(path + "/" + id);
            case "create" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}");
            case "update" -> put(path + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}");
            case "archive" -> post(path + "/" + id + "/archive");
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
