package pl.macieserafin.hourforge.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.account.AccountDataService;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.session.DeviceSessions;
import pl.macieserafin.hourforge.team.TeamManagementService;
import pl.macieserafin.hourforge.twofactor.TwoFactorService;
import pl.macieserafin.hourforge.user.UserRepository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What the instance operator can do: see how the instance is used, find accounts and teams, block and remove abuse,
 * help a person who lost their authenticator, and show a notice to everybody. Every action is logged with its author.
 */
@Service
public class OperatorService {
    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);
    static final int PAGE_SIZE = 20;

    public record Stats(long accounts, long unconfirmed, long blocked, long activeLastWeek, long activeLastMonth,
                        long twoFactor, long teams, long timeEntries) {
    }

    public record Account(UUID id, String email, String displayName, Instant createdAt, Instant lastActiveAt, boolean confirmed,
                          Instant blockedAt, String blockedReason, boolean twoFactor, long ownedTeams, boolean inactiveOwnerKept) {
    }

    public record TeamRow(UUID id, String name, Instant createdAt, Instant archivedAt, long members, String ownerEmail, long timeEntries) {
    }

    public record Page<T>(List<T> items, int page, boolean hasMore) {
    }

    public record AnnouncementView(String message, Announcement.Level level, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final DeviceSessions devices;
    private final TwoFactorService twoFactor;
    private final AccountDataService accounts;
    private final TeamManagementService teams;
    private final AnnouncementRepository announcements;
    private final AccountMailService mail;
    private final Clock clock;

    public OperatorService(JdbcTemplate jdbc, UserRepository users, UserCredentialRepository credentials, DeviceSessions devices,
                           TwoFactorService twoFactor, AccountDataService accounts, TeamManagementService teams,
                           AnnouncementRepository announcements, AccountMailService mail, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.credentials = credentials;
        this.devices = devices;
        this.twoFactor = twoFactor;
        this.accounts = accounts;
        this.teams = teams;
        this.announcements = announcements;
        this.mail = mail;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        var now = clock.instant();
        return new Stats(
                count("select count(*) from users where deleted_at is null"),
                count("select count(*) from users where deleted_at is null and email_verified_at is null"),
                count("select count(*) from users where deleted_at is null and blocked_at is not null"),
                count("select count(*) from users where deleted_at is null and last_active_at >= ?", Timestamp.from(now.minus(Duration.ofDays(7)))),
                count("select count(*) from users where deleted_at is null and last_active_at >= ?", Timestamp.from(now.minus(Duration.ofDays(30)))),
                count("select count(*) from user_two_factor where enabled_at is not null"),
                count("select count(*) from teams"),
                count("select count(*) from time_entries"));
    }

    @Transactional(readOnly = true)
    public Page<Account> accounts(String query, int page) {
        var pattern = pattern(query);
        var rows = jdbc.query("""
                select u.id, u.email, u.display_name, u.created_at, u.last_active_at, u.email_verified_at is not null as confirmed,
                       u.blocked_at, u.blocked_reason,
                       exists (select 1 from user_two_factor t where t.user_id = u.id and t.enabled_at is not null) as two_factor,
                       (select count(*) from team_members m where m.user_id = u.id and m.left_at is null and m.role = 'OWNER') as owned_teams,
                       coalesce(u.inactivity_warned_days = 0, false) as inactive_owner_kept
                from users u
                where u.deleted_at is null and (cast(? as text) is null or lower(u.email) like ? escape '\\' or lower(u.display_name) like ? escape '\\')
                order by u.created_at desc, u.id limit ? offset ?
                """, (row, index) -> new Account(row.getObject("id", UUID.class), row.getString("email"), row.getString("display_name"),
                        instant(row.getTimestamp("created_at")), instant(row.getTimestamp("last_active_at")), row.getBoolean("confirmed"),
                        instant(row.getTimestamp("blocked_at")), row.getString("blocked_reason"), row.getBoolean("two_factor"),
                        row.getLong("owned_teams"), row.getBoolean("inactive_owner_kept")),
                pattern, pattern, pattern, PAGE_SIZE + 1, offset(page));
        return page(rows, page);
    }

    @Transactional(readOnly = true)
    public Page<TeamRow> teams(String query, int page) {
        var pattern = pattern(query);
        var rows = jdbc.query("""
                select t.id, t.name, t.created_at, t.archived_at,
                       (select count(*) from team_members m where m.team_id = t.id and m.left_at is null) as members,
                       (select u.email from team_members m join users u on u.id = m.user_id
                        where m.team_id = t.id and m.left_at is null and m.role = 'OWNER' limit 1) as owner_email,
                       (select count(*) from time_entries e where e.team_id = t.id) as entries
                from teams t
                where (cast(? as text) is null or lower(t.name) like ? escape '\\')
                order by t.created_at desc, t.id limit ? offset ?
                """, (row, index) -> new TeamRow(row.getObject("id", UUID.class), row.getString("name"), instant(row.getTimestamp("created_at")),
                        instant(row.getTimestamp("archived_at")), row.getLong("members"), row.getString("owner_email"), row.getLong("entries")),
                pattern, pattern, PAGE_SIZE + 1, offset(page));
        return page(rows, page);
    }

    /** Stops the account from signing in and ends all its sessions at once. */
    @Transactional
    public void block(UUID operatorId, UUID userId, String reason) {
        if (operatorId.equals(userId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operators cannot block themselves");
        var cleanReason = reason == null ? "" : reason.strip();
        if (cleanReason.isEmpty() || cleanReason.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a reason of up to 500 characters");
        var user = activeUser(userId);
        user.block(clock.instant(), cleanReason);
        credentials.findLockedByUserId(userId).ifPresent(credential -> credential.invalidateSessions());
        users.flush();
        devices.endAllExcept(userId, null);
        log.info("Operator blocked an account: operatorId={}, userId={}", operatorId, userId);
    }

    @Transactional
    public void unblock(UUID operatorId, UUID userId) {
        activeUser(userId).block(null, null);
        log.info("Operator unblocked an account: operatorId={}, userId={}", operatorId, userId);
    }

    /** For a person who lost both the authenticator and the backup codes, after the operator confirmed who they are. */
    @Transactional
    public void turnOffTwoFactor(UUID operatorId, UUID userId) {
        var user = activeUser(userId);
        if (!twoFactor.isEnabled(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-step sign-in is off");
        twoFactor.remove(userId);
        mail.sendTwoFactorChanged(user, false);
        log.info("Operator turned off two-step sign-in: operatorId={}, userId={}", operatorId, userId);
    }

    @Transactional
    public void deleteAccount(UUID operatorId, UUID userId) {
        if (operatorId.equals(userId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operators delete their own account in the account settings");
        accounts.deleteWithoutPassword(userId);
        devices.endAllExcept(userId, null);
        log.info("Operator deleted an account: operatorId={}, userId={}", operatorId, userId);
    }

    @Transactional
    public void deleteTeam(UUID operatorId, UUID teamId) {
        teams.deleteByOperator(teamId);
        log.info("Operator deleted a team: operatorId={}, teamId={}", operatorId, teamId);
    }

    @Transactional(readOnly = true)
    public Optional<AnnouncementView> announcement() {
        return announcements.findById(Announcement.ID)
                .map(found -> new AnnouncementView(found.getMessage(), found.getLevel(), found.getUpdatedAt()));
    }

    @Transactional
    public AnnouncementView announce(UUID operatorId, String message, Announcement.Level level) {
        var text = message == null ? "" : message.strip();
        if (text.isEmpty() || text.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a notice of up to 500 characters");
        var chosen = level == null ? Announcement.Level.INFO : level;
        var now = clock.instant();
        var stored = announcements.findById(Announcement.ID).map(found -> { found.update(text, chosen, now); return found; })
                .orElseGet(() -> announcements.save(new Announcement(text, chosen, now)));
        log.info("Operator changed the announcement: operatorId={}", operatorId);
        return new AnnouncementView(stored.getMessage(), stored.getLevel(), stored.getUpdatedAt());
    }

    @Transactional
    public void clearAnnouncement(UUID operatorId) {
        announcements.deleteById(Announcement.ID);
        log.info("Operator removed the announcement: operatorId={}", operatorId);
    }

    private pl.macieserafin.hourforge.user.User activeUser(UUID userId) {
        return users.findById(userId).filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private long count(String sql, Object... arguments) {
        var value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    /** A case-insensitive "contains" pattern with LIKE wildcards escaped, or null for no filter. */
    private static String pattern(String query) {
        if (query == null || query.isBlank()) return null;
        var text = query.strip().toLowerCase(Locale.ROOT);
        if (text.length() > 100) text = text.substring(0, 100);
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static int offset(int page) {
        if (page < 0 || page > 10_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return page * PAGE_SIZE;
    }

    private static <T> Page<T> page(List<T> rows, int page) {
        return new Page<>(rows.size() > PAGE_SIZE ? rows.subList(0, PAGE_SIZE) : rows, page, rows.size() > PAGE_SIZE);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
