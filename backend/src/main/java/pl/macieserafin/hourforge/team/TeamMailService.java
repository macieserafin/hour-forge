package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import pl.macieserafin.hourforge.mail.MailContent;
import pl.macieserafin.hourforge.mail.Mailer;
import pl.macieserafin.hourforge.notification.NotificationKind;
import pl.macieserafin.hourforge.notification.NotificationService;
import pl.macieserafin.hourforge.user.User;

/** The e-mails about what happens in a team. Accounts that were deleted have no address and get nothing. */
@Service
public class TeamMailService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final Mailer mailer;
    private final NotificationService notifications;

    public TeamMailService(Mailer mailer, NotificationService notifications) {
        this.mailer = mailer;
        this.notifications = notifications;
    }

    public void sendInvitation(String to, String teamName, String inviterName, String url, Instant expiresAt, Integer maxUses) {
        mailer.send(to, MailContent.builder("Zaproszenie do zespołu " + teamName + " w HourForge")
                .title("Zaproszenie do zespołu „" + teamName + "”")
                .preheader(inviterName + " zaprasza Cię do wspólnego mierzenia czasu pracy.")
                .paragraph(inviterName + " zaprasza Cię do zespołu „" + teamName + "” w HourForge.")
                .action("Otwórz link, zaloguj się lub załóż konto i dołącz do zespołu:", "Dołącz do zespołu", url)
                .paragraph("Link jest ważny do " + DATE.format(expiresAt) + (maxUses == null ? "" : " i można go użyć tyle razy: " + maxUses) + ".")
                .note("Jeśli nie znasz tej osoby albo nie spodziewałeś się zaproszenia, zignoruj tę wiadomość.")
                .build());
    }

    public void sendRemoved(User person, String teamName) {
        notify(person, MailContent.builder("Usunięto Cię z zespołu " + teamName)
                .title("Zakończono Twój dostęp do zespołu")
                .preheader("Zapisana praca pozostaje w raportach zespołu.")
                .greeting(person.getDisplayName())
                .paragraph("Twój dostęp do zespołu „" + teamName + "” w HourForge został zakończony przez jego właściciela lub administratora. "
                        + "Zapisana przez Ciebie praca pozostaje w raportach zespołu. Ponowne dołączenie wymaga nowego zaproszenia."));
    }

    public void sendOwnershipChange(User person, String teamName, boolean becameOwner) {
        notify(person, MailContent.builder(becameOwner ? "Jesteś właścicielem zespołu " + teamName : "Przekazałeś własność zespołu " + teamName)
                .title(becameOwner ? "Jesteś właścicielem zespołu" : "Przekazano własność zespołu")
                .preheader("Zespół „" + teamName + "” ma nowego właściciela.")
                .greeting(person.getDisplayName())
                .paragraph(becameOwner ? "Poprzedni właściciel przekazał Ci własność zespołu „" + teamName + "” w HourForge. "
                        + "Możesz teraz nim zarządzać, w tym nadawać rolę administratora i usunąć zespół."
                        : "Własność zespołu „" + teamName + "” w HourForge została przekazana innej osobie. Jesteś w nim teraz zwykłym członkiem.")
                .link("Zespół w HourForge:", mailer.url("/teams")));
    }

    public void sendRoleChange(User person, String teamName, boolean admin) {
        notify(person, MailContent.builder(admin ? "Jesteś administratorem zespołu " + teamName : "Nie jesteś już administratorem zespołu " + teamName)
                .title(admin ? "Masz rolę administratora" : "Zmieniono Twoją rolę w zespole")
                .preheader("Zmiana roli w zespole „" + teamName + "”.")
                .greeting(person.getDisplayName())
                .paragraph(admin ? "Właściciel zespołu „" + teamName + "” w HourForge nadał Ci rolę administratora. Możesz zarządzać "
                        + "członkami, zaproszeniami, projektami, tagami, korektami czasu i raportami zespołu."
                        : "Właściciel zespołu „" + teamName + "” w HourForge odebrał Ci rolę administratora. Zostajesz zwykłym członkiem zespołu."));
    }

    public void sendArchived(User person, String teamName) {
        notify(person, MailContent.builder("Zespół " + teamName + " został zarchiwizowany")
                .title("Zespół został zarchiwizowany")
                .preheader("Historia i raporty pozostają dostępne.")
                .greeting(person.getDisplayName())
                .paragraph("Zespół „" + teamName + "” w HourForge został zarchiwizowany. Historia i raporty pozostają dostępne, "
                        + "ale nie można już dodawać ani zmieniać wpisów, projektów ani ustawień."));
    }

    private void notify(User person, MailContent.Builder content) {
        if (person.getDeletedAt() != null) return;
        if (!notifications.allows(person.getId(), NotificationKind.TEAM_EVENTS)) return;
        mailer.notify(person.getEmail(), content.build(), notifications.unsubscribeLinks(person, NotificationKind.TEAM_EVENTS));
    }
}
