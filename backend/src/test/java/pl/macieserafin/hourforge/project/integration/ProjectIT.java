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
    @ValueSource(strings = {"list", "get", "create", "update", "archive", "restore", "delete"})
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
    @ValueSource(strings = {"create", "update", "archive", "restore", "delete"})
    void membersCannotWrite(String operation) throws Exception {
        long count = projects.count();
        mvc.perform(request(operation, base, project.getId()).with(user(principal(member))).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(projects.count()).isEqualTo(count);
        assertThat(project.getName()).isEqualTo("Existing");
        assertThat(project.getArchivedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "update", "archive", "restore", "delete"})
    void projectMustBelongToPathTeamAndMustExist(String operation) throws Exception {
        for (UUID id : new UUID[]{otherProject.getId(), UUID.randomUUID()}) {
            mvc.perform(request(operation, base, id).with(user(principal(owner))).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        assertThat(otherProject.getName()).isEqualTo("Private project");
        assertThat(otherProject.getArchivedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "get", "create", "update", "archive", "restore", "delete"})
    void authenticationIsRequiredAndDeletedUsersCannotAccess(String operation) throws Exception {
        mvc.perform(request(operation, base, project.getId()).with(csrf())).andExpect(status().isUnauthorized());
        var deleted = new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused");
        mvc.perform(request(operation, base, project.getId()).with(user(deleted)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "archive", "restore", "delete"})
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

    @Test
    void descriptionLengthIsValidatedAfterTrimming() throws Exception {
        long count = projects.count();
        for (var request : new MockHttpServletRequestBuilder[]{post(base), put(base + "/" + project.getId())}) {
            mvc.perform(request.with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Long\",\"description\":\"" + "d".repeat(2001) + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(projects.count()).isEqualTo(count);
        assertThat(projects.findById(project.getId()).orElseThrow().getDescription()).isEqualTo("Original description");

        String maximal = "d".repeat(2000);
        mvc.perform(post(base).with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long\",\"description\":\"  " + maximal + "  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value(maximal));
        mvc.perform(put(base + "/" + project.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Existing\",\"description\":\"" + maximal + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(maximal));
    }

    @Test
    void repositoryLinkCanBeCreatedUpdatedAndCleared() throws Exception {
        String response = mvc.perform(post(base).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Linked","repositoryUrl":" https://github.com/owner/repo.git "}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.repositoryUrl").value("https://github.com/owner/repo.git"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
        assertThat(projects.findById(id).orElseThrow().getRepositoryUrl()).isEqualTo("https://github.com/owner/repo.git");
        mvc.perform(put(base + "/" + id).with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Linked\",\"repositoryUrl\":\"https://github.com/other/repo\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/" + id).with(user(principal(member))))
                .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/other/repo"));
        mvc.perform(put(base + "/" + id).with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Linked\",\"repositoryUrl\":\"  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.repositoryUrl").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://github.com/owner/repo", "javascript:alert(1)", "https://evil.test/owner/repo",
            "https://github.com.evil.test/owner/repo", "https://token@github.com/owner/repo",
            "https://github.com/owner/repo?token=secret", "https://github.com/owner/repo#secret",
            "https://github.com/owner", "https://github.com/owner/..", "https://github.com:443/owner/repo"})
    void invalidRepositoryLinksCannotBeSaved(String url) throws Exception {
        for (var request : new MockHttpServletRequestBuilder[]{post(base), put(base + "/" + project.getId())}) {
            mvc.perform(request.with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Linked\",\"repositoryUrl\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(project.getRepositoryUrl()).isNull();
    }

    @Test
    void repositoryLinkLengthIsLimited() throws Exception {
        mvc.perform(post(base).with(user(principal(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Linked\",\"repositoryUrl\":\"https://github.com/owner/" + "r".repeat(2048) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restoreIsIdempotentAndPreservesHistoryAndLink() throws Exception {
        project.setRepositoryUrl("https://github.com/owner/repo");
        project.setArchivedAt(Instant.now());
        var entry = entries.saveAndFlush(new TimeEntry(member, team, project, EntryType.MANUAL,
                LocalDate.now(), null, null, 3600, "Preserved"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(base + "/" + project.getId() + "/restore").with(user(principal(owner))).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isEmpty())
                    .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/owner/repo"));
        }
        assertThat(entries.findById(entry.getId())).isPresent();
        mvc.perform(put(base + "/" + project.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Restored\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/timer/start").with(user(principal(member))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"projectId\":\"%s\"}".formatted(team.getId(), project.getId())))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownerCanDeleteEmptyActiveOrArchivedProject(boolean archived) throws Exception {
        if (archived) project.setArchivedAt(Instant.now());
        mvc.perform(delete(base + "/" + project.getId()).with(user(principal(owner))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(projects.findById(project.getId())).isEmpty();
        assertThat(projects.findById(otherProject.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"manual", "running", "paused", "completed"})
    void anyEntryPreventsDeletingTheProject(String kind) throws Exception {
        boolean manual = kind.equals("manual");
        var entry = new TimeEntry(member, team, project, manual ? EntryType.MANUAL : EntryType.TIMER,
                LocalDate.now(), manual ? null : Instant.now().minusSeconds(60),
                kind.equals("completed") ? Instant.now() : null, manual || kind.equals("completed") ? 60 : null, "Preserved");
        if (kind.equals("paused")) entry.setPausedAt(Instant.now());
        entries.saveAndFlush(entry);
        mvc.perform(delete(base + "/" + project.getId()).with(user(principal(owner))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(projects.findById(project.getId())).isPresent();
        assertThat(entries.findById(entry.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"restore", "delete"})
    void archivedTeamBlocksProjectRestoreAndDelete(String operation) throws Exception {
        team.setArchivedAt(Instant.now());
        teams.flush();
        mvc.perform(request(operation, base, project.getId()).with(user(principal(owner))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(projects.findById(project.getId())).isPresent();
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
            case "restore" -> post(path + "/" + id + "/restore");
            case "delete" -> delete(path + "/" + id);
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
