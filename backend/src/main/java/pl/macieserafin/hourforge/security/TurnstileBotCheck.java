package pl.macieserafin.hourforge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Cloudflare Turnstile: the widget gives the browser a one-time token, which the server checks with Cloudflare before
 * an account is created. Off without both keys, so instances that do not want it need no configuration.
 * When Cloudflare cannot be reached the check fails, so registration waits instead of letting scripts through.
 */
@Component
public class TurnstileBotCheck implements BotCheck {
    private static final Logger log = LoggerFactory.getLogger(TurnstileBotCheck.class);

    private final String siteKey;
    private final String secretKey;
    private final String verifyUrl;
    private final RestClient client;

    public TurnstileBotCheck(@Value("${app.bot-check.site-key:}") String siteKey, @Value("${app.bot-check.secret-key:}") String secretKey,
                             @Value("${app.bot-check.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl) {
        var enabled = !siteKey.isBlank() && !secretKey.isBlank();
        this.siteKey = enabled ? siteKey.strip() : null;
        this.secretKey = enabled ? secretKey.strip() : null;
        this.verifyUrl = verifyUrl;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String siteKey() {
        return siteKey;
    }

    @Override
    public boolean passes(String token, String remoteAddress) {
        if (siteKey == null) return true;
        // Turnstile tokens are at most 2048 characters.
        if (token == null || token.isBlank() || token.length() > 2048) return false;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteAddress != null) form.add("remoteip", remoteAddress);
        try {
            Map<?, ?> result = client.post().uri(verifyUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RestClientException exception) {
            log.warn("Bot check could not be verified: {}", exception.getClass().getSimpleName());
            return false;
        }
    }
}
