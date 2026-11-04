package pl.macieserafin.hourforge.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.session.DeviceInfo;
import pl.macieserafin.hourforge.user.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Builds and sends the account e-mails that carry one-time links. */
@Service
public class AccountMailService {
    private static final DateTimeFormatter SIGN_IN_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter REMOVAL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);

    private final EmailTokenService tokens;
    private final EmailSender sender;
    private final String frontendUrl;

    public AccountMailService(EmailTokenService tokens, EmailSender sender, @Value("${app.frontend-url}") String frontendUrl) {
        this.tokens = tokens;
        this.sender = sender;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Transactional
    public void sendPasswordReset(User user) {
        var token = tokens.issue(user, EmailTokenPurpose.RESET_PASSWORD, null);
        sender.send(user.getEmail(), "Reset hasła w HourForge",
                "Cześć " + user.getDisplayName() + ",\n\nustaw nowe hasło, otwierając link (ważny 1 godzinę):\n"
                        + frontendUrl + "/reset-password/" + token + "\n\nJeśli to nie Ty o to prosiłeś, zignoruj tę wiadomość. Hasło się nie zmieni.\n");
    }

    /** Sent to the owner of an address somebody tried to register again; it reveals nothing to the requester. */
    public void sendRegistrationAttempt(User existing) {
        sender.send(existing.getEmail(), "Ktoś próbował założyć konto na Twój adres",
                "Cześć " + existing.getDisplayName() + ",\n\nktoś próbował zarejestrować w HourForge konto na ten adres e-mail, "
                        + "ale konto już istnieje. Jeśli to Ty, zaloguj się lub ustaw nowe hasło:\n"
                        + frontendUrl + "/forgot-password\n\nJeśli to nie Ty, nic nie musisz robić.\n");
    }

    /**
     * Sent to the owner of an address when somebody signed in with a Google account that has the same address. Nothing
     * was linked or created (A13-D1); only the person who proved that Google account learns that the address is taken.
     */
    public void sendGoogleSignInAttempt(User existing) {
        sender.send(existing.getEmail(), "Ktoś próbował zalogować się przez Google na Twój adres",
                "Cześć " + existing.getDisplayName() + ",\n\nktoś próbował zalogować się do HourForge kontem Google z adresem e-mail "
                        + "Twojego konta. Nie połączyliśmy tego konta Google z Twoim kontem i niczego nie zmieniliśmy.\n\n"
                        + "Jeśli to Ty, zaloguj się tak jak zwykle, a potem połącz Google w ustawieniach konta:\n"
                        + frontendUrl + "/account#google\n\nJeśli nie pamiętasz hasła, ustaw nowe:\n" + frontendUrl + "/forgot-password\n\n"
                        + "Jeśli to nie Ty, nic nie musisz robić.\n");
    }

    /** The link that confirms the removal of an account without a password (A13-D8). */
    public void sendAccountDeletionLink(User user, String token) {
        sender.send(user.getEmail(), "Potwierdź usunięcie konta w HourForge",
                "Cześć " + user.getDisplayName() + ",\n\nktoś poprosił o usunięcie Twojego konta w HourForge. Aby je usunąć, otwórz link "
                        + "(ważny 24 godziny):\n" + frontendUrl + "/confirm-account-deletion/" + token + "\n\n"
                        + "Usuniemy Twoje imię, adres e-mail i połączone konto Google. Zapisane godziny pracy zostaną w raportach zespołów "
                        + "pod nazwą „Użytkownik usunięty”.\n\nJeśli to nie Ty, zignoruj tę wiadomość. Konto zostanie bez zmian.\n");
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendGoogleLinked(User user, String googleEmail) {
        sender.send(user.getEmail(), "Połączono konto Google z HourForge",
                "Cześć " + user.getDisplayName() + ",\n\ndo Twojego konta połączono konto Google"
                        + (googleEmail == null ? "" : " " + googleEmail) + ". Od teraz możesz logować się także przez Google.\n\n"
                        + "Jeśli to nie Ty, od razu odłącz Google i zmień hasło:\n" + frontendUrl + "/account#google\n");
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendGoogleUnlinked(User user, String googleEmail) {
        sender.send(user.getEmail(), "Odłączono konto Google od HourForge",
                "Cześć " + user.getDisplayName() + ",\n\nod Twojego konta odłączono konto Google"
                        + (googleEmail == null ? "" : " " + googleEmail) + ". Logowanie przez nie już nie działa, "
                        + "a pozostałe urządzenia zostały wylogowane.\n\n"
                        + "Jeśli to nie Ty, od razu zmień hasło:\n" + frontendUrl + "/account#sessions\n");
    }

    @Transactional
    public void sendEmailChange(User user, String newEmail) {
        var token = tokens.issue(user, EmailTokenPurpose.CHANGE_EMAIL, newEmail);
        sender.send(newEmail, "Potwierdź nowy adres e-mail w HourForge",
                "Cześć " + user.getDisplayName() + ",\n\npotwierdź zmianę adresu e-mail konta, otwierając link (ważny 24 godziny):\n"
                        + frontendUrl + "/confirm-email-change/" + token + "\n\nJeśli to nie Ty o to prosiłeś, zignoruj tę wiadomość.\n");
    }

    public void sendAccountDeleted(String oldEmail) {
        sender.send(oldEmail, "Konto w HourForge zostało usunięte",
                "Twoje konto w HourForge zostało usunięte. Imię, adres e-mail i hasło zostały usunięte; zapisane godziny pracy "
                        + "pozostają w raportach zespołów pod nazwą „Użytkownik usunięty”. Trwający timer został odrzucony bez zapisania czasu.\n\n"
                        + "Jeśli to nie Ty, skontaktuj się z administratorem serwisu.\n");
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendNewDeviceSignIn(User user, DeviceInfo device, Instant at) {
        var details = new StringBuilder()
                .append("Urządzenie: ").append(device.browser() == null ? "nieznana przeglądarka" : device.browser())
                .append(device.system() == null ? "" : " · " + device.system()).append('\n')
                .append("Czas: ").append(SIGN_IN_TIME.format(at)).append('\n');
        if (device.address() != null) details.append("Adres: ").append(device.address()).append('\n');
        if (device.location() != null) details.append("Miejsce (przybliżone): ").append(device.location()).append('\n');
        sender.send(user.getEmail(), "Nowe logowanie na Twoje konto HourForge",
                "Cześć " + user.getDisplayName() + ",\n\nna Twoje konto zalogowano się z nowego urządzenia:\n\n" + details
                        + "\nJeśli to Ty, nic nie musisz robić.\n"
                        + "Jeśli to nie Ty, od razu zmień hasło i wyloguj nieznane urządzenia:\n"
                        + frontendUrl + "/account#sessions\n");
    }

    /** A security message: always sent, whatever the notification settings say. */
    public void sendTwoFactorChanged(User user, boolean enabled) {
        sender.send(user.getEmail(), enabled ? "Włączono logowanie dwuetapowe w HourForge" : "Wyłączono logowanie dwuetapowe w HourForge",
                "Cześć " + user.getDisplayName() + ",\n\n"
                        + (enabled ? "na Twoim koncie włączono logowanie dwuetapowe. Od teraz przy logowaniu podasz też kod z aplikacji "
                                + "uwierzytelniającej. Zachowaj kody zapasowe w bezpiecznym miejscu.\n"
                                : "na Twoim koncie wyłączono logowanie dwuetapowe. Do logowania wystarczy teraz samo hasło.\n")
                        + "\nJeśli to nie Ty, od razu zmień hasło:\n" + frontendUrl + "/account#sessions\n");
    }

    /** Required notice before an unused account is removed; always sent, whatever the notification settings say. */
    public void sendInactivityWarning(User user, Instant removalOn) {
        sender.send(user.getEmail(), "Twoje konto w HourForge zostanie usunięte " + REMOVAL_DATE.format(removalOn),
                "Cześć " + user.getDisplayName() + ",\n\nod dłuższego czasu nie korzystasz z konta w HourForge. Konta nieużywane przez "
                        + "24 miesiące usuwamy, aby nie przechowywać danych bez potrzeby.\n\n"
                        + "Jeśli chcesz zachować konto, po prostu zaloguj się przed " + REMOVAL_DATE.format(removalOn) + ":\n"
                        + frontendUrl + "/login\n\n"
                        + "Jeśli nic nie zrobisz, usuniemy Twoje imię, adres e-mail i hasło. Zapisane godziny pracy zostaną w raportach "
                        + "zespołów pod nazwą „Użytkownik usunięty”.\n");
    }

    /** Tells the owners' fellow admins that an unused owner account is kept because of their team. */
    public void sendInactiveOwner(User admin, String teamName, String ownerName) {
        sender.send(admin.getEmail(), "Właściciel zespołu " + teamName + " od dawna nie korzysta z HourForge",
                "Cześć " + admin.getDisplayName() + ",\n\n" + ownerName + ", właściciel zespołu " + teamName + ", nie korzysta z HourForge "
                        + "od ponad 24 miesięcy. Konto nie zostanie usunięte, bo zespół jest nadal używany.\n\n"
                        + "Poproś właściciela o przekazanie własności zespołu albo skontaktuj się z operatorem serwisu.\n");
    }

    public void sendEmailChanged(String oldEmail, String newEmail) {
        sender.send(oldEmail, "Adres e-mail konta w HourForge został zmieniony",
                "Adres e-mail Twojego konta w HourForge zmieniono na " + newEmail + ". Od teraz logujesz się nowym adresem.\n\n"
                        + "Jeśli to nie Ty, skontaktuj się z administratorem serwisu.\n");
    }
}
