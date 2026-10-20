package pl.macieserafin.hourforge.project.integration;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectColorIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;

    private User owner;
    private Team team;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        owner = fixtures.user("color@example.com", "Color");
        team = fixtures.team("Colors", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        principal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aProjectCanBeCreatedChangedAndClearedOfItsColour() throws Exception {
        var url = "/api/teams/" + team.getId() + "/projects";
        var json = send(post(url), "{\"name\":\"Painted\",\"color\":\"TEAL\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.color").value("TEAL"))
                .andReturn().getResponse().getContentAsString();
        var id = JsonPath.<String>read(json, "$.id");
        send(put(url + "/" + id), "{\"name\":\"Painted\",\"color\":\"AMBER\"}").andExpect(status().isOk()).andExpect(jsonPath("$.color").value("AMBER"));
        mvc.perform(get(url + "/" + id).with(user(principal))).andExpect(jsonPath("$.color").value("AMBER"));
        send(put(url + "/" + id), "{\"name\":\"Painted\"}").andExpect(status().isOk()).andExpect(jsonPath("$.color").doesNotExist());
        send(post(url), "{\"name\":\"Plain\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.color").doesNotExist());
    }

    @Test
    void onlyTheFixedPaletteIsAccepted() throws Exception {
        var url = "/api/teams/" + team.getId() + "/projects";
        send(post(url), "{\"name\":\"Bad\",\"color\":\"#ff0000\"}").andExpect(status().isBadRequest());
        send(post(url), "{\"name\":\"Bad\",\"color\":\"red\"}").andExpect(status().isBadRequest());
        send(post(url), "{\"name\":\"Bad\",\"color\":\"<script>\"}").andExpect(status().isBadRequest());
    }

    @Test
    void theColourReachesTheHistoryAndTheReports() throws Exception {
        var url = "/api/teams/" + team.getId() + "/projects";
        var json = send(post(url), "{\"name\":\"Painted\",\"color\":\"PINK\"}").andReturn().getResponse().getContentAsString();
        var project = projects().findByIdAndTeamId(java.util.UUID.fromString(JsonPath.read(json, "$.id")), team.getId()).orElseThrow();
        var today = LocalDate.now(ZoneOffset.UTC);
        entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.MANUAL, today, null, null, 600, "Colourful"));

        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString()).with(user(principal)))
                .andExpect(jsonPath("$.content[0].projectColor").value("PINK"));
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/entries").param("from", today.toString()).param("to", today.toString()).with(user(principal)))
                .andExpect(jsonPath("$.data.content[0].projectColor").value("PINK"));
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/breakdown").param("from", today.toString()).param("to", today.toString())
                        .param("groupBy", "PROJECT").with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].projectColor").value("PINK"));
    }

    @Autowired pl.macieserafin.hourforge.project.ProjectRepository projectRepository;

    private pl.macieserafin.hourforge.project.ProjectRepository projects() {
        return projectRepository;
    }
}
