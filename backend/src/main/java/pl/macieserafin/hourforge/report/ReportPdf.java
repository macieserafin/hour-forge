package pl.macieserafin.hourforge.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;

/**
 * A plain A4 landscape report: headings, paragraphs and tables with wrapped text and a repeated header row.
 * The embedded DejaVu fonts cover Polish letters; characters a font lacks are replaced instead of failing the report.
 */
final class ReportPdf implements AutoCloseable {
    record Column(String title, float width, boolean right) {
        Column(String title, float width) {
            this(title, width, false);
        }
    }

    private static final float MARGIN = 36;
    private static final float FOOTER = 28;
    private static final float BODY = 8.5f;
    private static final float LEADING = 11;
    private static final float PADDING = 3;

    private final PDDocument document = new PDDocument();
    private final PDFont regular = load("fonts/DejaVuSans.ttf");
    private final PDFont bold = load("fonts/DejaVuSans-Bold.ttf");
    private final PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private final Map<PDFont, Map<Integer, Boolean>> supported = new HashMap<>();
    private PDPage page;
    private PDPageContentStream content;
    private float y;

    ReportPdf() {
        newPage();
    }

    private PDFont load(String path) {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return PDType0Font.load(document, stream, true);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    float usableWidth() {
        return pageSize.getWidth() - 2 * MARGIN;
    }

    void title(String text) {
        line(text, bold, 16, 20);
        y -= 4;
    }

    void heading(String text) {
        ensure(40);
        y -= 10;
        line(text, bold, 11, 15);
        y -= 2;
    }

    void paragraph(String text) {
        for (var wrapped : wrap(text, regular, BODY, usableWidth())) line(wrapped, regular, BODY, LEADING);
    }

    void note(String text) {
        for (var wrapped : wrap(text, regular, BODY, usableWidth())) line(wrapped, regular, BODY, LEADING);
    }

    void table(List<Column> columns, List<List<String>> rows) {
        header(columns);
        for (var row : rows) {
            var cells = new ArrayList<List<String>>();
            int lines = 1;
            for (int index = 0; index < columns.size(); index++) {
                var wrapped = wrap(row.get(index), regular, BODY, columns.get(index).width() - 2 * PADDING);
                cells.add(wrapped);
                lines = Math.max(lines, wrapped.size());
            }
            float height = lines * LEADING + 2 * PADDING;
            if (y - height < MARGIN + FOOTER) {
                newPage();
                header(columns);
            }
            drawRow(columns, cells, regular, height);
        }
        y -= 6;
    }

    byte[] finish(String footer) {
        try {
            content.close();
            int total = document.getNumberOfPages();
            for (int index = 0; index < total; index++) {
                try (var stream = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    var text = safe(footer + " · Strona " + (index + 1) + " z " + total, regular);
                    text(stream, regular, 7.5f, MARGIN, MARGIN - 4, text, new Color(0x55, 0x55, 0x55));
                }
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void newPage() {
        try {
            if (content != null) content.close();
            page = new PDPage(pageSize);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = pageSize.getHeight() - MARGIN;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void ensure(float height) {
        if (y - height < MARGIN + FOOTER) newPage();
    }

    private void line(String text, PDFont font, float size, float leading) {
        ensure(leading);
        y -= leading;
        try {
            text(content, font, size, MARGIN, y + (leading - size) / 2, safe(text, font), Color.BLACK);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void header(List<Column> columns) {
        float height = LEADING + 2 * PADDING;
        if (y - height < MARGIN + FOOTER) newPage();
        drawRow(columns, columns.stream().map(column -> List.of(column.title())).toList(), bold, height, new Color(0xEE, 0xEE, 0xF4));
    }

    private void drawRow(List<Column> columns, List<List<String>> cells, PDFont font, float height) {
        drawRow(columns, cells, font, height, null);
    }

    private void drawRow(List<Column> columns, List<List<String>> cells, PDFont font, float height, Color background) {
        try {
            float x = MARGIN;
            float bottom = y - height;
            float tableWidth = (float) columns.stream().mapToDouble(Column::width).sum();
            if (background != null) {
                content.setNonStrokingColor(background);
                content.addRect(MARGIN, bottom, tableWidth, height);
                content.fill();
            }
            content.setStrokingColor(new Color(0xCC, 0xCC, 0xCC));
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN, bottom);
            content.lineTo(MARGIN + tableWidth, bottom);
            content.stroke();
            for (int index = 0; index < columns.size(); index++) {
                var column = columns.get(index);
                float lineY = y - PADDING - BODY;
                for (var cellLine : cells.get(index)) {
                    var shown = safe(cellLine, font);
                    float offset = column.right() ? column.width() - 2 * PADDING - width(shown, font, BODY) : 0;
                    text(content, font, BODY, x + PADDING + offset, lineY, shown, Color.BLACK);
                    lineY -= LEADING;
                }
                x += column.width();
            }
            y = bottom;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void text(PDPageContentStream stream, PDFont font, float size, float x, float baseline, String text, Color color)
            throws IOException {
        if (text.isEmpty()) return;
        stream.beginText();
        stream.setNonStrokingColor(color);
        stream.setFont(font, size);
        stream.newLineAtOffset(x, baseline);
        stream.showText(text);
        stream.endText();
    }

    private float width(String text, PDFont font, float size) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Line breaks and tabs become breaks and spaces; characters the font cannot draw become '?'. */
    private String safe(String text, PDFont font) {
        var cache = supported.computeIfAbsent(font, ignored -> new HashMap<>());
        var out = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (codePoint == '\t' || codePoint == '\r' || codePoint == '\n') {
                out.append(' ');
            } else if (Character.isISOControl(codePoint)) {
                // dropped
            } else if (cache.computeIfAbsent(codePoint, cp -> canDraw(font, cp))) {
                out.appendCodePoint(codePoint);
            } else {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static boolean canDraw(PDFont font, int codePoint) {
        try {
            font.encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IllegalArgumentException | IOException exception) {
            return false;
        }
    }

    /** Wraps by words to the width; explicit line breaks are kept and over-long words are split. */
    private List<String> wrap(String text, PDFont font, float size, float maxWidth) {
        var lines = new ArrayList<String>();
        for (var paragraph : (text == null ? "" : text).split("\\R", -1)) {
            var current = new StringBuilder();
            for (var word : paragraph.split(" ")) {
                var candidate = current.isEmpty() ? word : current + " " + word;
                if (width(safe(candidate, font), font, size) <= maxWidth) {
                    current = new StringBuilder(candidate);
                    continue;
                }
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder();
                var rest = word;
                while (width(safe(rest, font), font, size) > maxWidth && rest.length() > 1) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && width(safe(rest.substring(0, cut), font), font, size) > maxWidth) cut--;
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                current.append(rest);
            }
            lines.add(current.toString());
        }
        return lines;
    }
}
