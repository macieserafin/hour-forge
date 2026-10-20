package pl.macieserafin.hourforge.jira.integration;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.jira.JiraAtlassianClient;
import pl.macieserafin.hourforge.jira.JiraIntegrationService;
import pl.macieserafin.hourforge.jira.TokenCipher;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.TeamRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The default: the integration is off, nothing of it is reachable and nothing needs configuring. */
class JiraDisabledIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void whenSwitchedOffTheStatusSaysSoAndEveryOtherEndpointIsMissing() throws Exception {
        var person = fixtures.user("owner@example.com", "Owner");
        var team = fixtures.team("Studio", "UTC");
        fixtures.member(team, person, TeamRole.OWNER, Instant.now());
        var principal = new AppUserPrincipal(person.getId(), person.getEmail(), "unused");

        mvc.perform(get("/api/integrations/jira/status").param("teamId", team.getId().toString()).with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/integrations/jira/authorize").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"" + team.getId() + "\",\"intent\":\"SITE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/integrations/jira/issues").param("teamId", team.getId().toString()).with(user(principal)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/integrations/jira/callback").param("code", "x").param("state", "y").with(user(principal)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEntryCannotBeLinkedToAnIssueWhenSwitchedOff() throws Exception {
        var person = fixtures.user("worker@example.com", "Worker");
        var team = fixtures.team("Studio", "UTC");
        fixtures.member(team, person, TeamRole.MEMBER, Instant.now());
        var project = fixtures.project(team, "Website", null);
        var principal = new AppUserPrincipal(person.getId(), person.getEmail(), "unused");
        var body = "{\"teamId\":\"" + team.getId() + "\",\"projectId\":\"" + project.getId() + "\",\"durationSeconds\":60,\"jiraIssueId\":\"10001\"}";
        mvc.perform(post("/api/time-entries").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/time-entries").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(",\"jiraIssueId\":\"10001\"", "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.jiraIssueKey").doesNotExist());
    }

    @Test
    void noJiraBeansExistWhenSwitchedOff() {
        assertThat(context.getBeansOfType(TokenCipher.class)).isEmpty();
        assertThat(context.getBeansOfType(JiraAtlassianClient.class)).isEmpty();
        assertThat(context.getBeansOfType(JiraIntegrationService.class)).isEmpty();
    }
}
