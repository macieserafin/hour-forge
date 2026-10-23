package pl.macieserafin.hourforge.image;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The stored pictures, kept apart from the users and teams rows so ordinary queries never load them. */
@Repository
public class ImageStore {
    private final JdbcTemplate jdbc;

    public ImageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAvatar(UUID userId, byte[] jpeg) {
        jdbc.update("insert into user_avatars (user_id, image) values (?, ?) on conflict (user_id) do update set image = excluded.image", userId, jpeg);
    }

    public Optional<byte[]> avatar(UUID userId) {
        return first(jdbc.query("select image from user_avatars where user_id = ?", (row, index) -> row.getBytes(1), userId));
    }

    public void deleteAvatar(UUID userId) {
        jdbc.update("delete from user_avatars where user_id = ?", userId);
    }

    public void saveTeamLogo(UUID teamId, byte[] jpeg) {
        jdbc.update("insert into team_logos (team_id, image) values (?, ?) on conflict (team_id) do update set image = excluded.image", teamId, jpeg);
    }

    public Optional<byte[]> teamLogo(UUID teamId) {
        return first(jdbc.query("select image from team_logos where team_id = ?", (row, index) -> row.getBytes(1), teamId));
    }

    public void deleteTeamLogo(UUID teamId) {
        jdbc.update("delete from team_logos where team_id = ?", teamId);
    }

    private static Optional<byte[]> first(List<byte[]> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
