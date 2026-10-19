package pl.macieserafin.hourforge.security.integration;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Low limits keep the scenarios short; production defaults are covered in LoginIT.
@SpringBootTest(properties = {
        "app.rate-limit.login-account-ip.max=10", "app.rate-limit.login-ip.max=3", "app.rate-limit.login-account.max=4",
        "app.rate-limit.registration.max=2", "app.rate-limit.team-creation.max=2",
        "app.rate-limit.invitation-creation.max=2", "app.rate-limit.project-creation.max=2"})
class RateLimitIT extends ApiIntegrationTestSupport {
    private static final String PASSWORD = "Rate-limit-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired TeamRepository teams;
    @Autowired ProjectRepository projects;

    private User owner;
    private User other;
    private Team team;

    @BeforeEach
    void setup() {
        owner = fixtures.user("limit-owner@example.com", "Owner");
        other = fixtures.user("limit-other@example.com", "Other");
        credentials.saveAndFlush(new UserCredential(owner, encoder.encode(PASSWORD)));
        team = fixtures.team("Limits", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
    }

    @Test
    void blocksAnAddressAfterFailuresAcrossAccounts() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            login("unknown-" + attempt + "@example.com", "wrong", "10.1.0.1").andExpect(status().isUnauthorized());
        }
        login(owner.getEmail(), PASSWORD, "10.1.0.1").andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
        login(owner.getEmail(), PASSWORD, "10.1.0.2").andExpect(status().isNoContent());
    }

    @Test
    void blocksAnAccountAttackedFromManyAddresses() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            login(owner.getEmail(), "wrong", "10.2.0." + attempt).andExpect(status().isUnauthorized());
        }
        login(owner.getEmail().toUpperCase(), PASSWORD, "10.2.0.99").andExpect(status().isTooManyRequests());
        login(other.getEmail(), "wrong", "10.2.0.99").andExpect(status().isUnauthorized());
    }

    @Test
    void limitsRegistrationsPerAddressWithoutCreatingAccounts() throws Exception {
        register("first@example.com", "10.3.0.1").andExpect(status().isCreated());
        register("second@example.com", "10.3.0.1").andExpect(status().isCreated());
        register("third@example.com", "10.3.0.1").andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"));
        assertThat(users.findByEmailIgnoreCase("third@example.com")).isEmpty();
        register("third@example.com", "10.3.0.2").andExpect(status().isCreated());
    }

    @Test
    void limitsTeamCreationPerUser() throws Exception {
        long count = teams.count();
        for (int index = 0; index < 2; index++) {
            create(post("/api/teams"), owner, "{\"name\":\"Team " + index + "\",\"timezone\":\"UTC\"}").andExpect(status().isCreated());
        }
        create(post("/api/teams"), owner, "{\"name\":\"Too many\",\"timezone\":\"UTC\"}").andExpect(status().isTooManyRequests());
        assertThat(teams.count()).isEqualTo(count + 2);
        create(post("/api/teams"), other, "{\"name\":\"Other team\",\"timezone\":\"UTC\"}").andExpect(status().isCreated());
    }

    @Test
    void limitsInvitationAndProjectCreationPerUserIndependently() throws Exception {
        String invite = "{\"expiresAt\":\"" + Instant.now().plusSeconds(3600) + "\"}";
        for (int index = 0; index < 2; index++) {
            create(post("/api/teams/{id}/invites", team.getId()), owner, invite).andExpect(status().isCreated());
        }
        create(post("/api/teams/{id}/invites", team.getId()), owner, invite).andExpect(status().isTooManyRequests());

        long count = projects.count();
        for (int index = 0; index < 2; index++) {
            create(post("/api/teams/{id}/projects", team.getId()), owner, "{\"name\":\"Project " + index + "\"}")
                    .andExpect(status().isCreated());
        }
        create(post("/api/teams/{id}/projects", team.getId()), owner, "{\"name\":\"Too many\"}")
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        assertThat(projects.count()).isEqualTo(count + 2);
    }

    private ResultActions login(String email, String password, String address) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).with(address(address))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", password));
    }

    private ResultActions register(String email, String address) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).with(address(address)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"New\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private ResultActions create(MockHttpServletRequestBuilder request, User actor, String body) throws Exception {
        return mvc.perform(request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor address(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
