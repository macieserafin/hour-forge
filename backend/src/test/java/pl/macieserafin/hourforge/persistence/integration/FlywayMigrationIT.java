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
                var upgraded = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                        .target("32").load();
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
                assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(31);
                // These links existed before credential versions were recorded. Migration must not grant them
                // the current version: the account might have changed since they were sent.
                statement.execute("""
                        INSERT INTO email_tokens (id, user_id, purpose, token_hash, new_email, expires_at)
                        VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'RESET_PASSWORD',
                                repeat('a', 64), NULL, CURRENT_TIMESTAMP + interval '1 hour'),
                               (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'CHANGE_EMAIL',
                                repeat('b', 64), 'new@example.test', CURRENT_TIMESTAMP + interval '1 day'),
                               (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'VERIFY_EMAIL',
                                repeat('c', 64), NULL, CURRENT_TIMESTAMP + interval '1 day')
                        """);
                // A password account and its sign-in from before V37, which adds accounts without a password,
                // linked sign-in providers and the sign-in method.
                statement.execute("""
                        INSERT INTO user_credentials (user_id, password_hash)
                        VALUES ('00000000-0000-0000-0000-000000000001', '$2a$12$legacy.hash.kept.as.it.was');
                        INSERT INTO login_events (id, user_id, occurred_at, outcome)
                        VALUES ('00000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000001',
                                CURRENT_TIMESTAMP, 'SUCCESS')
                        """);
                upgraded = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                        .target("37").load();
                // V33 binds links to credential versions, V34 adds the repeat-day request records, V35 the template tag,
                // V36 the entry version, V37 accounts without a password, linked providers and the sign-in method.
                assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(5);
                // The unnamed CHECK of V10 has the name PostgreSQL gives it; V38 drops it by that name.
                try (var result = statement.executeQuery("""
                        SELECT conname FROM pg_constraint
                        WHERE conrelid = 'email_tokens'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%RESET_PASSWORD%'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("email_tokens_purpose_check");
                    assertThat(result.next()).isFalse();
                }
                upgraded = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                        .target("38").load();
                // V38 lets a link confirm the removal of an account without a password (DELETE_ACCOUNT).
                assertThat(upgraded.migrate().migrationsExecuted).isOne();
                statement.execute("""
                        INSERT INTO email_tokens (id, user_id, purpose, token_hash, new_email, expires_at)
                        VALUES ('00000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000001', 'DELETE_ACCOUNT',
                                repeat('d', 64), NULL, CURRENT_TIMESTAMP + interval '1 day')
                        """);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute("""
                        INSERT INTO email_tokens (id, user_id, purpose, token_hash, new_email, expires_at)
                        VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'UNKNOWN_PURPOSE',
                                repeat('e', 64), NULL, CURRENT_TIMESTAMP + interval '1 day')
                        """)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("ck_email_tokens_purpose");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute("""
                        INSERT INTO email_tokens (id, user_id, purpose, token_hash, new_email, expires_at)
                        VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'DELETE_ACCOUNT',
                                repeat('f', 64), 'other@example.test', CURRENT_TIMESTAMP + interval '1 day')
                        """)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("ck_email_tokens_new_email");
                statement.execute("DELETE FROM email_tokens WHERE id = '00000000-0000-0000-0000-000000000009'");
                try (var result = statement.executeQuery("""
                        SELECT c.password_hash, e.method, (SELECT count(*) FROM user_identities)
                        FROM user_credentials c JOIN login_events e ON e.user_id = c.user_id
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("$2a$12$legacy.hash.kept.as.it.was");
                    assertThat(result.getString(2)).isEqualTo("PASSWORD");
                    assertThat(result.getInt(3)).isZero();
                }
                try (var result = statement.executeQuery("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_name = 'user_credentials' AND column_name = 'password_hash'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("YES");
                }
                try (var result = statement.executeQuery("SELECT min(version), (SELECT count(tag_id) FROM entry_templates) FROM time_entries")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
                try (var result = statement.executeQuery("SELECT count(*) FROM copy_day_requests")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
                // The three links from before V33 survive V37 and V38 unchanged.
                try (var result = statement.executeQuery("SELECT count(*), bool_and(credential_version IS NULL) FROM email_tokens")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(3);
                    assertThat(result.getBoolean(2)).isTrue();
                }
                try (var result = statement.executeQuery("SELECT last_active_at IS NOT NULL FROM users WHERE id = '00000000-0000-0000-0000-000000000001'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                }
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
                upgraded = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load();
                // V39 adds the outbox of e-mails, empty; an attachment needs its name, type and content together.
                assertThat(upgraded.migrate().migrationsExecuted).isOne();
                try (var result = statement.executeQuery("SELECT count(*) FROM outgoing_emails")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute("""
                        INSERT INTO outgoing_emails (id, recipient, subject, text_body, html_body, category, attachment_name,
                            next_attempt_at, created_at)
                        VALUES (gen_random_uuid(), 'a@example.test', 's', 't', 'h', 'TRANSACTIONAL', 'report.pdf',
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("ck_outgoing_emails_attachment");
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
