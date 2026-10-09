package SteamGame.recommend.domain.game;

import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class GamesSchemaTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;

    // SteamSpy 를 걷어낸 뒤 채울 출처가 없는 칼럼이라 마이그레이션으로 지움
    @Test
    void gamesTable_hasNoOwnersEstimate() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'games' AND column_name = 'owners_estimate'""",
                Integer.class);

        assertThat(count).isZero();
    }
}
