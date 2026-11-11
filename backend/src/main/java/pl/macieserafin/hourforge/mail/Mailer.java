package pl.macieserafin.hourforge.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;

/**
 * Turns {@link MailContent} into an e-mail in the HourForge look, as text and HTML, and hands it to the sender. All
 * messages share one layout: name of the product, a card with the content, at most one main action, and a footer that
 * says why the message came and, for optional ones, how to stop them.
 */
@Component
public class Mailer {
    /** Where an optional message is switched off: the page from the footer, the address for one click and the settings. */
    public record Unsubscribe(String pageUrl, String oneClickUrl, String settingsUrl) {
    }

    private static final String FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
    // "Kuźnia" tokens of the light theme (frontend/src/ui/tokens.css); the dark ones follow in the style block.
    private static final String BG = "#F6F4F1";
    private static final String SURFACE = "#FFFFFF";
    private static final String SURFACE_2 = "#F1EDE8";
    private static final String LINE = "#E4DED6";
    private static final String TEXT = "#1C1917";
    private static final String MUTED = "#5F564D";
    private static final String PRIMARY = "#5B21B6";
    private static final String NAV = "#1B1530";

    private final EmailSender sender;
    private final String frontendUrl;
    private final String host;

    public Mailer(EmailSender sender, @Value("${app.frontend-url}") String frontendUrl) {
        this.sender = sender;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        var parsed = URI.create(this.frontendUrl).getHost();
        this.host = parsed == null ? this.frontendUrl : parsed;
    }

    /** An address in the app, e.g. {@code url("/login")}. */
    public String url(String path) {
        return frontendUrl + path;
    }

    public void send(String to, MailContent content) {
        sender.send(compose(to, content, null));
    }

    /** An optional message: it says how to stop it and carries the one-click unsubscribe header. */
    public void notify(String to, MailContent content, Unsubscribe unsubscribe) {
        sender.send(compose(to, content, unsubscribe));
    }

    public void notify(String to, MailContent content, Unsubscribe unsubscribe, Email.Attachment attachment) {
        sender.send(compose(to, content, unsubscribe).withAttachment(attachment));
    }

    public Email compose(String to, MailContent content, Unsubscribe unsubscribe) {
        var category = unsubscribe == null ? Email.Category.TRANSACTIONAL : Email.Category.NOTIFICATION;
        return new Email(to, content.subject(), text(content, unsubscribe), html(content, unsubscribe), category,
                unsubscribe == null ? null : unsubscribe.oneClickUrl(), null);
    }

    // ---- text ----

    private String text(MailContent content, Unsubscribe unsubscribe) {
        var out = new StringBuilder();
        if (content.greeting() != null) out.append(content.greeting()).append("\n\n");
        for (var block : content.blocks()) {
            switch (block) {
                case MailContent.Paragraph paragraph -> out.append(paragraph.text()).append("\n\n");
                case MailContent.Action action -> out.append(action.intro()).append('\n').append(action.url()).append("\n\n");
                case MailContent.Link link -> out.append(link.intro()).append('\n').append(link.url()).append("\n\n");
                case MailContent.Details details -> {
                    details.rows().forEach(row -> out.append(row.label()).append(": ").append(row.value()).append('\n'));
                    out.append('\n');
                }
                case MailContent.Note note -> out.append(note.text()).append("\n\n");
            }
        }
        out.append("-- \n");
        if (unsubscribe != null) {
            out.append("Nie chcesz takich wiadomości? Wypisz się jednym kliknięciem:\n").append(unsubscribe.pageUrl()).append('\n')
                    .append("Wszystkie powiadomienia ustawisz w koncie: ").append(unsubscribe.settingsUrl()).append('\n');
        } else {
            out.append("Wiadomość wysłana automatycznie przez HourForge (").append(host).append("). Nie odpowiadaj na nią.\n");
        }
        return out.toString();
    }

    // ---- HTML ----

