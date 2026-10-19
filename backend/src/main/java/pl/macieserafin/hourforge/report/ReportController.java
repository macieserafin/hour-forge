package pl.macieserafin.hourforge.report;

import java.util.UUID;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/teams/{teamId}/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/summary")
    public ReportResponse.Summary summary(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter) {
        return reports.summary(actor.getUserId(), teamId, filter);
    }

    @GetMapping("/breakdown")
    public ReportResponse.Breakdown breakdown(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter,
            @RequestParam(defaultValue = "MEMBER") ReportGrouping groupBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reports.breakdown(actor.getUserId(), teamId, filter, groupBy, page, size);
    }

    @GetMapping("/entries")
    public ReportResponse.Result<ReportResponse.Entry> entries(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter,
            @RequestParam(defaultValue = "WORK_DATE") ReportEntrySort sort,
            @RequestParam(defaultValue = "DESC") Direction direction,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reports.entries(actor.getUserId(), teamId, filter, sort, direction == Direction.ASC, page, size);
    }

    @GetMapping("/active-timers")
    public ReportResponse.Active active(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reports.active(actor.getUserId(), teamId, filter, page, size);
    }

    @GetMapping("/members")
    public ReportResponse.Result<ReportResponse.Member> members(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.members(actor.getUserId(), teamId, page, size);
    }

    @GetMapping("/projects")
    public ReportResponse.Result<ReportResponse.Project> projects(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.projects(actor.getUserId(), teamId, page, size);
    }
}
