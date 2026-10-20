package pl.macieserafin.hourforge.entrytemplate.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntryTemplateIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired ProjectRepository projects;
    @Autowired TeamRepository teams;

    private User owner;
    private User member;
    private User outsider;
    private Team team;
    private Project project;
    private AppUserPrincipal ownerPrincipal;
    private AppUserPrincipal memberPrincipal;
    private AppUserPrincipal outsiderPrincipal;

    @BeforeEach
    void setup() {
        owner = fixtures.user("owner-tpl@example.com", "Owner");
        member = fixtures.user("member-tpl@example.com", "Member");
        outsider = fixtures.user("outsider-tpl@example.com", "Outsider");
        team = fixtures.team("Studio", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Website", null);
        ownerPrincipal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
        memberPrincipal = new AppUserPrincipal(member.getId(), member.getEmail(), "unused");
        outsiderPrincipal = new AppUserPrincipal(outsider.getId(), outsider.getEmail(), "unused");
    }

    private String url() {
        return "/api/teams/" + team.getId() + "/templates";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, AppUserPrincipal who, String body) throws Exception {
        entityManager.flush();
        return mvc.perform(request.with(user(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(String name, Project target, String description) {
        return "{\"name\":\"" + name + "\",\"projectId\":\"" + target.getId() + "\"" + (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
    }

    private String create(AppUserPrincipal who, String name) throws Exception {
        var json = send(post(url()), who, body(name, project, "Weekly review")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void createsListsEditsAndDeletesOwnTemplates() throws Exception {
        var id = create(memberPrincipal, "Standup");
        send(post(url()), memberPrincipal, body("Admin", project, null)).andExpect(status().isCreated()).andExpect(jsonPath("$.description").doesNotExist());
        mvc.perform(get(url()).with(user(memberPrincipal))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Admin")).andExpect(jsonPath("$[1].name").value("Standup"))
                .andExpect(jsonPath("$[1].projectId").value(project.getId().toString())).andExpect(jsonPath("$[1].description").value("Weekly review"));

        send(put(url() + "/" + id), memberPrincipal, body("Daily standup", project, "Changed")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Daily standup")).andExpect(jsonPath("$.description").value("Changed"));
        send(delete(url() + "/" + id), memberPrincipal, "").andExpect(status().isNoContent());
        mvc.perform(get(url()).with(user(memberPrincipal))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void templatesArePrivateToTheirOwnerAndTeam() throws Exception {
        var id = create(memberPrincipal, "Private");
        mvc.perform(get(url()).with(user(ownerPrincipal))).andExpect(jsonPath("$.length()").value(0));
        send(put(url() + "/" + id), ownerPrincipal, body("Stolen", project, null)).andExpect(status().isNotFound());
        send(delete(url() + "/" + id), ownerPrincipal, "").andExpect(status().isNotFound());
        mvc.perform(get(url()).with(user(outsiderPrincipal))).andExpect(status().isNotFound());
        send(post(url()), outsiderPrincipal, body("Nope", project, null)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select name from entry_templates where id = ?::uuid", String.class, id)).isEqualTo("Private");
    }

    @Test
    void namesAreUniquePerPersonAndTeamIgnoringCase() throws Exception {
        create(memberPrincipal, "Review");
        send(post(url()), memberPrincipal, body("review", project, null)).andExpect(status().isConflict());
        send(post(url()), ownerPrincipal, body("Review", project, null)).andExpect(status().isCreated());
        var other = create(memberPrincipal, "Other");
        send(put(url() + "/" + other), memberPrincipal, body("REVIEW", project, null)).andExpect(status().isConflict());
        send(put(url() + "/" + other), memberPrincipal, body("OTHER", project, null)).andExpect(status().isOk());
    }

    @Test
    void validatesInputAndLimitsTheNumber() throws Exception {
        send(post(url()), memberPrincipal, body("   ", project, null)).andExpect(status().isBadRequest());
        send(post(url()), memberPrincipal, body("x".repeat(81), project, null)).andExpect(status().isBadRequest());
        send(post(url()), memberPrincipal, body("Long", project, "d".repeat(2001))).andExpect(status().isBadRequest());
        send(post(url()), memberPrincipal, "{\"name\":\"No project\"}").andExpect(status().isBadRequest());
        for (int i = 0; i < 30; i++) create(memberPrincipal, "Template " + i);
        send(post(url()), memberPrincipal, body("One too many", project, null)).andExpect(status().isConflict());
    }

    @Test
    void theProjectMustBelongToTheTeamAndBeActive() throws Exception {
        var foreignTeam = fixtures.team("Foreign", "UTC");
        var foreign = fixtures.project(foreignTeam, "Foreign", null);
        send(post(url()), memberPrincipal, body("Foreign", foreign, null)).andExpect(status().isNotFound());
        var archived = fixtures.project(team, "Old", null);
        archived.setArchivedAt(Instant.now());
        projects.saveAndFlush(archived);
        send(post(url()), memberPrincipal, body("Old", archived, null)).andExpect(status().isConflict());
    }

    @Test
    void needsSignInAndCsrfAndFrozenTeamsRefuseChanges() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        mvc.perform(post(url()).with(user(memberPrincipal)).contentType(MediaType.APPLICATION_JSON).content(body("x", project, null))).andExpect(status().isForbidden());
        var id = create(memberPrincipal, "Before archive");
        var frozen = teams.findById(team.getId()).orElseThrow();
        frozen.setArchivedAt(Instant.now());
        teams.saveAndFlush(frozen);
        send(post(url()), memberPrincipal, body("After", project, null)).andExpect(status().isConflict());
        send(put(url() + "/" + id), memberPrincipal, body("Renamed", project, null)).andExpect(status().isConflict());
        mvc.perform(get(url()).with(user(memberPrincipal))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aJiraLinkIsRefusedWhenTheIntegrationIsOff() throws Exception {
        send(post(url()), memberPrincipal, body("Linked", project, null).replace("}", ",\"jiraIssueId\":\"10001\"}")).andExpect(status().isConflict());
    }

    @Test
    void deletingTheTeamAnEmptyProjectOrTheAccountRemovesTheTemplates() throws Exception {
        var spare = fixtures.project(team, "Spare", null);
        send(post(url()), memberPrincipal, body("On spare", spare, null)).andExpect(status().isCreated());
        send(delete("/api/teams/" + team.getId() + "/projects/" + spare.getId()), ownerPrincipal, "").andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from entry_templates where team_id = ?", Integer.class, team.getId())).isZero();

        create(memberPrincipal, "On website");
        send(delete("/api/teams/" + team.getId()), ownerPrincipal, "").andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from entry_templates where team_id = ?", Integer.class, team.getId())).isZero();
    }
}
