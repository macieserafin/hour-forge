package pl.macieserafin.hourforge.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.google.GoogleProperties;
import pl.macieserafin.hourforge.operator.OperatorService;
import pl.macieserafin.hourforge.security.BotCheck;

/**
 * Settings the frontend needs before anyone signs in. Nothing secret: the bot check key is public by design, the notice
 * is meant for everyone, and {@code googleSignIn} only says whether the Google buttons are shown.
 */
@RestController
public class PublicConfigController {
    public record PublicConfig(String botCheckSiteKey, OperatorService.AnnouncementView announcement, boolean googleSignIn) {
    }

    private final BotCheck botCheck;
    private final OperatorService operator;
    private final GoogleProperties google;

    public PublicConfigController(BotCheck botCheck, OperatorService operator, GoogleProperties google) {
        this.botCheck = botCheck;
        this.operator = operator;
        this.google = google;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(botCheck.siteKey(), operator.announcement().orElse(null), google.enabled());
    }
}
