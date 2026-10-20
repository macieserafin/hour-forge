package pl.macieserafin.hourforge.jira;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.RequestRateLimits;

/** The Jira connection endpoints. They exist only while the integration is switched on. */
@RestController
@RequestMapping("/api/integrations/jira")
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraOAuthController {
    public record AuthorizeRequest(@NotNull UUID teamId, @NotNull OAuthState.Intent intent) {
    }

    public record AuthorizeResponse(String url) {
    }

    public record SelectSiteRequest(@NotNull UUID teamId, @NotNull @Size(min = 1, max = 100) String cloudId) {
    }

    private final JiraIntegrationService integration;
    private final RequestRateLimits rateLimits;
    private final String frontendUrl;

    public JiraOAuthController(JiraIntegrationService integration, RequestRateLimits rateLimits,
                               @Value("${app.frontend-url}") String frontendUrl) {
        this.integration = integration;
        this.rateLimits = rateLimits;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @PostMapping("/authorize")
    public AuthorizeResponse authorize(@AuthenticationPrincipal AppUserPrincipal principal, HttpServletRequest http,
                                       @Valid @RequestBody AuthorizeRequest request) {
        rateLimits.jiraAuthorize(principal.getUserId());
        return new AuthorizeResponse(integration.startAuthorization(principal.getUserId(), request.teamId(), request.intent(),
                http.getSession().getId()));
    }

    /** Atlassian sends the browser here; the answer is always a redirect back into the app with a fixed result word. */
    @GetMapping("/callback")
    public void callback(@AuthenticationPrincipal AppUserPrincipal principal, HttpServletRequest http, HttpServletResponse response,
                         @RequestParam(required = false) String code, @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error) throws IOException {
        var result = integration.complete(principal.getUserId(), http.getSession().getId(), code, state, error);
        var target = new StringBuilder(frontendUrl).append("/integrations?jira=").append(result.outcome().code());
        if (result.teamId() != null) target.append("&team=").append(result.teamId());
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(target.toString());
    }

    @GetMapping("/sites")
    public List<JiraIntegrationService.SiteChoice> sites(@AuthenticationPrincipal AppUserPrincipal principal,
                                                          @RequestParam UUID teamId) {
        return integration.sites(principal.getUserId(), teamId);
    }

    @PostMapping("/site")
    public ResponseEntity<Void> selectSite(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @Valid @RequestBody SelectSiteRequest request) {
        integration.selectSite(principal.getUserId(), request.teamId(), request.cloudId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnectMe(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam UUID teamId) {
        integration.disconnectUser(principal.getUserId(), teamId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/site")
    public ResponseEntity<Void> disconnectSite(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam UUID teamId) {
        integration.disconnectSite(principal.getUserId(), teamId);
        return ResponseEntity.noContent().build();
    }
}
