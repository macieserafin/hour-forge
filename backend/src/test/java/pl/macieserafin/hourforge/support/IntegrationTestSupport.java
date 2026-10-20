package pl.macieserafin.hourforge.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.macieserafin.hourforge.security.RateLimiter;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestData.class, TestMailConfig.class})
public abstract class IntegrationTestSupport {
    @Autowired protected TestData fixtures;
    @Autowired protected RecordingEmailSender mail;
    @Autowired private RateLimiter rateLimiter;

    // Rate limits and recorded mail live in context-wide beans; each test starts empty.
    @BeforeEach
    void resetRateLimits() {
        rateLimiter.clear();
        mail.clear();
    }
}
