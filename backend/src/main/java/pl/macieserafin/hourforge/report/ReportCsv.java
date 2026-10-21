package pl.macieserafin.hourforge.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import pl.macieserafin.hourforge.timeentry.EntryType;

/**
 * CSV for spreadsheets: UTF-8 with a byte order mark and ';' as the separator (the default of Polish Excel),
 * values that could be read as formulas are neutralized, and fields with separators, quotes or line breaks are quoted.
 */
public final class ReportCsv {
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    ReportCsv() {
        out.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        row("Data pracy", "Osoba", "Zespół", "Projekt", "Tag", "Zadanie Jira", "Opis", "Rodzaj", "Czas (gg:mm:ss)", "Czas (sekundy)");
    }

    void entry(ReportResponse.Entry entry, String teamName) {
        long seconds = entry.durationSeconds() == null ? 0 : entry.durationSeconds();
        row(entry.workDate() == null ? "" : entry.workDate().toString(), entry.displayName(), teamName, entry.projectName(),
                entry.tagName() == null ? "" : entry.tagName(),
                entry.jiraIssueKey() == null ? "" : entry.jiraIssueKey(),
                entry.description() == null ? "" : entry.description(),
                entry.entryType() == EntryType.TIMER ? "Timer" : "Wpis ręczny", clock(seconds), Long.toString(seconds));
    }

    byte[] bytes() {
        return out.toByteArray();
    }

    public static String clock(long seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /** One escaped field. */
    public static String field(String value) {
        var text = value == null ? "" : value;
        // A leading =, +, -, @, tab or carriage return makes spreadsheets evaluate the cell as a formula.
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(SEPARATOR) || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private void row(String... values) {
        var line = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) line.append(SEPARATOR);
            line.append(field(values[index]));
        }
        out.writeBytes(line.append(LINE_END).toString().getBytes(StandardCharsets.UTF_8));
    }
}
