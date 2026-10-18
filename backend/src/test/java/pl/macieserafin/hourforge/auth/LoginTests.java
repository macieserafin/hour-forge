package pl.macieserafin.hourforge.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginTests {
    private static final String EMAIL = "login@example.com";
    private static final String PASSWORD = "Test-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationContext context;

    private User user;

    @BeforeEach
    void createUser() {
        user = users.saveAndFlush(new User(EMAIL, "Login Test"));
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void requiresAuthenticationAndDoesNotEnableSeedingInTestProfile() throws Exception {
        assertThat(context.containsBean("seedUsers")).isFalse();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfLoginSessionAndLogoutFlow() throws Exception {
        var session = new MockHttpSession();
        String originalSessionId = session.getId();
        String token = csrfToken(session);
        mvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-CSRF-TOKEN", token)
                        .param("email", EMAIL.toUpperCase()).param("password", PASSWORD))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(originalSessionId);

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Login Test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/logout").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session)))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameStatus() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", EMAIL).param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", "unknown@example.com").param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsLoginWithoutCsrfOrWithInvalidCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf().useInvalidToken())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsLogoutWithoutCsrfAndKeepsSessionAuthenticated() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void userWithoutCredentialsCannotLogIn() throws Exception {
        users.saveAndFlush(new User("no-password@example.com", "No password"));
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", "no-password@example.com").param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storesOnlyHashAndDoesNotAssignGlobalTeamRoles() throws Exception {
        var credential = credentials.findById(user.getId()).orElseThrow();
        assertThat(credential.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, credential.getPasswordHash())).isTrue();
        var session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isNoContent());
        var securityContext = (org.springframework.security.core.context.SecurityContext)
                session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(securityContext.getAuthentication().getAuthorities())
                .noneMatch(authority -> authority.getAuthority().startsWith("ROLE_"));
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.token");
    }
}
