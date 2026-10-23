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
                        + "pozostają w raportach zespołów pod nazwą „Użytkownik usunięty”.\n\n"
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

    public void sendEmailChanged(String oldEmail, String newEmail) {
        sender.send(oldEmail, "Adres e-mail konta w HourForge został zmieniony",
                "Adres e-mail Twojego konta w HourForge zmieniono na " + newEmail + ". Od teraz logujesz się nowym adresem.\n\n"
                        + "Jeśli to nie Ty, skontaktuj się z administratorem serwisu.\n");
    }
}
