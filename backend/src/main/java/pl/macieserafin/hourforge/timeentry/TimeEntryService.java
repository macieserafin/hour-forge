package pl.macieserafin.hourforge.timeentry;

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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class TimeEntryService {
    private final TimeEntryRepository entries;
    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ProjectRepository projects;

    public TimeEntryService(TimeEntryRepository entries, UserRepository users, TeamRepository teams,
                            TeamMemberRepository members, ProjectRepository projects) {
        this.entries = entries;
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.projects = projects;
    }

    @Transactional
    public TimeEntryResponse start(UUID userId, StartTimerRequest request) {
        var user = lockUser(userId);
        var project = writableProject(userId, request.teamId(), request.projectId());
        if (activeEntry(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A timer is already running");
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var workDate = now.atZone(ZoneId.of(project.getTeam().getTimezone())).toLocalDate();
        var entry = new TimeEntry(user, project.getTeam(), project, EntryType.TIMER, workDate,
                now, null, null, request.description());
        return response(entries.saveAndFlush(entry));
    }

    @Transactional
    public TimeEntryResponse stop(UUID userId, StopTimerRequest request) {
        lockUser(userId);
        var entry = entries.findByIdAndUserId(request.entryId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (entry.getEntryType() != EntryType.TIMER || entry.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This entry is not an active timer");
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long seconds = Duration.between(entry.getStartedAt(), now).getSeconds();
        if (seconds < 1 || seconds > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timer duration must fit a positive number of whole seconds");
        }
        // Ownership is sufficient: leaving a team must not prevent stopping one's own timer.
        entry.setEndedAt(now);
        entry.setDurationSeconds((int) seconds);
        if (request.description() != null) {
            entry.setDescription(request.description().isBlank() ? null : request.description());
        }
        return response(entries.saveAndFlush(entry));
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
        var entry = new TimeEntry(user, project.getTeam(), project, EntryType.MANUAL, request.workDate(),
                null, null, request.durationSeconds(), request.description());
        return response(entries.saveAndFlush(entry));
    }

    private Optional<TimeEntry> activeEntry(UUID userId) {
        return entries.findByUserIdAndEntryTypeAndEndedAtIsNull(userId, EntryType.TIMER);
    }

    private User lockUser(UUID userId) {
        return users.findLockedForTimeEntryById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Project writableProject(UUID userId, UUID teamId, UUID projectId) {
        // Same team lock as project archiving: checking archivedAt and inserting are atomic with it.
        teams.findLockedById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
                entry.getEndedAt(), entry.getDurationSeconds(), entry.getDescription(), entry.getCreatedAt(), entry.getUpdatedAt());
    }
}
