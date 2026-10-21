package pl.macieserafin.hourforge.jobs;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    static final LocalTime SEND_AFTER = LocalTime.of(8, 0);

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ReportExportService exports;
    private final NotificationService notifications;
    private final EmailSender sender;

    public WeeklyReportService(TeamRepository teams, TeamMemberRepository members, ReportExportService exports,
                               NotificationService notifications, EmailSender sender) {
        this.teams = teams;
        this.members = members;
        this.exports = exports;
        this.notifications = notifications;
        this.sender = sender;
    }

    /** Sends the reports that are due at {@code now} and returns how many teams got one. A team is handled once per week. */
    @Transactional
    public int sendDue(Instant now) {
        int sent = 0;
        for (var team : teams.findByWeeklyReportEnabledTrueAndArchivedAtIsNull()) {
            var local = now.atZone(ZoneId.of(team.getTimezone()));
            if (local.getDayOfWeek() != DayOfWeek.MONDAY || local.toLocalTime().isBefore(SEND_AFTER)) continue;
            var thisMonday = local.toLocalDate();
            if (thisMonday.equals(team.getWeeklyReportSentFor())) continue;
            // Marked first: a failure while building one report must not make the others repeat or this one loop.
            team.setWeeklyReportSentFor(thisMonday);
            teams.saveAndFlush(team);
            if (deliver(team, thisMonday.minusWeeks(1))) sent++;
        }
        return sent;
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
