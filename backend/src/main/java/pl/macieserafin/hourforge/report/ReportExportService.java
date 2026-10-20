package pl.macieserafin.hourforge.report;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Builds the downloadable versions of a report. */
@Service
public class ReportExportService {
    private final ReportService reports;
    private final int maxCsvRows;

    public ReportExportService(ReportService reports, @Value("${app.report-export.max-rows:50000}") int maxCsvRows) {
        this.reports = reports;
        this.maxCsvRows = maxCsvRows;
    }

    public record Download(String filename, byte[] content) {
    }

    /** The whole result of the filter, or an error asking to narrow it; a spreadsheet must never silently lose rows. */
    public Download csv(UUID actorId, UUID teamId, ReportFilter filter) {
        var context = reports.exportContext(actorId, teamId, filter, false);
        var csv = new ReportCsv();
        boolean truncated = reports.forEachEntry(teamId, context.filter(), maxCsvRows, entry -> csv.entry(entry, context.teamName()));
        if (truncated) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The report has more than " + maxCsvRows + " entries; narrow the dates or the filters");
        }
        return new Download("hourforge-raport-" + rangeLabel(context.filter()) + ".csv", csv.bytes());
    }

    static String rangeLabel(ReportFilter filter) {
        return filter.undated() ? "bez-daty" : filter.from() + "_" + filter.to();
    }
}
