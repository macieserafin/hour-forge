package pl.macieserafin.hourforge.report;

import java.util.UUID;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.RequestRateLimits;

@RestController
@RequestMapping("/api/teams/{teamId}/reports")
public class ReportController {
    private final ReportService reports;
    private final ReportExportService exports;
    private final RequestRateLimits rateLimits;

    public ReportController(ReportService reports, ReportExportService exports, RequestRateLimits rateLimits) {
        this.reports = reports;
        this.exports = exports;
        this.rateLimits = rateLimits;
    }

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

    @GetMapping("/timesheet")
    public ReportResponse.Timesheet timesheet(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter) {
        return reports.timesheet(actor.getUserId(), teamId, filter);
    }

    @GetMapping("/entries")
    public ReportResponse.Result<ReportResponse.Entry> entries(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter,
            @RequestParam(defaultValue = "WORK_DATE") ReportEntrySort sort,
            @RequestParam(defaultValue = "DESC") Direction direction,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reports.entries(actor.getUserId(), teamId, filter, sort, direction == Direction.ASC, page, size);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> csv(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter) {
        rateLimits.reportExport(actor.getUserId());
        return download(exports.csv(actor.getUserId(), teamId, filter), "text/csv; charset=UTF-8");
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> xlsx(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter) {
        rateLimits.reportExport(actor.getUserId());
        return download(exports.xlsx(actor.getUserId(), teamId, filter), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter) {
        rateLimits.reportExport(actor.getUserId());
        return download(exports.pdf(actor.getUserId(), teamId, filter, false), "application/pdf");
    }

    /** Any active member can download their own entries; the person filter is ignored. */
    @GetMapping("/mine/export.pdf")
    public ResponseEntity<byte[]> ownPdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID teamId, @ModelAttribute ReportFilter filter) {
        rateLimits.reportExport(actor.getUserId());
        return download(exports.pdf(actor.getUserId(), teamId, filter, true), "application/pdf");
    }

    private static ResponseEntity<byte[]> download(ReportExportService.Download download, String contentType) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(download.content());
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
