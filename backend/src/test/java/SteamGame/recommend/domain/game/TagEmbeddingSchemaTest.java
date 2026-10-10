package SteamGame.recommend.domain.game;

import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TagEmbeddingSchemaTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;

    private String columnType(String table, String column) {
        return jdbc.query("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?""",
                rs -> rs.next() ? rs.getString(1) : null, table, column);
    }

    @Test
    void gamesHasTagEmbeddingColumns() {
        assertThat(columnType("games", "tag_embedding")).isEqualTo("vector");
        assertThat(columnType("games", "tag_embedded_at")).isEqualTo("timestamptz");
    }

    @Test
    void tagEmbeddingHasHnswIndex() {
        String def = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_games_tag_embedding'", String.class);
        assertThat(def).contains("hnsw").contains("vector_cosine_ops");
    }

    @Test
    void impressionSourceAcceptsKnownValuesOnly() {
        assertThat(columnType("recommendation_impression", "source")).isEqualTo("varchar");
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, source) VALUES ('c', 1, 'SIMILAR', 'GNN')");
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, source) VALUES ('c', 2, 'INPUT', NULL)");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO recommendation_impression (client_id, appid, context, source) VALUES ('c', 3, 'SIMILAR', 'OTHER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
