package pl.macieserafin.hourforge.dashboard;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/api/dashboard/summary")
    public DashboardSummary summary(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam UUID teamId,
                                    @RequestParam(defaultValue = "MONDAY") pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart weekStart) {
        return dashboard.summary(principal.getUserId(), teamId, weekStart);
    }
}
