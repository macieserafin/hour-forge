package pl.macieserafin.hourforge.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.user.User;

public final class UnitFixtures {
    public static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private UnitFixtures() {}
    public static <T> T identified(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
    public static User user() { return identified(new User("actor@example.test", "Actor")); }
    public static Team team() { return identified(new Team("Team", "Europe/Warsaw")); }
    public static Project project(Team team) { return identified(new Project(team, "Project", null)); }
}
