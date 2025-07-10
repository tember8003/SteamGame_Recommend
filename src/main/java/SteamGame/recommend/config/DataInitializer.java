package SteamGame.recommend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Check if data already exists
        try {
            Integer gameCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class);

            if (gameCount != null && gameCount > 0) {
                log.info("Database already contains {} games. Skipping initialization.", gameCount);
                return;
            }
        } catch (Exception e) {
            log.info("Games table does not exist. Creating schema.");
        }

        log.info("Database is empty. Loading initial data...");

        try {
            // Completely clean existing data (in case there might be leftover data)
            cleanupExistingData();

            // Execute schema
            executeSchemaIfNeeded();

            // Execute data script (with transaction handling)
            executeDataScript();

            // Final verification
            Integer finalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class);
            log.info("Database initialization completed! Total {} games loaded", finalCount);

        } catch (Exception e) {
            log.error("Error occurred during database initialization", e);
            throw e;
        }
    }

    private void cleanupExistingData() {
        try {
            log.info("Cleaning up existing data...");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS FALSE");
            jdbcTemplate.execute("DELETE FROM game_tags");
            jdbcTemplate.execute("DELETE FROM tag_cooccurrence");
            jdbcTemplate.execute("DELETE FROM reviews");
            jdbcTemplate.execute("DELETE FROM tags");
            jdbcTemplate.execute("DELETE FROM games");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS TRUE");
            log.info("Existing data cleanup completed");
        } catch (Exception e) {
            log.warn("Error during existing data cleanup (ignored): {}", e.getMessage());
        }
    }

    private void executeSchemaIfNeeded() throws Exception {
        try {
            // Check if games table exists
            jdbcTemplate.queryForObject("SELECT 1 FROM games LIMIT 1", Integer.class);
            log.info("Tables already exist.");
        } catch (Exception e) {
            log.info("Creating tables...");
            executeScript("schema.sql");
        }
    }

    private void executeDataScript() throws Exception {
        log.info("Inserting game data... (approximately 8000 games, this may take some time)");
        long startTime = System.currentTimeMillis();

        try {
            // H2 configuration - MySQL compatibility mode
            jdbcTemplate.execute("SET MODE MySQL");
            jdbcTemplate.execute("SET AUTOCOMMIT FALSE");

            // In H2, INSERT excluding AUTO_INCREMENT columns is handled automatically
            // No need for separate IDENTITY_INSERT settings

            executeScript("data.sql");

            // Explicit commit
            jdbcTemplate.execute("COMMIT");
            log.info("Transaction commit completed");

        } catch (Exception e) {
            // Rollback on error
            try {
                jdbcTemplate.execute("ROLLBACK");
                log.error("Transaction rolled back");
            } catch (Exception rollbackException) {
                log.error("Error during rollback: {}", rollbackException.getMessage());
            }
            throw e;
        } finally {
            try {
                jdbcTemplate.execute("SET AUTOCOMMIT TRUE");
            } catch (Exception finallyException) {
                log.error("Finally block error: {}", finallyException.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("Data insertion completed! Time taken: {} seconds", (endTime - startTime) / 1000.0);
    }

    private void executeScript(String scriptPath) throws Exception {
        ClassPathResource resource = new ClassPathResource(scriptPath);
        String script = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        );

        // Disable foreign key checks (before executing data.sql)
        if ("data.sql".equals(scriptPath)) {
            log.info("Disabling foreign key checks");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS FALSE");
        }

        // Use Spring Boot's default script execution (safe)
        try {
            log.info("Executing script: {}", scriptPath);

            // Execute script all at once (Spring handles parsing automatically)
            jdbcTemplate.execute(script);

            log.info("{} script execution completed", scriptPath);

        } catch (Exception e) {
            log.error("Script execution failed: {}", scriptPath, e);
            throw e;
        }

        // Restore foreign key checks (after executing data.sql)
        if ("data.sql".equals(scriptPath)) {
            log.info("Restoring foreign key checks");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS TRUE");
        }

        // Verify core table data
        if ("data.sql".equals(scriptPath)) {
            checkCoreTableData();
        }
    }

    private void checkCoreTableData() {
        try {
            Integer games = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class);
            Integer reviews = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class);

            log.info("Core table data verification - games: {} entries, reviews: {} entries", games, reviews);

            if (games == 0) {
                throw new RuntimeException("No data in games table!");
            }
            if (reviews == 0) {
                throw new RuntimeException("No data in reviews table!");
            }

        } catch (Exception e) {
            log.error("Core table data verification failed", e);
            throw new RuntimeException("Core data validation failed", e);
        }
    }
}