package pl.macieserafin.hourforge.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.operator.OperatorService;
import pl.macieserafin.hourforge.security.BotCheck;

/** Settings the frontend needs before anyone signs in. Nothing secret: the bot check key is public by design, and the notice is meant for everyone. */
@RestController
public class PublicConfigController {
    public record PublicConfig(String botCheckSiteKey, OperatorService.AnnouncementView announcement) {
    }

    private final BotCheck botCheck;
    private final OperatorService operator;

    public PublicConfigController(BotCheck botCheck, OperatorService operator) {
        this.botCheck = botCheck;
        this.operator = operator;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(botCheck.siteKey(), operator.announcement().orElse(null));
    }
}