    private String html(MailContent content, Unsubscribe unsubscribe) {
        var body = new StringBuilder();
        body.append("<h1 class=\"hf-text\" style=\"margin:0 0 20px;font-size:22px;line-height:30px;font-weight:700;color:")
                .append(TEXT).append(";\">").append(esc(content.title())).append("</h1>");
        if (content.greeting() != null) body.append(paragraph(content.greeting()));
        for (var block : content.blocks()) {
            switch (block) {
                case MailContent.Paragraph paragraph -> body.append(paragraph(paragraph.text()));
                case MailContent.Action action -> body.append(paragraph(action.intro())).append(button(action.label(), action.url()))
                        .append("<p class=\"hf-muted\" style=\"margin:0 0 16px;font-size:13px;line-height:20px;color:").append(MUTED)
                        .append(";\">Jeśli przycisk nie działa, skopiuj ten adres do przeglądarki:<br>")
                        .append(anchor(action.url(), action.url(), "word-break:break-all;")).append("</p>");
                case MailContent.Link link -> body.append("<p class=\"hf-text\" style=\"margin:0 0 16px;color:").append(TEXT).append(";\">")
                        .append(esc(link.intro())).append("<br>").append(anchor(link.url(), link.url(), "word-break:break-all;")).append("</p>");
                case MailContent.Details details -> body.append(details(details));
                case MailContent.Note note -> body.append("<p class=\"hf-muted\" style=\"margin:8px 0 0;font-size:14px;line-height:21px;color:")
                        .append(MUTED).append(";\">").append(esc(note.text())).append("</p>");
            }
        }

        var footer = new StringBuilder();
        if (unsubscribe != null) {
            footer.append("Nie chcesz takich wiadomości? ").append(anchor(unsubscribe.pageUrl(), "Wypisz się jednym kliknięciem", ""))
                    .append(". Wszystkie powiadomienia ustawisz w ").append(anchor(unsubscribe.settingsUrl(), "ustawieniach konta", "")).append('.');
        } else {
            footer.append("Wiadomość wysłana automatycznie przez HourForge (").append(esc(host)).append("). Nie odpowiadaj na nią.");
        }

        var out = new StringBuilder(4096);
        out.append("<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light dark\"><meta name=\"supported-color-schemes\" content=\"light dark\">")
                .append("<title>").append(esc(content.subject())).append("</title><style>")
                .append("@media (prefers-color-scheme: dark){")
                .append(".hf-bg{background:#121016 !important}.hf-card{background:#1C1922 !important;border-color:#322D3B !important}")
                .append(".hf-text{color:#F2EFF7 !important}.hf-muted{color:#B4ACC2 !important}.hf-box{background:#25212D !important}")
                .append(".hf-link{color:#C4B5FD !important}.hf-brand{color:#F2EFF7 !important}}")
                .append("@media (max-width:600px){.hf-pad{padding:24px 20px !important}}")
                .append("</style></head><body class=\"hf-bg\" style=\"margin:0;padding:0;background:").append(BG).append(";\">");
        if (content.preheader() != null) {
            // Shown in the inbox list next to the subject; the padding keeps the client from filling it with the body.
            out.append("<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;\">").append(esc(content.preheader()))
                    .append("&#8199;&#847;".repeat(40)).append("</div>");
        }
        out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"hf-bg\" style=\"background:")
                .append(BG).append(";\"><tr><td align=\"center\" style=\"padding:32px 12px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:560px;\">")
                // Name of the product: the triad of sparks from the logo and the word, as text so it never needs an image.
                .append("<tr><td style=\"padding:0 4px 16px;font-family:").append(FONT).append(";\">")
                .append("<a href=\"").append(esc(frontendUrl)).append("\" style=\"text-decoration:none;\">")
                .append(spark("#F59E0B")).append(spark("#FBBF24")).append(spark("#2DD4BF"))
                .append("<span class=\"hf-brand\" style=\"font-size:18px;font-weight:700;letter-spacing:-0.2px;color:").append(NAV)
                .append(";vertical-align:middle;padding-left:6px;\">HourForge</span></a></td></tr>")
                .append("<tr><td class=\"hf-card\" style=\"background:").append(SURFACE).append(";border:1px solid ").append(LINE)
                .append(";border-top:4px solid ").append(PRIMARY).append(";border-radius:12px;\">")
                .append("<div class=\"hf-pad\" style=\"padding:32px 36px;font-family:").append(FONT)
                .append(";font-size:16px;line-height:24px;color:").append(TEXT).append(";\">").append(body).append("</div></td></tr>")
                .append("<tr><td class=\"hf-muted\" style=\"padding:20px 8px 0;font-family:").append(FONT)
                .append(";font-size:12px;line-height:18px;color:").append(MUTED).append(";text-align:center;\">").append(footer)
                .append("</td></tr></table></td></tr></table></body></html>");
        return out.toString();
    }

    private static String spark(String colour) {
        return "<span style=\"display:inline-block;width:8px;height:8px;margin-right:3px;border-radius:2px;vertical-align:middle;background:"
                + colour + ";\"></span>";
    }

    private static String paragraph(String text) {
        return "<p class=\"hf-text\" style=\"margin:0 0 16px;color:" + TEXT + ";\">" + esc(text) + "</p>";
    }

    private static String button(String label, String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:8px 0 20px;\"><tr>"
                + "<td bgcolor=\"" + PRIMARY + "\" style=\"border-radius:8px;background:" + PRIMARY + ";\">"
                + "<a href=\"" + esc(url) + "\" style=\"display:inline-block;padding:13px 26px;font-family:" + FONT
                + ";font-size:16px;font-weight:600;line-height:20px;color:#FFFFFF;text-decoration:none;border-radius:8px;\">"
                + esc(label) + "</a></td></tr></table>";
    }

    private static String anchor(String url, String label, String extraStyle) {
        return "<a class=\"hf-link\" href=\"" + esc(url) + "\" style=\"color:" + PRIMARY + ";text-decoration:underline;" + extraStyle + "\">"
                + esc(label) + "</a>";
    }

    private static String details(MailContent.Details details) {
        var rows = new StringBuilder();
        for (var row : details.rows()) {
            rows.append("<tr><td class=\"hf-muted\" style=\"padding:6px 16px 6px 0;font-size:14px;line-height:20px;color:").append(MUTED)
                    .append(";white-space:nowrap;vertical-align:top;\">").append(esc(row.label())).append("</td>")
                    .append("<td class=\"hf-text\" style=\"padding:6px 0;font-size:14px;line-height:20px;color:").append(TEXT)
                    .append(";font-weight:600;\">").append(esc(row.value())).append("</td></tr>");
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"hf-box\" "
                + "style=\"margin:4px 0 20px;background:" + SURFACE_2 + ";border-radius:8px;\"><tr><td style=\"padding:10px 16px;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">" + rows + "</table></td></tr></table>";
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
