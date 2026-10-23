package pl.macieserafin.hourforge.auth.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import pl.macieserafin.hourforge.support.BrowserSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.user.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired TeamMemberRepository members;
    @Autowired PasswordEncoder encoder;
    private static final String BODY = """
            {"displayName":" New User ","email":" New@Example.test ","password":"Valid-password-2026!"}
            """;

    @Test void registersAnAnonymousAccountAndAllowsLoginOnlyAfterConfirmationWithoutGrantingTeamAccess() throws Exception {
        var session = new BrowserSession(mvc);
        long membershipCount = members.count();
        session.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(BODY))
                .andExpect(status().isCreated()).andExpect(content().string(""));
        var user = users.findByEmailIgnoreCase("new@example.test").orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("New User");
        var hash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("Valid-password-2026!");
        assertThat(encoder.matches("Valid-password-2026!", hash)).isTrue();
        assertThat(members.count()).isEqualTo(membershipCount);
        session.perform(get("/api/me")).andExpect(status().isUnauthorized());
        session.perform(post("/api/auth/login").with(csrf()).param("email", " NEW@EXAMPLE.TEST ")
                        .param("password", "Valid-password-2026!"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(post("/api/auth/email-verification/confirm").with(csrf()).contentType("application/json")
                        .content("{\"token\":\"" + mail.lastToken("verify-email") + "\"}"))
                .andExpect(status().isNoContent());
        session = new BrowserSession(mvc);
        session.perform(post("/api/auth/login").with(csrf()).param("email", " NEW@EXAMPLE.TEST ")
                        .param("password", "Valid-password-2026!"))
                .andExpect(status().isNoContent());
        session.perform(get("/api/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New User")).andExpect(jsonPath("$.password").doesNotExist());
        session.perform(get("/api/teams")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void duplicateEmailLooksLikeSuccessPreservesTheExistingPasswordAndWarnsTheOwner() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isCreated());
        var original = credentials.findByUserEmailIgnoreCase("new@example.test").orElseThrow().getPasswordHash();
        long userCount = users.count();
        mail.clear();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(BODY.replace("New@Example.test", "NEW@EXAMPLE.TEST").replace("Valid-password-2026!", "Different-password!")))
                .andExpect(status().isCreated());
        assertThat(credentials.findByUserEmailIgnoreCase("new@example.test").orElseThrow().getPasswordHash()).isEqualTo(original);
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo("new@example.test");
        assertThat(mail.last().body()).contains("/forgot-password").doesNotContain("Different-password!");
    }

    @Test void rejectsMissingAndInvalidCsrfWithoutCreatingAnAccount() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(csrf().useInvalidToken()).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        assertThat(users.findByEmailIgnoreCase("new@example.test")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"displayName\":\" \",\"email\":\"new@example.test\",\"password\":\"Valid-password-2026!\"}",
        "{\"displayName\":\"User\",\"email\":\"invalid\",\"password\":\"Valid-password-2026!\"}",
        "{\"displayName\":\"User\",\"email\":\"new@example.test\",\"password\":\"short\"}",
        "{\"displayName\":\"User\",\"email\":\"new@example.test\"}",
        "{}"
    })
    void rejectsInvalidInputWithoutPersistingPartialAccounts(String body) throws Exception {
        long userCount = users.count();
        long credentialCount = credentials.count();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(credentials.count()).isEqualTo(credentialCount);
    }

    static java.util.stream.Stream<String> boundaryPasswords() {
        return java.util.stream.Stream.of("a".repeat(12), "a".repeat(72), "ą".repeat(36), "😀".repeat(18), " leading password ");
    }

    @ParameterizedTest
    @MethodSource("boundaryPasswords")
    void acceptsBoundaryPasswordsAndPreservesWhitespace(String password) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(BODY.replace("Valid-password-2026!", password)))
                .andExpect(status().isCreated());
        var hash = credentials.findByUserEmailIgnoreCase("new@example.test").orElseThrow().getPasswordHash();
        assertThat(encoder.matches(password, hash)).isTrue();
    }

    @Test void rejectsOversizedUnicodePasswordsWithoutReturningTheirValue() throws Exception {
        String password = "ą".repeat(37);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(BODY.replace("Valid-password-2026!", password)))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(password))));
        assertThat(users.findByEmailIgnoreCase("new@example.test")).isEmpty();
    }
}
