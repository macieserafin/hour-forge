package pl.macieserafin.hourforge.persistence.integration;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import pl.macieserafin.hourforge.config.DatabaseMigration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production database accounts (backend/docker/postgres/01-create-app-user.sh): the migration account owns the
 * schema and runs the migrations; the application account reads and writes rows but cannot change the schema.
 */
class DatabaseRolesIT {
    private static final String SCRIPT = "/create-app-user.sh";
    private static final String APP = "hourforge";
    private static final String APP_PASSWORD = "app-secret";
    private static final String MIGRATOR = "hourforge_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-secret";

    @Test
    void anExistingDatabaseMovesToSeparateAccountsAndTheApplicationLosesSchemaChanges() throws Exception {
        try (var database = database()) {
            database.start();
            // An older setup: the application account owns the schema and migrated part of it itself.
            assertThat(provision(database, false).getExitCode()).isZero();
            Flyway.configure().dataSource(database.getJdbcUrl(), APP, APP_PASSWORD).target("10").load().migrate();

            assertThat(provision(database, true).getExitCode()).isZero();
            int executed = DatabaseMigration.run(Map.of("DB_URL", database.getJdbcUrl(),
                    "MIGRATION_DB_USERNAME", MIGRATOR, "MIGRATION_DB_PASSWORD", MIGRATOR_PASSWORD));
            assertThat(executed).isPositive();
            // Provisioning runs on every start of the stack.
            assertThat(provision(database, true).getExitCode()).isZero();

            try (var migrator = connect(database, MIGRATOR, MIGRATOR_PASSWORD)) {
                migrator.createStatement().execute("CREATE TABLE later_table (id int PRIMARY KEY, note text)");
            }
            try (var app = connect(database, APP, APP_PASSWORD); var statement = app.createStatement()) {
                statement.executeQuery("SELECT count(*) FROM users").close();
                statement.executeUpdate("UPDATE users SET email = email WHERE false");
                statement.executeUpdate("DELETE FROM users WHERE false");
                // A table from a later migration is usable without another grant.
                statement.executeUpdate("INSERT INTO later_table VALUES (1, 'a')");
                statement.executeUpdate("UPDATE later_table SET note = 'b'");
                statement.executeUpdate("DELETE FROM later_table");

                for (var ddl : List.of("CREATE TABLE intruder (id int)", "DROP TABLE users",
                        "ALTER TABLE users ADD COLUMN intruder int", "CREATE INDEX intruder ON users (email)",
                        "TRUNCATE users")) {
                    assertThatThrownBy(() -> statement.execute(ddl)).as(ddl)
                            .isInstanceOf(SQLException.class).hasMessageMatching("(?s).*(permission denied|must be owner).*");
                }
            }
            assertThat(owners(database)).containsOnly(MIGRATOR);
        }
    }

    @Test
    void theTwoAccountsMustDiffer() throws Exception {
        try (var database = database()) {
            database.start();
            var result = database.execInContainer("env", "POSTGRES_USER=" + database.getUsername(),
                    "POSTGRES_DB=" + database.getDatabaseName(), "DB_USERNAME=" + APP, "DB_PASSWORD=" + APP_PASSWORD,
                    "MIGRATION_DB_USERNAME=" + APP, "MIGRATION_DB_PASSWORD=" + APP_PASSWORD, "sh", SCRIPT);
            assertThat(result.getExitCode()).isNotZero();
            assertThat(result.getStderr()).contains("must differ");
        }
    }

    @Test
    void migrationNeedsTheMigrationAccount() {
        assertThatThrownBy(() -> DatabaseMigration.run(Map.of("DB_URL", "jdbc:postgresql://localhost/none")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MIGRATION_DB_USERNAME");
    }

    private static PostgreSQLContainer database() {
        return new PostgreSQLContainer("postgres:18-alpine")
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/postgres/01-create-app-user.sh")), SCRIPT);
    }

    private static Container.ExecResult provision(PostgreSQLContainer database, boolean separateAccounts) throws Exception {
        var command = new ArrayList<>(List.of("env", "POSTGRES_USER=" + database.getUsername(),
                "POSTGRES_DB=" + database.getDatabaseName(), "DB_USERNAME=" + APP, "DB_PASSWORD=" + APP_PASSWORD));
        if (separateAccounts) {
            command.add("MIGRATION_DB_USERNAME=" + MIGRATOR);
            command.add("MIGRATION_DB_PASSWORD=" + MIGRATOR_PASSWORD);
        }
        command.addAll(List.of("sh", SCRIPT));
        var result = database.execInContainer(command.toArray(String[]::new));
        assertThat(result.getStderr()).doesNotContain("ERROR");
        return result;
    }

    private static Connection connect(PostgreSQLContainer database, String user, String password) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), user, password);
    }

    private static List<String> owners(PostgreSQLContainer database) throws SQLException {
        try (var admin = connect(database, database.getUsername(), database.getPassword());
             var rows = admin.createStatement().executeQuery(
                     "SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public'")) {
            var owners = new ArrayList<String>();
            while (rows.next()) owners.add(rows.getString(1));
            return owners;
        }
    }
}
