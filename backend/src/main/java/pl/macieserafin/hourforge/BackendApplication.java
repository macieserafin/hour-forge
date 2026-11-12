package pl.macieserafin.hourforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pl.macieserafin.hourforge.config.DatabaseMigration;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        // "migrate": only the database migrations, as a separate deployment step (see DatabaseMigration).
        if (args.length > 0 && DatabaseMigration.COMMAND.equals(args[0])) {
            int executed = DatabaseMigration.run(System.getenv());
            System.out.println("Database migrations applied: " + executed);
            return;
        }
        SpringApplication.run(BackendApplication.class, args);
    }

}
