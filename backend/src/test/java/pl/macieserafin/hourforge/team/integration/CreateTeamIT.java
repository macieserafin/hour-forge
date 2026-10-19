package pl.macieserafin.hourforge.team.integration;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateTeamIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;

    private User creator;
    private AppUserPrincipal principal;

    @BeforeEach
    void createUser() {
        creator = fixtures.user("team-creator@example.com", "Creator");
        principal = new AppUserPrincipal(creator.getId(), creator.getEmail(), "unused-in-request-test");
    }

    @Test
    void createsTeamAndActiveOwnerUsingAuthenticatedIdentity() throws Exception {
        var other = fixtures.user("other-creator@example.com", "Other");
        var before = Instant.now();
        var response = mvc.perform(post("/api/teams").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Forge Team  ","timezone":" Europe/Warsaw ",
                                 "userId":"%s","role":"MEMBER"}
                                """.formatted(other.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Forge Team"))
                .andExpect(jsonPath("$.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse();

        UUID teamId = UUID.fromString(JsonPath.read(response.getContentAsString(), "$.id"));
        var team = teams.findById(teamId).orElseThrow();
        assertThat(team.getCreatedAt()).isBetween(before, Instant.now());
        var teamMembers = members.findAll().stream().filter(member -> member.getTeam().getId().equals(teamId)).toList();
        assertThat(teamMembers).hasSize(1);
        var owner = teamMembers.getFirst();
        assertThat(owner.getUser().getId()).isEqualTo(creator.getId());
        assertThat(owner.getRole()).isEqualTo(TeamRole.OWNER);
        assertThat(owner.getLeftAt()).isNull();
        assertThat(owner.getJoinedAt()).isBetween(before, Instant.now());
    }

    @Test
    void userCanCreateMultipleTeamsWithoutGlobalOwnerRole() throws Exception {
        for (String name : new String[]{"First", "Second"}) {
            mvc.perform(post("/api/teams").with(user(principal)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\",\"timezone\":\"UTC\"}"))
                    .andExpect(status().isCreated());
        }
        assertThat(members.findAll().stream()
                .filter(member -> member.getUser().getId().equals(creator.getId()) && member.getRole() == TeamRole.OWNER))
                .hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":null,\"timezone\":\"UTC\"}",
            "{\"name\":\"   \",\"timezone\":\"UTC\"}",
            "{\"name\":\"Team\"}",
            "{\"name\":\"Team\",\"timezone\":\"  \"}",
            "{\"name\":\"Team\",\"timezone\":\"Unknown/Zone\"}",
            "{\"name\":\"Team\",\"timezone\":\"+02:00\"}"
    })
    void rejectsInvalidInputWithoutWritingData(String body) throws Exception {
        long teamCount = teams.count();
        long memberCount = members.count();
        mvc.perform(post("/api/teams").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(teams.count()).isEqualTo(teamCount);
        assertThat(members.count()).isEqualTo(memberCount);
    }

    @Test
    void rejectsNameLongerThanDatabaseColumn() throws Exception {
        mvc.perform(post("/api/teams").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(256) + "\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        String body = "{\"name\":\"Team\",\"timezone\":\"UTC\"}";
        mvc.perform(post("/api/teams").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/teams").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesCreationForUserWhoNoLongerExists() throws Exception {
        var deleted = new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused");
        long teamCount = teams.count();
        mvc.perform(post("/api/teams").with(user(deleted)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Team\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(teams.count()).isEqualTo(teamCount);
    }
}
