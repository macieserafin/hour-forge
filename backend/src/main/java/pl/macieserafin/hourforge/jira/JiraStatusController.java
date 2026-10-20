package pl.macieserafin.hourforge.jira;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

/** Always present, so the interface can tell "integration switched off" from "not connected yet". */
@RestController
@RequestMapping("/api/integrations/jira")
public class JiraStatusController {
    private final ObjectProvider<JiraIntegrationService> integration;

    public JiraStatusController(ObjectProvider<JiraIntegrationService> integration) {
        this.integration = integration;
    }

    @GetMapping("/status")
    public JiraIntegrationService.Status status(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam UUID teamId) {
        var service = integration.getIfAvailable();
        if (service == null) return new JiraIntegrationService.Status(false, false, null, null, false, false);
        return service.status(principal.getUserId(), teamId);
    }
}
