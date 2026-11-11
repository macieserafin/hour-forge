package pl.macieserafin.hourforge.mail.unit;

import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.mail.Email;
import pl.macieserafin.hourforge.mail.MailContent;
import pl.macieserafin.hourforge.mail.MailContent.Detail;
import pl.macieserafin.hourforge.mail.Mailer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every message is the same content twice: as text for any client and as HTML in the HourForge look. */
class MailerTest {
    private final List<Email> sent = new ArrayList<>();
    private final Mailer mailer = new Mailer(sent::add, "https://hourforge.example/");

    private MailContent reset() {
        return MailContent.builder("Reset hasła w HourForge")
                .title("Ustaw nowe hasło")
                .preheader("Link jest ważny przez 1 godzinę.")
                .greeting("Ala")
                .paragraph("Otrzymaliśmy prośbę.")
                .action("Ustaw nowe hasło, otwierając link:", "Ustaw nowe hasło", mailer.url("/reset-password/abc"))
                .details(List.of(new Detail("Urządzenie", "Chrome · Android")))
                .note("Jeśli to nie Ty, zignoruj tę wiadomość.")
                .build();
    }

    @Test
    void theTextVersionKeepsEveryLinkOnItsOwnLineAndDetailsAsLabelAndValue() {
        mailer.send("ala@example.test", reset());

        var text = sent.getFirst().text();
        assertThat(text).startsWith("Cześć Ala,\n\nOtrzymaliśmy prośbę.\n\n");
        assertThat(text).contains("Ustaw nowe hasło, otwierając link:\nhttps://hourforge.example/reset-password/abc\n");
        assertThat(text).contains("Urządzenie: Chrome · Android\n");
        assertThat(text).contains("-- \nWiadomość wysłana automatycznie przez HourForge (hourforge.example).");
        assertThat(text).doesNotContain("<");
    }

    @Test
    void theHtmlVersionSaysTheSameWithOneButtonAndTheAddressWrittenOut() {
        mailer.send("ala@example.test", reset());

        var email = sent.getFirst();
        var html = email.html();
        assertThat(html).startsWith("<!DOCTYPE html><html lang=\"pl\">");
        assertThat(html).contains("<title>Reset hasła w HourForge</title>", ">Ustaw nowe hasło</h1>", "Cześć Ala,", "Otrzymaliśmy prośbę.",
                "Link jest ważny przez 1 godzinę.", "Chrome · Android", "Jeśli to nie Ty, zignoruj tę wiadomość.");
        // The button and the copyable address lead to the same place; the visible address is the real one.
        assertThat(html).containsSubsequence("href=\"https://hourforge.example/reset-password/abc\"", ">Ustaw nowe hasło</a>",
                "href=\"https://hourforge.example/reset-password/abc\"", ">https://hourforge.example/reset-password/abc</a>");
        // No images to block and no scripts; the name of the product is text.
        assertThat(html).doesNotContain("<img", "<script", "http://");
        assertThat(email.category()).isEqualTo(Email.Category.TRANSACTIONAL);
        assertThat(email.oneClickUnsubscribe()).isNull();
    }

    @Test
    void namesAndOtherValuesCannotInjectMarkup() {
        mailer.send("x@example.test", MailContent.builder("Zespół <b>Zły</b>")
                .greeting("<script>alert(1)</script>")
                .paragraph("Zespół „<a href=\"https://evil.example\">kliknij</a>”")
                .details(List.of(new Detail("Projekt", "\"><img src=x onerror=alert(1)>")))
                .action("Otwórz:", "Otwórz", "https://hourforge.example/x?a=1&b=\"2\"")
                .build());

        var html = sent.getFirst().html();
        assertThat(html).doesNotContain("<script>alert", "<a href=\"https://evil.example\"", "<img", "<b>Zły</b>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "href=\"https://hourforge.example/x?a=1&amp;b=&quot;2&quot;\"");
    }

    @Test
    void anOptionalMessageSaysHowToStopItAndCarriesTheOneClickAddress() {
        var unsubscribe = new Mailer.Unsubscribe("https://hourforge.example/unsubscribe/t?kind=TEAM_EVENTS",
                "https://hourforge.example/api/notifications/unsubscribe/one-click?token=t&kind=TEAM_EVENTS",
                "https://hourforge.example/account#notifications");
        mailer.notify("ala@example.test", MailContent.builder("Zespół został zarchiwizowany").paragraph("Treść.").build(), unsubscribe);

        var email = sent.getFirst();
        assertThat(email.category()).isEqualTo(Email.Category.NOTIFICATION);
        assertThat(email.oneClickUnsubscribe()).isEqualTo(unsubscribe.oneClickUrl());
        assertThat(email.text()).contains("Wypisz się jednym kliknięciem:\n" + unsubscribe.pageUrl() + "\n",
                "Wszystkie powiadomienia ustawisz w koncie: " + unsubscribe.settingsUrl());
        assertThat(email.html()).contains("href=\"" + unsubscribe.pageUrl() + "\"", "href=\"https://hourforge.example/account#notifications\"");
    }

    @Test
    void aMessageHasAtMostOneMainActionAndANotificationAlwaysAWayOut() {
        var builder = MailContent.builder("S").action("A", "A", "https://a");
        assertThatThrownBy(() -> builder.action("B", "B", "https://b")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Email("a@b", "S", "t", "h", Email.Category.NOTIFICATION, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutANameThereIsNoGreetingAndTheTitleDefaultsToTheSubject() {
        mailer.send("x@example.test", MailContent.builder("Konto zostało usunięte").greeting(null).paragraph("Treść.").build());

        assertThat(sent.getFirst().text()).startsWith("Treść.");
        assertThat(sent.getFirst().html()).contains(">Konto zostało usunięte</h1>").doesNotContain("Cześć");
    }
}
