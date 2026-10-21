package pl.macieserafin.hourforge.report;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

/** Builds the downloadable versions of a report. */
@Service
public class ReportExportService {
    private static final int BUCKET_PAGE = 100;
    private static final int MAX_BUCKET_PAGES = 10;

    private final ReportService reports;
    private final ReportRepository repository;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final Clock clock;
    private final int maxCsvRows;
    private final int maxPdfEntries;

    public ReportExportService(ReportService reports, ReportRepository repository, UserRepository users,
                               ProjectRepository projects, Clock clock,
                               @Value("${app.report-export.max-rows:50000}") int maxCsvRows,
                               @Value("${app.report-export.pdf-max-entries:2000}") int maxPdfEntries) {
        this.reports = reports;
        this.repository = repository;
        this.users = users;
        this.projects = projects;
        this.clock = clock;
        this.maxCsvRows = maxCsvRows;
        this.maxPdfEntries = maxPdfEntries;
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

    /**
     * A readable report: totals, time per person, project and day, and the entries. The owner gets the whole team,
     * a member only their own entries ({@code own}). A very long entry list is cut and says so.
     */
    @Transactional(readOnly = true)
    public Download pdf(UUID actorId, UUID teamId, ReportFilter filter, boolean own) {
        var context = reports.exportContext(actorId, teamId, filter, own);
        var f = context.filter();
        var totals = repository.totals(teamId, f);
        var zone = ZoneId.of(context.timezone());
        try (var pdf = new ReportPdf()) {
            pdf.title(own ? "Mój raport czasu pracy" : "Raport czasu pracy zespołu");
            pdf.paragraph("Zespół: " + context.teamName());
            if (own) pdf.paragraph("Osoba: " + users.findById(actorId).map(User::getDisplayName).orElse(""));
            pdf.paragraph(f.undated() ? "Zakres: wpisy bez daty"
                    : "Zakres: " + f.from() + " – " + f.to() + " (strefa czasowa zespołu: " + context.timezone() + ")");
            var criteria = criteria(f, own);
            if (!criteria.isEmpty()) pdf.paragraph("Filtry: " + criteria);
            pdf.heading("Podsumowanie");
            pdf.paragraph("Łączny czas: " + ReportCsv.clock(totals.durationSeconds()) + "   ·   Liczba wpisów: " + totals.entryCount());
            pdf.note("Według daty pracy. Czas w toku nie jest wliczany; wpisy bez daty nie wchodzą do zestawień okresowych.");

            if (!own) breakdown(pdf, "Czas według osób", "Osoba", teamId, f, ReportGrouping.MEMBER);
            breakdown(pdf, "Czas według projektów", "Projekt", teamId, f, ReportGrouping.PROJECT);
            breakdown(pdf, "Czas według dni", "Dzień", teamId, f, ReportGrouping.DAY);

            pdf.heading("Wpisy");
            var rows = new ArrayList<List<String>>();
            boolean truncated = reports.forEachEntry(teamId, f, maxPdfEntries, entry -> rows.add(own
                    ? List.of(date(entry.workDate()), entry.projectName(), tag(entry), jira(entry), text(entry.description()), kind(entry.entryType()), duration(entry))
                    : List.of(date(entry.workDate()), entry.displayName(), entry.projectName(), tag(entry), jira(entry), text(entry.description()),
                            kind(entry.entryType()), duration(entry))));
            if (rows.isEmpty()) {
                pdf.paragraph("Brak wpisów dla wybranych filtrów.");
            } else {
                pdf.table(own
                        ? List.of(new ReportPdf.Column("Data pracy", 70), new ReportPdf.Column("Projekt", 120), new ReportPdf.Column("Tag", 70), new ReportPdf.Column("Jira", 65), new ReportPdf.Column("Opis pracy", 290),
                                new ReportPdf.Column("Rodzaj", 70), new ReportPdf.Column("Czas", 58, true))
                        : List.of(new ReportPdf.Column("Data pracy", 70), new ReportPdf.Column("Osoba", 105), new ReportPdf.Column("Projekt", 100), new ReportPdf.Column("Tag", 60), new ReportPdf.Column("Jira", 65),
                                new ReportPdf.Column("Opis pracy", 230), new ReportPdf.Column("Rodzaj", 70), new ReportPdf.Column("Czas", 58, true)),
                        rows);
            }
            if (truncated) {
                pdf.note("Pokazano pierwsze " + maxPdfEntries + " z " + totals.entryCount() + " wpisów. "
                        + (own ? "Zawęź zakres dat, aby zobaczyć resztę." : "Pełną listę zawiera eksport CSV."));
            }
            var generated = clock.instant().atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            var name = "hourforge-" + (own ? "moj-raport-" : "raport-") + rangeLabel(f) + ".pdf";
            return new Download(name, pdf.finish("HourForge · " + context.teamName() + " · wygenerowano " + generated));
        }
    }

    private void breakdown(ReportPdf pdf, String heading, String label, UUID teamId, ReportFilter filter, ReportGrouping grouping) {
        var rows = new ArrayList<List<String>>();
        for (int page = 0; page < MAX_BUCKET_PAGES; page++) {
            var result = repository.breakdown(teamId, filter, grouping, page, BUCKET_PAGE);
            for (var bucket : result.content()) {
                rows.add(List.of(switch (grouping) {
                    case MEMBER -> bucket.displayName();
                    case PROJECT -> bucket.projectName() + (bucket.projectArchivedAt() != null ? " (archiwalny)" : "");
                    default -> date(bucket.workDate());
                }, ReportCsv.clock(bucket.durationSeconds()), Long.toString(bucket.entryCount())));
            }
            if (!result.hasNext()) break;
        }
        pdf.heading(heading);
        if (rows.isEmpty()) {
            pdf.paragraph("Brak zapisanego czasu dla wybranych filtrów.");
        } else {
            pdf.table(List.of(new ReportPdf.Column(label, 420), new ReportPdf.Column("Czas", 90, true), new ReportPdf.Column("Wpisy", 60, true)), rows);
        }
    }

    private String criteria(ReportFilter f, boolean own) {
        var parts = new ArrayList<String>();
        if (!own && !f.userIds().isEmpty()) {
            parts.add("osoby: " + users.findAllById(f.userIds()).stream().map(User::getDisplayName).sorted().collect(Collectors.joining(", ")));
        }
        if (!f.projectIds().isEmpty()) {
            parts.add("projekty: " + projects.findAllById(f.projectIds()).stream().map(Project::getName).sorted().collect(Collectors.joining(", ")));
        }
        if (f.entryType() != null) parts.add("rodzaj: " + kind(f.entryType()));
        if (f.q() != null) parts.add("opis zawiera „" + f.q() + "”");
        return String.join("; ", parts);
    }

    private static String date(java.time.LocalDate date) {
        return date == null ? "Bez daty" : date.toString();
    }

    private static String tag(ReportResponse.Entry entry) {
        return entry.tagName() == null ? "" : entry.tagName();
    }

    private static String jira(ReportResponse.Entry entry) {
        return entry.jiraIssueKey() == null ? "" : entry.jiraIssueKey();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "Bez opisu" : value;
    }

    private static String kind(EntryType type) {
        return type == EntryType.TIMER ? "Timer" : "Wpis ręczny";
    }

    private static String duration(ReportResponse.Entry entry) {
        return ReportCsv.clock(entry.durationSeconds() == null ? 0 : entry.durationSeconds());
    }

    static String rangeLabel(ReportFilter filter) {
        return filter.undated() ? "bez-daty" : filter.from() + "_" + filter.to();
    }
}
