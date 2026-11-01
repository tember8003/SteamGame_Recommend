package SteamGame.recommend.domain.library.share;

import SteamGame.recommend.domain.taste.TasteDbTestConfig;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 다른 DB 테스트와 같은 설정을 써서 스프링 컨텍스트를 새로 만들지 않는다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
class LibraryShareRepositoryTest extends PostgresTestBase {

    @Autowired LibraryShareRepository repository;
    @Autowired JdbcTemplate jdbc;

    private static final String PAYLOAD = "{\"a\":1,\"b\":[\"x\"]}";

    @Test
    void savesAndFindsPayload() {
        repository.insert("id-live", PAYLOAD, Instant.now().plus(1, ChronoUnit.DAYS));
        String found = repository.findActive("id-live").orElseThrow();
        assertThat(found).contains("\"a\"").contains("\"x\"");
    }

    @Test
    void storesPayloadAsJsonb() {
        repository.insert("id-jsonb", PAYLOAD, Instant.now().plus(1, ChronoUnit.DAYS));
        assertThat(jdbc.queryForObject(
                "SELECT jsonb_typeof(payload) FROM library_share WHERE share_id='id-jsonb'", String.class))
                .isEqualTo("object");
    }

    @Test
    void expiredIsNotFound() {
        repository.insert("id-old", PAYLOAD, Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(repository.findActive("id-old")).isEmpty();
    }

    @Test
    void unknownIsNotFound() {
        assertThat(repository.findActive("nope")).isEmpty();
    }

    @Test
    void deleteExpiredRemovesOnlyExpired() {
        repository.insert("id-old2", PAYLOAD, Instant.now().minus(1, ChronoUnit.HOURS));
        repository.insert("id-new2", PAYLOAD, Instant.now().plus(1, ChronoUnit.DAYS));
        int deleted = repository.deleteExpired();
        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_share WHERE share_id='id-old2'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_share WHERE share_id='id-new2'", Integer.class)).isEqualTo(1);
    }
}
