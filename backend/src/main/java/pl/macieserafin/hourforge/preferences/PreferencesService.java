package pl.macieserafin.hourforge.preferences;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.user.UserRepository;

@Service
public class PreferencesService {
    private final UserPreferencesRepository preferences;
    private final TeamMemberRepository members;
    private final UserRepository users;

    public PreferencesService(UserPreferencesRepository preferences, TeamMemberRepository members, UserRepository users) {
        this.preferences = preferences;
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse get(UUID userId) {
        var stored = preferences.findById(userId).orElseGet(() -> new UserPreferences(userId));
        // A team the person has left is no longer a default; the stored value is kept but not offered.
        var team = stored.getDefaultTeamId() != null && isActiveMember(stored.getDefaultTeamId(), userId) ? stored.getDefaultTeamId() : null;
        return new PreferencesResponse(team, stored.getStartPage(), stored.getTimeFormat(), stored.getWeekStart(), stored.isLongTimerEmail(),
                stored.isBrowserNotifications(), stored.getLongTimerHours(), stored.isTitleTimer());
    }

    @Transactional
    public PreferencesResponse save(UUID userId, PreferencesRequest request) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (request.defaultTeamId() != null && !isActiveMember(request.defaultTeamId(), userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The default team must be one of your teams");
        }
        var stored = preferences.findById(userId).orElseGet(() -> new UserPreferences(userId));
        stored.apply(request.defaultTeamId(), request.startPage(), request.timeFormat(), request.weekStart(), request.longTimerEmail(),
                request.browserNotifications(), request.longTimerHours(), request.titleTimer());
        preferences.saveAndFlush(stored);
        return get(userId);
    }

    private boolean isActiveMember(UUID teamId, UUID userId) {
        return members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null).isPresent();
    }
}
