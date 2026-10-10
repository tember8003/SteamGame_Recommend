package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import SteamGame.recommend.infra.embedding.VectorLiterals;
import SteamGame.recommend.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EmbeddingRepository.class)
class GameRepositoryTagVectorTest extends PostgresTestBase {

    private static final List<Long> NO_EXCLUDE = List.of(-1L);

    @Autowired GameRepository gameRepository;
    @Autowired EmbeddingRepository embeddingRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private void game(long appid, String name, int reviews, boolean korean) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName(name);
        g.setReviewCount(reviews);
        g.setKoreanSupport(korean);
        em.persist(g);
        em.flush();
        jdbc.update("INSERT INTO reviews (appid, total_reviews) VALUES (?, ?)", appid, reviews);
    }

    // head 성분만 채운 320차원 벡터 리터럴
    private String vec(double... head) {
        float[] v = new float[320];
        for (int i = 0; i < head.length; i++) v[i] = (float) head[i];
        return VectorLiterals.toPgVector(v);
    }

    private void tagEmbed(long appid, double... head) {
        jdbc.update("UPDATE games SET tag_embedding = CAST(? AS vector) WHERE appid = ?", vec(head), appid);
    }

    @Test
    void ordersByTagCosineDistanceAndAppliesFilters() {
        game(500L, "Same", 100, true);
        game(501L, "Close", 100, true);
        game(502L, "Far", 100, true);
        game(503L, "FewReviews", 10, true);
        game(504L, "NoTagVector", 100, true);
        game(505L, "NoKorean", 100, false);
        tagEmbed(500L, 1.0);
        tagEmbed(501L, 0.9, 0.1);
        tagEmbed(502L, 0.0, 1.0);
        tagEmbed(503L, 1.0);
        tagEmbed(505L, 1.0);

        List<Game> result = gameRepository.findNearestByTagVector(vec(1.0), 50, true, null, NO_EXCLUDE, 0, 10);

        assertThat(result).extracting(Game::getAppid).containsExactly(500L, 501L, 502L);
    }

    @Test
    void excludesGivenAppids() {
        game(510L, "A", 100, true);
        game(511L, "B", 100, true);
        tagEmbed(510L, 1.0);
        tagEmbed(511L, 0.9, 0.1);

        List<Game> result = gameRepository.findNearestByTagVector(vec(1.0), 50, null, null, List.of(510L), 1, 10);

        assertThat(result).extracting(Game::getAppid).containsExactly(511L);
    }

    @Test
    void readsTagEmbeddingTextOrNull() {
        game(520L, "WithVector", 100, true);
        game(521L, "WithoutVector", 100, true);
        tagEmbed(520L, 1.0);

        assertThat(embeddingRepository.findTagEmbeddingText(520L)).startsWith("[1");
        assertThat(embeddingRepository.findTagEmbeddingText(521L)).isNull();
        assertThat(embeddingRepository.findTagEmbeddingText(999999L)).isNull();
    }
}
