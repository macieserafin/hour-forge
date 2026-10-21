package pl.macieserafin.hourforge.tag;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.user.UserRepository;

/** Work tags of a team. Owners and admins manage them; every active member can read and use them. */
@Service
public class TagService {
    private final TagRepository tags;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;
    private final Clock clock;

    public TagService(TagRepository tags, TeamRepository teams, TeamMemberRepository members, UserRepository users, Clock clock) {
        this.tags = tags;
        this.teams = teams;
        this.members = members;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list(UUID userId, UUID teamId, boolean includeArchived) {
        requireMember(userId, teamId, false);
        var result = includeArchived ? tags.findByTeamIdOrderByNameAscIdAsc(teamId) : tags.findByTeamIdAndArchivedAtIsNullOrderByNameAscIdAsc(teamId);
        return result.stream().map(TagResponse::of).toList();
    }

    @Transactional
    public TagResponse create(UUID userId, UUID teamId, TagRequest request) {
        var team = requireMember(userId, teamId, true);
        requireUniqueName(teamId, request.name(), null);
        return TagResponse.of(save(new Tag(team, request.name(), request.color())));
    }

    @Transactional
    public TagResponse update(UUID userId, UUID teamId, UUID tagId, TagRequest request) {
        requireMember(userId, teamId, true);
        var tag = find(teamId, tagId);
        if (tag.getArchivedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived tags cannot be edited");
        requireUniqueName(teamId, request.name(), tagId);
        tag.setName(request.name());
        tag.setColor(request.color());
        return TagResponse.of(save(tag));
    }

    @Transactional
    public TagResponse archive(UUID userId, UUID teamId, UUID tagId) {
        requireMember(userId, teamId, true);
        var tag = find(teamId, tagId);
        if (tag.getArchivedAt() == null) tag.setArchivedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return TagResponse.of(save(tag));
    }

    @Transactional
    public TagResponse restore(UUID userId, UUID teamId, UUID tagId) {
        requireMember(userId, teamId, true);
        var tag = find(teamId, tagId);
        tag.setArchivedAt(null);
        return TagResponse.of(save(tag));
    }

    private void requireUniqueName(UUID teamId, String name, UUID ownId) {
        if (tags.existsByTeamIdAndNameIgnoreCaseAndIdNot(teamId, name, ownId == null ? new UUID(0, 0) : ownId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A tag with this name already exists");
        }
    }

    private Tag save(Tag tag) {
        try {
            return tags.saveAndFlush(tag);
        } catch (DataIntegrityViolationException exception) {
            // Two requests with the same name raced past the check; the unique index decided.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A tag with this name already exists");
        }
    }

    private Tag find(UUID teamId, UUID tagId) {
        return tags.findByIdAndTeamId(tagId, teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Reads need only active membership; writes take the team lock, need owner or admin and a team that is not archived. */
    private Team requireMember(UUID userId, UUID teamId, boolean write) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var team = (write ? teams.findLockedById(teamId) : teams.findById(teamId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var member = members.findByTeamIdAndUserId(teamId, userId).filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (write && !member.getRole().canManage()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (write && team.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tags of an archived team cannot be changed");
        return team;
    }
}
