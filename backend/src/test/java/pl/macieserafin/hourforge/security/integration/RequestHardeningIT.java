package pl.macieserafin.hourforge.security.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rejected input is answered briefly and never copied into the log; oversized bodies are refused before they are read. */
class RequestHardeningIT extends ApiIntegrationTestSupport {
    private static final String MARKER = "RejectedValueMarker";

    @Autowired MockMvc mvc;

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        root.addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        root.detachAppender(logged);
    }

    private String registration(String displayName) {
        return "{\"displayName\":\"" + displayName + "\",\"email\":\"hardening@example.test\",\"password\":\"Valid-password-2026!\"}";
    }

    @Test
    void aRejectedValueIsAnsweredWith400AndNeverWrittenToTheLog() throws Exception {
        var tooLong = MARKER + "x".repeat(400);

        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration(tooLong)))
                .andExpect(status().isBadRequest());

        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(message -> message.contains(MARKER));
    }

    @Test
    void aMalformedBodyIsAnsweredWith400AndNeverWrittenToTheLog() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"displayName\":\"" + MARKER + "\""))
                .andExpect(status().isBadRequest());

        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(message -> message.contains(MARKER));
    }

    @Test
    void aBodyOverTheLimitIsRefusedWith413AndNotLogged() throws Exception {
        var huge = MARKER + "x".repeat(300 * 1024);

        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration(huge)))
                .andExpect(status().isPayloadTooLarge());

        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(message -> message.contains(MARKER));
    }

    @Test
    void aBodyJustUnderTheLimitIsStillRead() throws Exception {
        var largeButAllowed = "x".repeat(200 * 1024);

        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration(largeButAllowed)))
                .andExpect(status().isBadRequest());
    }
}
