package pl.macieserafin.hourforge.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetTeamsTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;

    private User current;
    private User other;
    private AppUserPrincipal principal;

    @BeforeEach
    void fixtures() {
        current = users.saveAndFlush(new User("teams-list@example.com", "Current"));
        other = users.saveAndFlush(new User("teams-other@example.com", "Other"));
        principal = new AppUserPrincipal(current.getId(), current.getEmail(), "unused-in-request-test");
    }

    @Test
    void listsOnlyOwnActiveTeamsWithRolePerTeam() throws Exception {
        var owned = createTeam("Owned", current, TeamRole.OWNER);
        var joined = createTeam("Joined", current, TeamRole.MEMBER);
        members.saveAndFlush(new TeamMember(joined, other, TeamRole.OWNER, Instant.now()));
        var departed = createTeam("Departed", current, TeamRole.MEMBER);
        members.findByTeamIdAndUserId(departed.getId(), current.getId()).orElseThrow().setLeftAt(Instant.now());
        createTeam("Unrelated", other, TeamRole.OWNER);

        mvc.perform(get("/api/teams").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(owned.getId().toString(), joined.getId().toString())))
                .andExpect(jsonPath("$[?(@.id == '" + owned.getId() + "')].role").value(contains("OWNER")))
                .andExpect(jsonPath("$[?(@.id == '" + joined.getId() + "')].role").value(contains("MEMBER")))
                .andExpect(jsonPath("$[0].timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void returnsEmptyArrayWhenUserHasNoTeams() throws Exception {
        createTeam("Someone else's", other, TeamRole.OWNER);
        mvc.perform(get("/api/teams").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void queryParameterCannotChangeRequestingUser() throws Exception {
        var own = createTeam("Own", current, TeamRole.MEMBER);
        createTeam("Other", other, TeamRole.OWNER);
        mvc.perform(get("/api/teams").param("userId", other.getId().toString()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.getId().toString()));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/teams")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSessionForDeletedUser() throws Exception {
        var deleted = new AppUserPrincipal(UUID.randomUUID(), "deleted@example.com", "unused");
        mvc.perform(get("/api/teams").with(user(deleted))).andExpect(status().isUnauthorized());
    }

    private Team createTeam(String name, User member, TeamRole role) {
        var team = teams.saveAndFlush(new Team(name, "Europe/Warsaw"));
        members.saveAndFlush(new TeamMember(team, member, role, Instant.now()));
        return team;
    }
}
