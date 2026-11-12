package pl.macieserafin.hourforge.mail.preview;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.PlatformTransactionManager;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.EmailTokenService;
import pl.macieserafin.hourforge.auth.EmailVerificationService;
import pl.macieserafin.hourforge.jobs.JobsProperties;
import pl.macieserafin.hourforge.jobs.LongTimerReminderService;
import pl.macieserafin.hourforge.jobs.WeeklyReportService;
import pl.macieserafin.hourforge.mail.Mailer;
import pl.macieserafin.hourforge.mail.SmtpEmailSender;
import pl.macieserafin.hourforge.notification.NotificationKind;
import pl.macieserafin.hourforge.notification.NotificationService;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.report.ReportExportService;
import pl.macieserafin.hourforge.session.DeviceInfo;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMailService;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sends one sample of every e-mail through real SMTP to a local Mailpit, to look at them all at once. A tool, not a test:
 * the name keeps it out of the normal runs, and it does nothing without {@code -Dmail.preview=true}.
 *
 * <pre>
 * docker compose up -d   (Mailpit on localhost:1025, inbox on http://localhost:8025)
 * cd backend
 * ./mvnw.cmd test -Dtest=MailPreviewSend -Dmail.preview=true
 * </pre>
 *
 * Optional: {@code -Dmail.preview.host}, {@code -Dmail.preview.port}, {@code -Dmail.preview.frontend} (links in the mails).
 * Every message goes through the same service code as in production; only tokens, people and reports are samples.
 */
class MailPreviewSend {
    private static final String TOKEN = "Qx7Kp2Lm9Nz4Rt8Vw1Ys3Bc6Df0Gh5Jk-Ab_CdEfGh";

    @Test
    void sendEveryMailToMailpit() throws Exception {
        assumeTrue(Boolean.getBoolean("mail.preview"), "Run with -Dmail.preview=true");
        var smtp = new JavaMailSenderImpl();
        smtp.setHost(System.getProperty("mail.preview.host", "localhost"));
        smtp.setPort(Integer.getInteger("mail.preview.port", 1025));
        var frontend = System.getProperty("mail.preview.frontend", "http://localhost:5173");
        var sender = new SmtpEmailSender(smtp, "HourForge <no-reply@localhost>");
        var mailer = new Mailer(email -> {
            try {
                sender.deliver(email);
            } catch (Exception exception) {
                throw new IllegalStateException("Mailpit did not take the message", exception);
            }
        }, frontend);

        var tokens = mock(EmailTokenService.class);
        when(tokens.issue(any(), any(), any())).thenReturn(TOKEN);
        var notifications = mock(NotificationService.class);
        when(notifications.allows(any(), any())).thenReturn(true);
        for (var kind : NotificationKind.values()) {
            when(notifications.unsubscribeLinks(any(), org.mockito.ArgumentMatchers.eq(kind))).thenReturn(new Mailer.Unsubscribe(
                    frontend + "/unsubscribe/" + TOKEN + "?kind=" + kind, frontend + "/api/notifications/unsubscribe/one-click?token=" + TOKEN
                    + "&kind=" + kind, frontend + "/account#notifications"));
        }

        var ala = new User("ala@example.test", "Ala Kowalska");
        var team = new Team("Studio Kreatywne", "Europe/Warsaw");
        setId(team, UUID.fromString("7d4c1f2e-0b6a-4e8f-9c3d-2a1b5e6f7a80"));

        var account = new AccountMailService(tokens, mailer);
        account.sendPasswordReset(ala);
        account.sendRegistrationAttempt(ala);
        account.sendGoogleSignInAttempt(ala);
        account.sendAccountDeletionLink(ala, TOKEN);
        account.sendGoogleLinked(ala, "ala.kowalska@gmail.com");
        account.sendGoogleUnlinked(ala, "ala.kowalska@gmail.com");
        account.sendEmailChange(ala, "ala.nowa@example.test");
        account.sendEmailChanged("ala@example.test", "ala.nowa@example.test");
        account.sendAccountDeleted("ala@example.test");
        account.sendNewDeviceSignIn(ala, new DeviceInfo("Chrome 142", "Android", "91.1.x.x", "Kraków, Polska"), Instant.now());
        account.sendTwoFactorChanged(ala, true);
        account.sendTwoFactorChanged(ala, false);
        account.sendInactivityWarning(ala, Instant.now().plus(Duration.ofDays(30)));
        account.sendInactiveOwner(ala, team.getName(), "Jan Nowak");

        new EmailVerificationService(null, tokens, mailer, null, null, Clock.systemUTC()).sendConfirmation(ala);

        var teamMail = new TeamMailService(mailer, notifications);
        teamMail.sendInvitation("ola@example.test", team.getName(), "Ala Kowalska", frontend + "/invite/" + TOKEN,
                Instant.now().plus(Duration.ofDays(7)), 5);
        teamMail.sendRemoved(ala, team.getName());
        teamMail.sendOwnershipChange(ala, team.getName(), true);
        teamMail.sendOwnershipChange(ala, team.getName(), false);
        teamMail.sendRoleChange(ala, team.getName(), true);
        teamMail.sendRoleChange(ala, team.getName(), false);
        teamMail.sendArchived(ala, team.getName());

        // The two jobs build their mails in private steps; reflection keeps their production code unchanged.
        var project = new Project(team, "Strona internetowa", null);
        var entry = new TimeEntry(ala, team, project, EntryType.TIMER, LocalDate.now(), Instant.now().minus(Duration.ofHours(11)), null, null, "Projekt");
        var reminder = new LongTimerReminderService(mock(JobsProperties.class), mock(TimeEntryRepository.class), mailer,
                mock(UserPreferencesRepository.class), notifications, mock(PlatformTransactionManager.class));
        var send = LongTimerReminderService.class.getDeclaredMethod("send", TimeEntry.class, Duration.class);
        send.setAccessible(true);
        send.invoke(reminder, entry, Duration.ofHours(11));

        var members = mock(TeamMemberRepository.class);
        when(members.findActiveByTeamId(any())).thenReturn(List.of(new TeamMember(team, ala, TeamRole.OWNER, Instant.now())));
        var exports = mock(ReportExportService.class);
        when(exports.pdf(any(), any(), any(), anyBoolean()))
                .thenReturn(new ReportExportService.Download("hourforge-raport-2026-11-02_2026-11-08.pdf", "%PDF-1.4\n%%EOF\n".getBytes()));
        var weekly = new WeeklyReportService(mock(TeamRepository.class), members, exports, notifications, mailer, mock(PlatformTransactionManager.class));
        var deliver = WeeklyReportService.class.getDeclaredMethod("deliver", Team.class, LocalDate.class);
        deliver.setAccessible(true);
        deliver.invoke(weekly, team, LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY));
    }

    private static void setId(Object entity, UUID id) throws ReflectiveOperationException {
        var field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
