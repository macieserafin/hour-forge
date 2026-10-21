package pl.macieserafin.hourforge.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import pl.macieserafin.hourforge.timeentry.EntryType;

/**
 * A minimal Excel workbook with one sheet: header, one row per entry. Text is stored as plain strings, never as
 * formulas, so no value can be evaluated by the spreadsheet. Built by hand to keep a large library out of the application.
 */
public final class ReportXlsx {
    private static final String[] HEADER = {"Data pracy", "Osoba", "Zespół", "Projekt", "Tag", "Zadanie Jira", "Opis", "Rodzaj", "Czas (gg:mm:ss)", "Czas (sekundy)"};
    private static final int MAX_CELL_LENGTH = 32_000;

    private final List<String> rows = new ArrayList<>();
    private int rowNumber;

    ReportXlsx() {
        row(true, (Object[]) HEADER);
    }

    void entry(ReportResponse.Entry entry, String teamName) {
        long seconds = entry.durationSeconds() == null ? 0 : entry.durationSeconds();
        row(false, entry.workDate() == null ? "" : entry.workDate().toString(), entry.displayName(), teamName, entry.projectName(),
                entry.tagName() == null ? "" : entry.tagName(), entry.jiraIssueKey() == null ? "" : entry.jiraIssueKey(),
                entry.description() == null ? "" : entry.description(), entry.entryType() == EntryType.TIMER ? "Timer" : "Wpis ręczny",
                ReportCsv.clock(seconds), seconds);
    }

    private void row(boolean header, Object... values) {
        var cells = new StringBuilder();
        for (int column = 0; column < values.length; column++) {
            var reference = (char) ('A' + column) + Integer.toString(rowNumber + 1);
            if (values[column] instanceof Long number) {
                cells.append("<c r=\"").append(reference).append("\"><v>").append(number).append("</v></c>");
            } else {
                cells.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"").append(header ? " s=\"1\"" : "").append("><is><t xml:space=\"preserve\">")
                        .append(escape(String.valueOf(values[column]))).append("</t></is></c>");
            }
        }
        rows.add("<row r=\"" + (++rowNumber) + "\">" + cells + "</row>");
    }

    byte[] bytes() {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Raport\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            put(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "</styleSheet>");
            put(zip, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\">"
                    + "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetData>"
                    + String.join("", rows) + "</sheetData></worksheet>");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** XML text without characters the format forbids; very long texts are cut to what a cell can hold. */
    static String escape(String value) {
        var text = value.length() > MAX_CELL_LENGTH ? value.substring(0, MAX_CELL_LENGTH) : value;
        var out = new StringBuilder(text.length());
        text.codePoints().forEach(code -> {
            boolean allowed = code == 0x9 || code == 0xA || code == 0xD || (code >= 0x20 && code <= 0xD7FF) || (code >= 0xE000 && code <= 0xFFFD) || code >= 0x10000;
            if (!allowed) return;
            switch (code) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.appendCodePoint(code);
            }
        });
        return out.toString();
    }
}
