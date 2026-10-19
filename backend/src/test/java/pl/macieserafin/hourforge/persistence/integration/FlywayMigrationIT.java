package pl.macieserafin.hourforge.persistence.integration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT {
    @Test
    void upgradesAnExistingDatabaseAndPreservesDomainData() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:18-alpine")) {
            database.start();
            var initial = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .target("1").load();
            assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO users (id, email, display_name)
                        VALUES ('00000000-0000-0000-0000-000000000001', 'migration@example.test', 'Preserved user')
                        """);
                statement.execute("""
                        INSERT INTO teams (id, name, timezone)
                        VALUES ('00000000-0000-0000-0000-000000000002', 'Migration team', 'UTC');
                        INSERT INTO projects (id, team_id, name, description)
                        VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
                                'Migration project', 'Preserved description')
                        """);
                var upgraded = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load();
                assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
                upgraded.validate();
                assertThat(upgraded.migrate().migrationsExecuted).isZero();
                try (var result = statement.executeQuery("SELECT display_name FROM users WHERE email = 'migration@example.test'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("Preserved user");
                }
                try (var result = statement.executeQuery("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'time_entries' AND column_name IN ('paused_at', 'paused_duration_micros')
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(2);
                }
                try (var result = statement.executeQuery("""
                        SELECT count(*) FROM information_schema.check_constraints
                        WHERE constraint_name IN ('ck_projects_description_length', 'ck_time_entries_description_length')
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(2);
                }
                try (var result = statement.executeQuery("SELECT archived_at FROM teams WHERE name = 'Migration team'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getObject(1)).isNull();
                }
                try (var result = statement.executeQuery("SELECT description FROM projects WHERE name = 'Migration project'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("Preserved description");
                }
            }
        }
    }
}
