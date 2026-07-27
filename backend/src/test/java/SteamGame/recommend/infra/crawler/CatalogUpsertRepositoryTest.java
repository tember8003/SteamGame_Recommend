package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.GameSeed;
import SteamGame.recommend.infra.crawler.dto.ReviewSeed;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CatalogUpsertRepository.class)
class CatalogUpsertRepositoryTest extends PostgresTestBase {

    @Autowired CatalogUpsertRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upsertGames_insertsThenUpdates_idempotently_withoutTouchingOtherColumns() {
        // 다음 계획 소유 컬럼(description)이 보존되는지 확인하기 위해 미리 넣어둔다
        jdbc.update("INSERT INTO games (appid, name, description) VALUES (570, 'Old Name', 'keep me')");

        repo.upsertGames(List.of(new GameSeed(570L, "Dota 2", "Valve", "Valve", 150000000L, 1450000)));

        assertThat(jdbc.queryForObject("SELECT name FROM games WHERE appid=570", String.class))
                .isEqualTo("Dota 2");
        assertThat(jdbc.queryForObject("SELECT review_count FROM games WHERE appid=570", Integer.class))
                .isEqualTo(1450000);
        // description은 크롤러 소유가 아니므로 보존되어야 한다
        assertThat(jdbc.queryForObject("SELECT description FROM games WHERE appid=570", String.class))
                .isEqualTo("keep me");

        // 재실행(멱등) — 행 수 그대로
        repo.upsertGames(List.of(new GameSeed(570L, "Dota 2", "Valve", "Valve", 150000000L, 1450000)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE appid=570", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void upsertReviews_insertsThenUpdates_idempotently() {
        repo.upsertReviews(List.of(new ReviewSeed(570L, 1450000, new BigDecimal("82.76"))));
        repo.upsertReviews(List.of(new ReviewSeed(570L, 1500000, new BigDecimal("83.00"))));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE appid=570", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=570", Integer.class))
                .isEqualTo(1500000);
    }
}
