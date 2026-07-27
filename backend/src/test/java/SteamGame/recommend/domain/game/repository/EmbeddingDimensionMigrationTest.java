package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2 마이그레이션의 vector(N) 차원이 EmbeddingProperties 기본 차원과 어긋나면 조기에 잡는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmbeddingDimensionMigrationTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;

    @Test
    void embeddingColumnDimension_matchesConfiguredDefault() {
        String type = jdbc.queryForObject("""
            SELECT format_type(a.atttypid, a.atttypmod)
            FROM pg_attribute a
            WHERE a.attrelid = 'games'::regclass AND a.attname = 'embedding'""",
            String.class);

        int dim = new EmbeddingProperties().getDimension();
        assertThat(type).isEqualTo("vector(" + dim + ")");
    }
}
