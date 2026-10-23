package pl.macieserafin.hourforge.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.BotCheck;

/** Settings the frontend needs before anyone signs in. Nothing secret: the bot check key is public by design. */
@RestController
public class PublicConfigController {
    public record PublicConfig(String botCheckSiteKey) {
    }

    private final BotCheck botCheck;

    public PublicConfigController(BotCheck botCheck) {
        this.botCheck = botCheck;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(botCheck.siteKey());
    }
}
