package pl.macieserafin.hourforge.timeentry;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
public class TimeEntryService {
    private static final int MAX_MANUAL_DURATION_SECONDS = 24 * 60 * 60;
    private static final int WORK_DATE_YEARS_BACK = 2;

    private final Clock clock;
    private final TimeEntryRepository entries;
    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ProjectRepository projects;

    public TimeEntryService(TimeEntryRepository entries, UserRepository users, TeamRepository teams,
                            TeamMemberRepository members, ProjectRepository projects, Clock clock) {
        this.clock = clock;
        this.entries = entries;
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public TimeEntryHistoryResponse history(UUID userId, UUID teamId, UUID projectId,
                                            LocalDate from, LocalDate to, int page, int size) {
        if (!users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || (from != null && to != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range or pagination");
        }
        members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (projectId != null && projects.findByIdAndTeamId(projectId, teamId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Specification<TimeEntry> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.equal(root.get("team").get("id"), teamId));
            predicates.add(cb.or(cb.equal(root.get("entryType"), EntryType.MANUAL),
                    cb.and(cb.equal(root.get("entryType"), EntryType.TIMER), cb.isNotNull(root.get("endedAt")))));
            if (projectId != null) predicates.add(cb.equal(root.get("project").get("id"), projectId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("workDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("workDate"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = entries.findAll(filter, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("workDate"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        var content = result.getContent().stream().map(entry -> new TimeEntryHistoryItem(
                entry.getId(), entry.getTeam().getId(), entry.getProject().getId(), entry.getProject().getName(),
                entry.getEntryType(), entry.getWorkDate(), entry.getDurationSeconds(), entry.getDescription(),
                entry.getStartedAt(), entry.getEndedAt())).toList();
        return new TimeEntryHistoryResponse(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public TimeEntryResponse start(UUID userId, StartTimerRequest request) {
        var user = lockUser(userId);
        var project = writableProject(userId, request.teamId(), request.projectId());
        if (activeEntry(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A timer is already running");
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var workDate = now.atZone(ZoneId.of(project.getTeam().getTimezone())).toLocalDate();
        var entry = new TimeEntry(user, project.getTeam(), project, EntryType.TIMER, workDate,
                now, null, null, request.description());
        return response(entries.saveAndFlush(entry));
    }

    @Transactional
    public TimeEntryResponse stop(UUID userId, StopTimerRequest request) {
        lockUser(userId);
        var entry = unfinishedTimer(userId, request.entryId());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var effectiveEnd = entry.getPausedAt() == null ? now : entry.getPausedAt();
        long seconds = Duration.between(entry.getStartedAt(), effectiveEnd)
                .minus(entry.getPausedDurationMicros(), ChronoUnit.MICROS).getSeconds();
        if (seconds < 1 || seconds > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timer duration must fit a positive number of whole seconds");
        }
        // Ownership is sufficient: leaving a team must not prevent stopping one's own timer.
        completePause(entry, now);
        entry.setEndedAt(now);
        entry.setDurationSeconds((int) seconds);
        if (request.description() != null) {
            entry.setDescription(request.description().isBlank() ? null : request.description());
        }
        return response(entries.saveAndFlush(entry));
    }

    @Transactional
    public TimeEntryResponse pause(UUID userId, TimerStateRequest request) {
        lockUser(userId);
        var entry = unfinishedTimer(userId, request.entryId());
        if (entry.getPausedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timer is already paused");
        }
        entry.setPausedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        updateDescription(entry, request.description());
        return response(entries.saveAndFlush(entry));
    }

    @Transactional
    public TimeEntryResponse resume(UUID userId, TimerStateRequest request) {
        lockUser(userId);
        var entry = unfinishedTimer(userId, request.entryId());
        if (entry.getPausedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timer is not paused");
        }
        writableProject(userId, entry.getTeam().getId(), entry.getProject().getId());
        completePause(entry, clock.instant().truncatedTo(ChronoUnit.MICROS));
        updateDescription(entry, request.description());
        return response(entries.saveAndFlush(entry));
    }

    private TimeEntry unfinishedTimer(UUID userId, UUID entryId) {
        var entry = entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (entry.getEntryType() != EntryType.TIMER || entry.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This entry is not an active timer");
        }
        return entry;
    }

    private void completePause(TimeEntry entry, Instant now) {
        if (entry.getPausedAt() != null) {
            entry.setPausedDurationMicros(Math.addExact(entry.getPausedDurationMicros(),
                    ChronoUnit.MICROS.between(entry.getPausedAt(), now)));
            entry.setPausedAt(null);
        }
    }

    private void updateDescription(TimeEntry entry, String description) {
        if (description != null) entry.setDescription(description.isBlank() ? null : description);
    }

    @Transactional(readOnly = true)
    public Optional<TimeEntryResponse> active(UUID userId) {
        if (!users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return activeEntry(userId).map(this::response);
    }

    @Transactional
    public TimeEntryResponse createManual(UUID userId, ManualTimeEntryRequest request) {
        var user = lockUser(userId);
        var project = writableProject(userId, request.teamId(), request.projectId());
        requireManualDuration(request.durationSeconds());
        requireWorkDateInWindow(request.workDate(), project.getTeam().getTimezone());
        var entry = new TimeEntry(user, project.getTeam(), project, EntryType.MANUAL, request.workDate(),
                null, null, request.durationSeconds(), request.description());
        return response(entries.saveAndFlush(entry));
    }

    @Transactional
    public TimeEntryResponse update(UUID userId, UUID entryId, UpdateTimeEntryRequest request) {
        var entry = editableEntry(userId, entryId);
        var project = projects.findByIdAndTeamId(request.projectId(), entry.getTeam().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!project.getId().equals(entry.getProject().getId()) && project.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot move an entry to an archived project");
        }
        int durationSeconds = request.durationSeconds().intValueExact();
        // Unchanged values may predate the limits, e.g. a long forgotten timer or an old entry.
        if (!entry.getDurationSeconds().equals(durationSeconds)) {
            requireManualDuration(durationSeconds);
        }
        if (!entry.getWorkDate().equals(request.workDate())) {
            requireWorkDateInWindow(request.workDate(), entry.getTeam().getTimezone());
        }
        entry.setProject(project);
        entry.setWorkDate(request.workDate());
        entry.setDurationSeconds(durationSeconds);
        entry.setDescription(request.description());
        return response(entries.saveAndFlush(entry));
    }

    @Transactional
    public void delete(UUID userId, UUID entryId) {
        entries.delete(editableEntry(userId, entryId));
        entries.flush();
    }

    private void requireManualDuration(int durationSeconds) {
        if (durationSeconds > MAX_MANUAL_DURATION_SECONDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A time entry cannot exceed 24 hours");
        }
    }

    private void requireWorkDateInWindow(LocalDate workDate, String timezone) {
        var today = clock.instant().atZone(ZoneId.of(timezone)).toLocalDate();
        if (workDate.isAfter(today) || workDate.isBefore(today.minusYears(WORK_DATE_YEARS_BACK))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Work date must be within the last " + WORK_DATE_YEARS_BACK + " years and not in the future");
        }
    }

    private TimeEntry editableEntry(UUID userId, UUID entryId) {
        lockUser(userId);
        var entry = entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var team = teams.findLockedById(entry.getTeam().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        members.findByTeamIdAndUserId(entry.getTeam().getId(), userId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (team.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Entries of an archived team cannot be changed");
        }
        if (entry.getEntryType() == EntryType.TIMER && entry.getEndedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only completed entries can be changed");
        }
        return entry;
    }

    private Optional<TimeEntry> activeEntry(UUID userId) {
        return entries.findByUserIdAndEntryTypeAndEndedAtIsNull(userId, EntryType.TIMER);
    }

    private User lockUser(UUID userId) {
        return users.findLockedForTimeEntryById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Project writableProject(UUID userId, UUID teamId, UUID projectId) {
        // Same team lock as project and team archiving: checking archivedAt and inserting are atomic with it.
        var team = teams.findLockedById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (team.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived teams cannot receive new time entries");
        }
        var project = projects.findByIdAndTeamId(projectId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (project.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived projects cannot receive new time entries");
        }
        return project;
    }

    private TimeEntryResponse response(TimeEntry entry) {
        return new TimeEntryResponse(entry.getId(), entry.getUser().getId(), entry.getTeam().getId(),
                entry.getProject().getId(), entry.getEntryType(), entry.getWorkDate(), entry.getStartedAt(),
                entry.getEndedAt(), entry.getDurationSeconds(), entry.getDescription(), entry.getCreatedAt(), entry.getUpdatedAt(),
                entry.getPausedAt(), entry.getPausedDurationMicros() / 1_000_000.0);
    }
}
