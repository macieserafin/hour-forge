package pl.macieserafin.hourforge.jobs;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.notification.NotificationKind;
import pl.macieserafin.hourforge.notification.NotificationService;
import pl.macieserafin.hourforge.report.ReportExportService;
import pl.macieserafin.hourforge.report.ReportFilter;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;

/** On Monday from 8:00 (team time) mails last week's PDF report to the team's owner and admins who want it. */
@Service
public class WeeklyReportService {
    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);
    static final LocalTime SEND_AFTER = LocalTime.of(8, 0);

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ReportExportService exports;
    private final NotificationService notifications;
    private final EmailSender sender;
    private final TransactionTemplate transaction;

    public WeeklyReportService(TeamRepository teams, TeamMemberRepository members, ReportExportService exports,
                               NotificationService notifications, EmailSender sender,
                               PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.teams = teams;
        this.members = members;
        this.exports = exports;
        this.notifications = notifications;
        this.sender = sender;
    }

    /**
     * Sends the reports that are due at {@code now} and returns how many teams got one. A team is handled once per week.
     * Every team has its own transaction: the week is marked and the mail leaves together at its commit, so a team whose
     * report cannot be built is skipped (and tried again at the next run) without repeating or holding back the others.
     */
    public int sendDue(Instant now) {
        int sent = 0;
        for (var teamId : teams.findByWeeklyReportEnabledTrueAndArchivedAtIsNull().stream().map(Team::getId).toList()) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> sendIfDue(teamId, now)))) sent++;
            } catch (RuntimeException exception) {
                log.error("Weekly report failed for teamId={}", teamId, exception);
            }
        }
        return sent;
    }

    private boolean sendIfDue(UUID teamId, Instant now) {
        var team = teams.findLockedById(teamId).orElse(null);
        if (team == null || team.isArchived() || !team.isWeeklyReportEnabled()) return false;
        var local = now.atZone(ZoneId.of(team.getTimezone()));
        if (local.getDayOfWeek() != DayOfWeek.MONDAY || local.toLocalTime().isBefore(SEND_AFTER)) return false;
        var thisMonday = local.toLocalDate();
        if (thisMonday.equals(team.getWeeklyReportSentFor())) return false;
        team.setWeeklyReportSentFor(thisMonday);
        teams.saveAndFlush(team);
        return deliver(team, thisMonday.minusWeeks(1));
    }

    private boolean deliver(Team team, java.time.LocalDate from) {
        var recipients = members.findActiveByTeamId(team.getId()).stream()
                .filter(member -> member.getRole().canManage() && member.getUser().getDeletedAt() == null)
                .filter(member -> notifications.allows(member.getUser().getId(), NotificationKind.WEEKLY_REPORT)).toList();
        if (recipients.isEmpty()) return false;
        var to = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        var filter = new ReportFilter(from, to, List.<UUID>of(), List.<UUID>of(), null);
        var pdf = exports.pdf(recipients.get(0).getUser().getId(), team.getId(), filter, false);
        for (var recipient : recipients) {
            var person = recipient.getUser();
            sender.sendWithAttachment(person.getEmail(), "Raport tygodniowy zespołu " + team.getName() + " (" + from + " – " + to + ")",
                    "Cześć " + person.getDisplayName() + ",\n\nw załączniku raport czasu pracy zespołu „" + team.getName() + "” za tydzień "
                            + from + " – " + to + ". Wysyłamy go w poniedziałek rano właścicielowi i administratorom; wyłącza go właściciel w ustawieniach zespołu.\n"
                            + notifications.footer(person, NotificationKind.WEEKLY_REPORT),
                    pdf.filename(), pdf.content(), "application/pdf");
        }
        return true;
    }
}
