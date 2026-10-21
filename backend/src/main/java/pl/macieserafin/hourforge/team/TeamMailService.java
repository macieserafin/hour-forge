package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.user.User;

/** The e-mails about what happens in a team. Accounts that were deleted have no address and get nothing. */
@Service
public class TeamMailService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final EmailSender sender;

    public TeamMailService(EmailSender sender) {
        this.sender = sender;
    }

    public void sendInvitation(String to, String teamName, String inviterName, String url, Instant expiresAt, Integer maxUses) {
        sender.send(to, "Zaproszenie do zespołu " + teamName + " w HourForge",
                inviterName + " zaprasza Cię do zespołu „" + teamName + "” w HourForge.\n\n"
                        + "Otwórz link, zaloguj się lub załóż konto i dołącz do zespołu:\n" + url + "\n\n"
                        + "Link jest ważny do " + DATE.format(expiresAt) + (maxUses == null ? "" : " i można go użyć tyle razy: " + maxUses) + ".\n"
                        + "Jeśli nie znasz tej osoby albo nie spodziewałeś się zaproszenia, zignoruj tę wiadomość.\n");
    }

    public void sendRemoved(User person, String teamName) {
        notify(person, "Usunięto Cię z zespołu " + teamName,
                "Twój dostęp do zespołu „" + teamName + "” w HourForge został zakończony przez jego właściciela lub administratora. "
                        + "Zapisana przez Ciebie praca pozostaje w raportach zespołu. Ponowne dołączenie wymaga nowego zaproszenia.");
    }

    public void sendOwnershipChange(User person, String teamName, boolean becameOwner) {
        notify(person, becameOwner ? "Jesteś właścicielem zespołu " + teamName : "Przekazałeś własność zespołu " + teamName,
                becameOwner ? "Poprzedni właściciel przekazał Ci własność zespołu „" + teamName + "” w HourForge. Możesz teraz nim zarządzać, w tym nadawać rolę administratora i usunąć zespół."
                        : "Własność zespołu „" + teamName + "” w HourForge została przekazana innej osobie. Jesteś w nim teraz zwykłym członkiem.");
    }

    public void sendRoleChange(User person, String teamName, boolean admin) {
        notify(person, admin ? "Jesteś administratorem zespołu " + teamName : "Nie jesteś już administratorem zespołu " + teamName,
                admin ? "Właściciel zespołu „" + teamName + "” w HourForge nadał Ci rolę administratora. Możesz zarządzać członkami, zaproszeniami, projektami, tagami, korektami czasu i raportami zespołu."
                        : "Właściciel zespołu „" + teamName + "” w HourForge odebrał Ci rolę administratora. Zostajesz zwykłym członkiem zespołu.");
    }

    public void sendArchived(User person, String teamName) {
        notify(person, "Zespół " + teamName + " został zarchiwizowany",
                "Zespół „" + teamName + "” w HourForge został zarchiwizowany. Historia i raporty pozostają dostępne, ale nie można już dodawać ani zmieniać wpisów, projektów ani ustawień.");
    }

    private void notify(User person, String subject, String text) {
        if (person.getDeletedAt() != null) return;
        sender.send(person.getEmail(), subject, "Cześć " + person.getDisplayName() + ",\n\n" + text + "\n");
    }
}
