package pl.macieserafin.hourforge.mail.unit;

import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.mail.AllowedRecipientsTransport;
import pl.macieserafin.hourforge.mail.AllowedRecipientsTransport.RecipientNotAllowedException;
import pl.macieserafin.hourforge.mail.Email;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testing a real mail server locally: only the listed addresses get mail, the rest of the outbox never leaves. */
class AllowedRecipientsTransportTest {
    private final List<String> sent = new ArrayList<>();
    private final AllowedRecipientsTransport transport =
            new AllowedRecipientsTransport(email -> sent.add(email.to()), " me@example.test, Other@Example.test ,");

    @Test
    void mailForAListedAddressGoesOutWhateverTheLetterCase() throws Exception {
        transport.deliver(email("ME@example.test"));
        transport.deliver(email("other@example.test"));

        assertThat(sent).containsExactly("ME@example.test", "other@example.test");
    }

    @Test
    void mailForAnyoneElseIsRefusedWithoutReachingTheServerAndWithoutNamingTheAddressInTheError() {
        assertThatThrownBy(() -> transport.deliver(email("stranger@example.test")))
                .isInstanceOf(RecipientNotAllowedException.class)
                .hasMessageNotContaining("stranger");
        assertThatThrownBy(() -> transport.deliver(email("me@example.test.evil.test")))
                .isInstanceOf(RecipientNotAllowedException.class);

        assertThat(sent).isEmpty();
    }

    @Test
    void anEmptyListIsAMistakeRatherThanAllowingEverybody() {
        assertThatThrownBy(() -> new AllowedRecipientsTransport(email -> { }, " , "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Email email(String to) {
        return new Email(to, "Subject", "text", "<p>html</p>", Email.Category.TRANSACTIONAL, null, null);
    }
}
