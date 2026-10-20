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
                statement.execute("""
                        INSERT INTO time_entries (id, user_id, team_id, project_id, entry_type, work_date, duration_seconds)
                        VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001',
                            '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
                            'MANUAL', '2026-10-19', 60)
                        """);
                statement.execute("""
                        INSERT INTO team_members (id, team_id, user_id, role, joined_at, left_at)
                        VALUES ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002',
                            '00000000-0000-0000-0000-000000000001', 'MEMBER', '2020-01-01', '2020-01-02');
                        INSERT INTO team_invites (id, team_id, created_by, token_hash, expires_at)
                        VALUES ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002',
                            '00000000-0000-0000-0000-000000000001', 'legacy-long', '9999-01-01'),
                            ('00000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000002',
                            '00000000-0000-0000-0000-000000000001', 'legacy-expired', '2000-01-01')
                        """);
                assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(11);
                try (var result = statement.executeQuery("SELECT removed_at = left_at FROM team_members")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                }
                try (var result = statement.executeQuery("SELECT expires_at <= CURRENT_TIMESTAMP + interval '30 days' AND expires_at > CURRENT_TIMESTAMP FROM team_invites WHERE token_hash = 'legacy-long'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                }
                try (var result = statement.executeQuery("SELECT expires_at = TIMESTAMPTZ '2000-01-01' FROM team_invites WHERE token_hash = 'legacy-expired'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                }
                upgraded.validate();
                try (var result = statement.executeQuery("SELECT work_date, duration_seconds FROM time_entries")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getDate(1).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 19));
                    assertThat(result.getInt(2)).isEqualTo(60);
                }
                statement.execute("UPDATE time_entries SET work_date = NULL");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute("""
                        UPDATE time_entries SET entry_type = 'TIMER', started_at = CURRENT_TIMESTAMP,
                            ended_at = CURRENT_TIMESTAMP + interval '60 seconds'
                        """)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("ck_time_entries_timer_date");
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
                try (var result = statement.executeQuery("SELECT description, repository_url FROM projects WHERE name = 'Migration project'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("Preserved description");
                    assertThat(result.getString(2)).isNull();
                }
            }
        }
    }
}
