package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.infra.embedding.VectorLiterals;
import SteamGame.recommend.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameRepositoryVectorTest extends PostgresTestBase {

    private static final List<Long> NO_EXCLUDE = List.of(-1L);
    private static final List<String> NO_TAG = List.of();

    @Autowired GameRepository gameRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private void persistGame(long appid, String name) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName(name);
        g.setReviewCount(100);
        em.persist(g);
    }

    private void review(long appid, int total) {
        jdbc.update("INSERT INTO reviews (appid, total_reviews) VALUES (?, ?)", appid, total);
    }

    // head 성분만 채운 768차원 벡터 리터럴
    private String vec(double... head) {
        float[] v = new float[768];
        for (int i = 0; i < head.length; i++) v[i] = (float) head[i];
        return VectorLiterals.toPgVector(v);
    }

    private void embed(long appid, double... head) {
        jdbc.update("UPDATE games SET embedding = CAST(? AS vector) WHERE appid = ?", vec(head), appid);
    }

    @Test
    void ordersByCosineDistance_andAppliesFilters() {
        persistGame(100L, "A-identical");
        persistGame(101L, "B-close");
        persistGame(102L, "C-orthogonal");
        persistGame(103L, "D-lowReview");
        persistGame(104L, "E-noEmbedding");
        em.flush();

        review(100L, 100);
        review(101L, 100);
        review(102L, 100);
        review(103L, 10);    // 리뷰 미달
        review(104L, 100);

        embed(100L, 1.0);        // 질의와 동일 방향 → 최근접
        embed(101L, 0.9, 0.1);   // 근접
        embed(102L, 0.0, 1.0);   // 직교 → 최원
        embed(103L, 1.0);        // 임베딩은 근접하나 리뷰 미달로 제외
        // 104 는 임베딩 없음 → 제외

        List<Game> result = gameRepository.findNearestByVector(
                vec(1.0), 50, null, null, NO_EXCLUDE, 0, false, NO_TAG, 10);

        assertThat(result).extracting(Game::getAppid).containsExactly(100L, 101L, 102L);
        assertThat(result).extracting(Game::getAppid).doesNotContain(103L, 104L);
    }

    @Test
    void excludeAppids_removesGamesFromResult() {
        persistGame(200L, "A");
        persistGame(201L, "B");
        em.flush();
        review(200L, 100);
        review(201L, 100);
        embed(200L, 1.0);
        embed(201L, 0.9, 0.1);

        List<Game> result = gameRepository.findNearestByVector(
                vec(1.0), 50, null, null, List.of(200L), 1, false, NO_TAG, 10);

        assertThat(result).extracting(Game::getAppid).containsExactly(201L);
    }
}
