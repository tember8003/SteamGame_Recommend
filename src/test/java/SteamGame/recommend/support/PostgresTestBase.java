package SteamGame.recommend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton-container base for DB tests.
 *
 * <p>The Postgres container is started ONCE per test JVM in a static initializer and is
 * deliberately never stopped — Testcontainers' Ryuk reaper removes it at JVM exit. This
 * replaces the per-class {@code @Testcontainers}/{@code @Container} lifecycle, which stopped
 * the shared {@code static} container after the first subclass finished and left every later
 * DB test class failing with {@code CannotGetJdbcConnectionException}. All subclasses share
 * this one container; each cleans up the rows it writes.
 */
public abstract class PostgresTestBase {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // pgvector 확장이 포함된 이미지 사용 (V2 CREATE EXTENSION vector 가 stock postgres:16 에선 실패)
        POSTGRES = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres"));
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
