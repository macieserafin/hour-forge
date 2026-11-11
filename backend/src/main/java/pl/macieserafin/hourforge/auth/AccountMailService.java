package pl.macieserafin.hourforge.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.mail.MailContent;
import pl.macieserafin.hourforge.mail.MailContent.Detail;
import pl.macieserafin.hourforge.mail.Mailer;
import pl.macieserafin.hourforge.session.DeviceInfo;
import pl.macieserafin.hourforge.user.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Builds and sends the account e-mails: one-time links and security notices, always sent whatever the settings say. */
@Service
public class AccountMailService {
    private static final DateTimeFormatter SIGN_IN_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter REMOVAL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);
    private static final String DELETED_NAME = "Zapisane godziny pracy zostaną w raportach zespołów pod nazwą „Użytkownik usunięty”.";

    private final EmailTokenService tokens;
    private final Mailer mailer;

    public AccountMailService(EmailTokenService tokens, Mailer mailer) {
        this.tokens = tokens;
        this.mailer = mailer;
    }

    @Transactional
    public void sendPasswordReset(User user) {
        var token = tokens.issue(user, EmailTokenPurpose.RESET_PASSWORD, null);
        mailer.send(user.getEmail(), MailContent.builder("Reset hasła w HourForge")
                .title("Ustaw nowe hasło")
                .preheader("Link jest ważny przez 1 godzinę.")
                .greeting(user.getDisplayName())
                .paragraph("Otrzymaliśmy prośbę o ustawienie nowego hasła do Twojego konta.")
                .action("Ustaw nowe hasło, otwierając link (ważny 1 godzinę):", "Ustaw nowe hasło", mailer.url("/reset-password/" + token))
                .note("Jeśli to nie Ty o to prosiłeś, zignoruj tę wiadomość. Hasło się nie zmieni.")
                .build());
    }

    /** Sent to the owner of an address somebody tried to register again; it reveals nothing to the requester. */
    public void sendRegistrationAttempt(User existing) {
        mailer.send(existing.getEmail(), MailContent.builder("Ktoś próbował założyć konto na Twój adres")
                .title("Masz już konto w HourForge")
                .preheader("Ktoś próbował zarejestrować nowe konto na ten adres e-mail.")
                .greeting(existing.getDisplayName())
                .paragraph("Ktoś próbował zarejestrować w HourForge konto na ten adres e-mail, ale konto już istnieje.")
                .action("Jeśli to Ty, zaloguj się lub ustaw nowe hasło:", "Ustaw nowe hasło", mailer.url("/forgot-password"))
                .note("Jeśli to nie Ty, nic nie musisz robić. Twoje konto pozostaje bez zmian.")
                .build());
    }

    /**
     * Sent to the owner of an address when somebody signed in with a Google account that has the same address. Nothing
     * was linked or created (A13-D1); only the person who proved that Google account learns that the address is taken.
     */
    public void sendGoogleSignInAttempt(User existing) {
        mailer.send(existing.getEmail(), MailContent.builder("Ktoś próbował zalogować się przez Google na Twój adres")
                .title("Próba logowania przez Google")
                .preheader("Niczego nie połączyliśmy i niczego nie zmieniliśmy.")
                .greeting(existing.getDisplayName())
                .paragraph("Ktoś próbował zalogować się do HourForge kontem Google z adresem e-mail Twojego konta. "
                        + "Nie połączyliśmy tego konta Google z Twoim kontem i niczego nie zmieniliśmy.")
                .action("Jeśli to Ty, zaloguj się tak jak zwykle, a potem połącz Google w ustawieniach konta:", "Otwórz ustawienia konta",
                        mailer.url("/account#google"))
                .link("Jeśli nie pamiętasz hasła, ustaw nowe:", mailer.url("/forgot-password"))
                .note("Jeśli to nie Ty, nic nie musisz robić.")
                .build());
    }

    /** The link that confirms the removal of an account without a password (A13-D8). */
    public void sendAccountDeletionLink(User user, String token) {
        mailer.send(user.getEmail(), MailContent.builder("Potwierdź usunięcie konta w HourForge")
                .title("Potwierdź usunięcie konta")
                .preheader("Link jest ważny przez 24 godziny. Tej operacji nie można cofnąć.")
                .greeting(user.getDisplayName())
                .paragraph("Ktoś poprosił o usunięcie Twojego konta w HourForge.")
                .action("Aby je usunąć, otwórz link (ważny 24 godziny):", "Usuń konto", mailer.url("/confirm-account-deletion/" + token))
                .paragraph("Usuniemy Twoje imię, adres e-mail i połączone konto Google. " + DELETED_NAME)
                .note("Jeśli to nie Ty, zignoruj tę wiadomość. Konto zostanie bez zmian.")
                .build());
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendGoogleLinked(User user, String googleEmail) {
        mailer.send(user.getEmail(), MailContent.builder("Połączono konto Google z HourForge")
                .title("Połączono konto Google")
                .preheader("Od teraz możesz logować się także przez Google.")
                .greeting(user.getDisplayName())
                .paragraph("Do Twojego konta połączono konto Google" + (googleEmail == null ? "" : " " + googleEmail)
                        + ". Od teraz możesz logować się także przez Google.")
                .action("Jeśli to nie Ty, od razu odłącz Google i zmień hasło:", "Sprawdź ustawienia konta", mailer.url("/account#google"))
                .build());
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendGoogleUnlinked(User user, String googleEmail) {
        mailer.send(user.getEmail(), MailContent.builder("Odłączono konto Google od HourForge")
                .title("Odłączono konto Google")
                .preheader("Logowanie przez Google już nie działa.")
                .greeting(user.getDisplayName())
                .paragraph("Od Twojego konta odłączono konto Google" + (googleEmail == null ? "" : " " + googleEmail)
                        + ". Logowanie przez nie już nie działa, a pozostałe urządzenia zostały wylogowane.")
                .action("Jeśli to nie Ty, od razu zmień hasło:", "Sprawdź urządzenia i hasło", mailer.url("/account#sessions"))
                .build());
    }

    @Transactional
    public void sendEmailChange(User user, String newEmail) {
        var token = tokens.issue(user, EmailTokenPurpose.CHANGE_EMAIL, newEmail);
        mailer.send(newEmail, MailContent.builder("Potwierdź nowy adres e-mail w HourForge")
                .title("Potwierdź nowy adres e-mail")
                .preheader("Link jest ważny przez 24 godziny.")
                .greeting(user.getDisplayName())
                .paragraph("Potwierdź zmianę adresu e-mail konta w HourForge na ten adres.")
                .action("Potwierdź zmianę, otwierając link (ważny 24 godziny):", "Potwierdź adres", mailer.url("/confirm-email-change/" + token))
                .note("Jeśli to nie Ty o to prosiłeś, zignoruj tę wiadomość.")
                .build());
    }

    public void sendAccountDeleted(String oldEmail) {
        mailer.send(oldEmail, MailContent.builder("Konto w HourForge zostało usunięte")
                .title("Konto zostało usunięte")
                .preheader("Twoje imię, adres e-mail i hasło zostały usunięte.")
                .paragraph("Twoje konto w HourForge zostało usunięte. Imię, adres e-mail i hasło zostały usunięte. " + DELETED_NAME
                        + " Trwający timer został odrzucony bez zapisania czasu.")
                .note("Jeśli to nie Ty, skontaktuj się z administratorem serwisu.")
                .build());
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendNewDeviceSignIn(User user, DeviceInfo device, Instant at) {
        var details = new ArrayList<Detail>(List.of(
                new Detail("Urządzenie", (device.browser() == null ? "nieznana przeglądarka" : device.browser())
                        + (device.system() == null ? "" : " · " + device.system())),
                new Detail("Czas", SIGN_IN_TIME.format(at))));
        if (device.address() != null) details.add(new Detail("Adres", device.address()));
        if (device.location() != null) details.add(new Detail("Miejsce (przybliżone)", device.location()));
        mailer.send(user.getEmail(), MailContent.builder("Nowe logowanie na Twoje konto HourForge")
                .title("Nowe logowanie na Twoje konto")
                .preheader("Jeśli to Ty, nic nie musisz robić.")
                .greeting(user.getDisplayName())
                .paragraph("Na Twoje konto zalogowano się z nowego urządzenia:")
                .details(details)
                .paragraph("Jeśli to Ty, nic nie musisz robić.")
                .action("Jeśli to nie Ty, od razu zmień hasło i wyloguj nieznane urządzenia:", "Sprawdź urządzenia", mailer.url("/account#sessions"))
                .build());
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendTwoFactorChanged(User user, boolean enabled) {
        mailer.send(user.getEmail(), MailContent.builder(enabled ? "Włączono logowanie dwuetapowe w HourForge" : "Wyłączono logowanie dwuetapowe w HourForge")
                .title(enabled ? "Włączono logowanie dwuetapowe" : "Wyłączono logowanie dwuetapowe")
                .preheader(enabled ? "Przy logowaniu podasz też kod z aplikacji." : "Do logowania wystarczy teraz samo hasło.")
                .greeting(user.getDisplayName())
                .paragraph(enabled ? "Na Twoim koncie włączono logowanie dwuetapowe. Od teraz przy logowaniu podasz też kod z aplikacji "
                        + "uwierzytelniającej. Zachowaj kody zapasowe w bezpiecznym miejscu."
                        : "Na Twoim koncie wyłączono logowanie dwuetapowe. Do logowania wystarczy teraz samo hasło.")
                .action("Jeśli to nie Ty, od razu zmień hasło:", "Sprawdź bezpieczeństwo konta", mailer.url("/account#sessions"))
                .build());
    }

    /** Required notice before an unused account is removed; always sent, whatever the notification settings say. */
    public void sendInactivityWarning(User user, Instant removalOn) {
        var date = REMOVAL_DATE.format(removalOn);
        mailer.send(user.getEmail(), MailContent.builder("Twoje konto w HourForge zostanie usunięte " + date)
                .title("Konto zostanie usunięte " + date)
                .preheader("Zaloguj się przed tą datą, aby je zachować.")
                .greeting(user.getDisplayName())
                .paragraph("Od dłuższego czasu nie korzystasz z konta w HourForge. Konta nieużywane przez 24 miesiące usuwamy, "
                        + "aby nie przechowywać danych bez potrzeby.")
                .action("Jeśli chcesz zachować konto, po prostu zaloguj się przed " + date + ":", "Zaloguj się", mailer.url("/login"))
                .note("Jeśli nic nie zrobisz, usuniemy Twoje imię, adres e-mail i hasło. " + DELETED_NAME)
                .build());
    }

    /** Tells the owners' fellow admins that an unused owner account is kept because of their team. */
    public void sendInactiveOwner(User admin, String teamName, String ownerName) {
        mailer.send(admin.getEmail(), MailContent.builder("Właściciel zespołu " + teamName + " od dawna nie korzysta z HourForge")
                .title("Właściciel zespołu od dawna nie korzysta z HourForge")
                .preheader("Konto nie zostanie usunięte, bo zespół jest nadal używany.")
                .greeting(admin.getDisplayName())
                .paragraph(ownerName + ", właściciel zespołu „" + teamName + "”, nie korzysta z HourForge od ponad 24 miesięcy. "
                        + "Konto nie zostanie usunięte, bo zespół jest nadal używany.")
                .paragraph("Poproś właściciela o przekazanie własności zespołu albo skontaktuj się z operatorem serwisu.")
                .build());
    }

    public void sendEmailChanged(String oldEmail, String newEmail) {
        mailer.send(oldEmail, MailContent.builder("Adres e-mail konta w HourForge został zmieniony")
                .title("Zmieniono adres e-mail konta")
                .preheader("Od teraz logujesz się nowym adresem.")
                .paragraph("Adres e-mail Twojego konta w HourForge zmieniono na " + newEmail + ". Od teraz logujesz się nowym adresem.")
                .note("Jeśli to nie Ty, skontaktuj się z administratorem serwisu.")
                .build());
    }
}
