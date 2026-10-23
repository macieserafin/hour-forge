package pl.macieserafin.hourforge.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.account.AccountDataService;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.operator.OperatorAccess;
import pl.macieserafin.hourforge.team.TeamManagementService;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Removes accounts nobody has used for 24 months (GDPR storage limitation). The person is warned by e-mail 30 and 7
 * days before, and any use of the app cancels it. Removal is the same anonymization as deleting the account oneself;
 * teams the person owns alone go with it. An owner of a team that others still use is kept, and its admins are told.
 * Instance operators are never removed.
 */
@Service
public class InactiveAccountService {
    private static final Logger log = LoggerFactory.getLogger(InactiveAccountService.class);
    static final Duration FIRST_WARNING = Duration.ofDays(30);
    static final Duration LAST_WARNING = Duration.ofDays(7);

    public enum Outcome { NOTHING, WARNED, REMOVED, KEPT }

    private final JobsProperties properties;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final TeamMemberRepository members;
    private final TeamManagementService teams;
    private final AccountDataService accounts;
    private final OperatorAccess operators;
    private final AccountMailService mail;

    public InactiveAccountService(JobsProperties properties, JdbcTemplate jdbc, UserRepository users, TeamMemberRepository members,
                                  TeamManagementService teams, AccountDataService accounts, OperatorAccess operators, AccountMailService mail) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.users = users;
        this.members = members;
        this.teams = teams;
        this.accounts = accounts;
        this.operators = operators;
        this.mail = mail;
    }

    /** Accounts within 30 days of removal (or past it) that still need a warning or a decision. */
    @Transactional(readOnly = true)
    public List<UUID> due(Instant now) {
        var warnFrom = now.minus(properties.inactiveAccountAge()).plus(FIRST_WARNING);
        return jdbc.queryForList("""
                select id from users
                where deleted_at is null and coalesce(last_active_at, created_at) < ?
                  and (inactivity_warned_days is null or inactivity_warned_days <> 0)
                order by coalesce(last_active_at, created_at)
                """, UUID.class, Timestamp.from(warnFrom));
    }

    @Transactional
    public Outcome handle(UUID userId, Instant now) {
        var user = users.findById(userId).orElse(null);
        if (user == null || user.getDeletedAt() != null || operators.isOperator(user)) return Outcome.NOTHING;
        var removalAt = (user.getLastActiveAt() != null ? user.getLastActiveAt() : user.getCreatedAt()).plus(properties.inactiveAccountAge());
        var warned = user.getInactivityWarnedDays();
        if (warned != null && warned == 7) {
            // Removed no earlier than the date the last warning gave.
            if (now.isBefore(removalAt) || now.isBefore(user.getInactivityWarnedAt().plus(LAST_WARNING))) return Outcome.NOTHING;
            return remove(user, now);
        }
        if (!now.isBefore(removalAt.minus(LAST_WARNING))) {
            // A late run (or a missed first warning) still gives the full week.
            var promised = removalAt.isAfter(now.plus(LAST_WARNING)) ? removalAt : now.plus(LAST_WARNING);
            mail.sendInactivityWarning(user, promised);
            user.warnAboutInactivity(7, now);
            return Outcome.WARNED;
        }
        if (warned == null && !now.isBefore(removalAt.minus(FIRST_WARNING))) {
            mail.sendInactivityWarning(user, removalAt);
            user.warnAboutInactivity(30, now);
            return Outcome.WARNED;
        }
        return Outcome.NOTHING;
    }

    private Outcome remove(User user, Instant now) {
        var owned = members.findActiveByUserId(user.getId()).stream().filter(member -> member.getRole() == TeamRole.OWNER).toList();
        var shared = owned.stream().filter(member -> members.findActiveByTeamId(member.getTeam().getId()).size() > 1).toList();
        if (!shared.isEmpty()) {
            user.warnAboutInactivity(0, now);
            for (var ownership : shared) {
                var team = ownership.getTeam();
                members.findActiveByTeamId(team.getId()).stream()
                        .filter(member -> member.getRole() == TeamRole.ADMIN)
                        .forEach(admin -> mail.sendInactiveOwner(admin.getUser(), team.getName(), user.getDisplayName()));
            }
            log.info("Inactive account kept because it owns teams others use: userId={}", user.getId());
            return Outcome.KEPT;
        }
        owned.forEach(ownership -> teams.deleteByOperator(ownership.getTeam().getId()));
        accounts.deleteWithoutPassword(user.getId());
        log.info("Inactive account removed: userId={}, teams={}", user.getId(), owned.size());
        return Outcome.REMOVED;
    }
}
