package pl.macieserafin.hourforge.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.TestBotCheck;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationBotCheckIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void withoutKeysTheFormNeedsNoTokenAndTheConfigOffersNoWidget() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk()).andExpect(jsonPath("$.botCheckSiteKey").value(nullValue()));
        register(null).andExpect(status().isCreated());
        assertThat(users.findByEmailIgnoreCase("bot-check@example.test")).isPresent();
    }

    @Test
    void whenOnRegistrationNeedsAValidTokenFromTheWidget() throws Exception {
        botCheck.turnOn();
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.botCheckSiteKey").value(TestBotCheck.SITE_KEY));

        register(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOT_CHECK_FAILED"));
        register("forged-token").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOT_CHECK_FAILED"));
        assertThat(users.findByEmailIgnoreCase("bot-check@example.test")).isEmpty();
        assertThat(mail.sent()).isEmpty();

        register(TestBotCheck.VALID_TOKEN).andExpect(status().isCreated());
        assertThat(users.findByEmailIgnoreCase("bot-check@example.test")).isPresent();
    }

    @Test
    void anOverlongTokenIsRejectedBeforeAnyCheck() throws Exception {
        botCheck.turnOn();
        register("x".repeat(5000)).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions register(String token) throws Exception {
        var body = "{\"displayName\":\"Bot Check\",\"email\":\"bot-check@example.test\",\"password\":\"Valid-password-2026!\""
                + (token == null ? "" : ",\"botCheckToken\":\"" + token + "\"") + "}";
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body));
    }
}
