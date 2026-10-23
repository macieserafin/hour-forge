package pl.macieserafin.hourforge.image;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.TeamManagementService;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.util.UUID;

/**
 * Profile pictures and team logos. A picture is shown only to people who share a team with its owner (or to the
 * owner), a logo only to the members of the team; only owners and admins change the logo.
 */
@Service
public class PictureService {
    private final ImageStore images;
    private final UserRepository users;
    private final TeamManagementService teams;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PictureService(ImageStore images, UserRepository users, TeamManagementService teams, JdbcTemplate jdbc, Clock clock) {
        this.images = images;
        this.users = users;
        this.teams = teams;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void setAvatar(UUID userId, byte[] upload) {
        var jpeg = process(upload);
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        images.saveAvatar(userId, jpeg);
        user.setAvatarUpdatedAt(clock.instant());
    }

    @Transactional
    public void removeAvatar(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        images.deleteAvatar(userId);
        user.setAvatarUpdatedAt(null);
    }

    @Transactional(readOnly = true)
    public byte[] avatar(UUID viewerId, UUID userId) {
        // Strangers get the same answer as for a person without a picture.
        if (!viewerId.equals(userId) && !shareATeam(viewerId, userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return images.avatar(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void setTeamLogo(UUID actorId, UUID teamId, byte[] upload) {
        var jpeg = process(upload);
        teams.changeLogo(actorId, teamId, clock.instant(), () -> images.saveTeamLogo(teamId, jpeg));
    }

    @Transactional
    public void removeTeamLogo(UUID actorId, UUID teamId) {
        teams.changeLogo(actorId, teamId, null, () -> images.deleteTeamLogo(teamId));
    }

    @Transactional(readOnly = true)
    public byte[] teamLogo(UUID viewerId, UUID teamId) {
        teams.requireActiveMember(viewerId, teamId);
        return images.teamLogo(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private boolean shareATeam(UUID first, UUID second) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from team_members a join team_members b on a.team_id = b.team_id
                               where a.user_id = ? and b.user_id = ? and a.left_at is null and b.left_at is null)
                """, Boolean.class, first, second));
    }

    private static byte[] process(byte[] upload) {
        try {
            return ImageProcessor.squareJpeg(upload);
        } catch (ImageProcessor.InvalidImageException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
