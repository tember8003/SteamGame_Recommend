package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.GameTag;
import SteamGame.recommend.domain.recommendation.MatchConstants;
import SteamGame.recommend.domain.tag.entity.Tag;
import SteamGame.recommend.infra.embedding.VectorLiterals;
import SteamGame.recommend.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameRepositoryMatchTest extends PostgresTestBase {

    private static final List<String> SENTINEL = List.of("__none__");
    private static final List<String> COOP_TAGS = MatchConstants.COOP_TAGS;

    private static final long A = 400L; // price 999, Co-op, 임베딩이 질의와 동일 방향
    private static final long B = 401L; // price 5999, Co-op
    private static final long C = 402L; // price null, Co-op
    private static final long D = 403L; // free, Singleplayer

    private String qNear;

    @Autowired GameRepository gameRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private Game persistGame(long appid, String name, Integer price, boolean free, int reviewCount) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName(name);
        g.setPrice(price);
        g.setFree(free);
        g.setReviewCount(reviewCount);
        em.persist(g);
        return g;
    }

    private Tag persistTag(String name) {
        Tag t = new Tag();
        t.setName(name);
        em.persist(t);
        return t;
    }

    private void link(Game g, Tag t) {
        GameTag gt = new GameTag();
        gt.setGame(g);
        gt.setTag(t);
        gt.setVotes(1);
        em.persist(gt);
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

    private List<Long> ids(List<Game> games) {
        return games.stream().map(Game::getAppid).collect(Collectors.toList());
    }

    @BeforeEach
    void setUp() {
        Tag coop = persistTag("Co-op");
        Tag singleplayer = persistTag("Singleplayer");

        Game a = persistGame(A, "A-cheap", 999, false, 500);
        Game b = persistGame(B, "B-expensive", 5999, false, 300);
        Game c = persistGame(C, "C-noPrice", null, false, 200);
        Game d = persistGame(D, "D-free", 0, true, 100);
        em.flush();

        link(a, coop);
        link(b, coop);
        link(c, coop);
        link(d, singleplayer);
        em.flush();

        review(A, 100);
        review(B, 100);
        review(C, 100);
        review(D, 100);

        embed(A, 1.0);
        embed(B, 0.0, 1.0);
        embed(C, 0.5, 0.5);
        embed(D, -1.0);

        qNear = vec(1.0);
    }

    @Test
    void budgetCutsAbovePriceButKeepsFree() {
        List<Game> r = gameRepository.findMatchByQuality(0, null, null, 2000, false, SENTINEL,
                List.of(-1L), 0, false, SENTINEL, 50);

        assertThat(ids(r)).contains(A).contains(D).doesNotContain(B).doesNotContain(C);
    }

    @Test
    void nullBudgetKeepsNullPrice() {
        List<Game> r = gameRepository.findMatchByQuality(0, null, null, null, false, SENTINEL,
                List.of(-1L), 0, false, SENTINEL, 50);

        assertThat(ids(r)).contains(C);
    }

    @Test
    void coopFilterRequiresCoopTag() {
        List<Game> r = gameRepository.findMatchByQuality(0, null, null, null, true, COOP_TAGS,
                List.of(-1L), 0, false, SENTINEL, 50);

        assertThat(ids(r)).doesNotContain(D);
    }

    @Test
    void nearestOrdersByEmbeddingDistance() {
        List<Game> r = gameRepository.findMatchNearest(qNear, 0, null, null, null, false, SENTINEL,
                List.of(-1L), 0, false, SENTINEL, 50);

        assertThat(r.get(0).getAppid()).isEqualTo(A);
    }

    @Test
    void qualityOrdersByReviewCountDesc() {
        List<Game> r = gameRepository.findMatchByQuality(0, null, null, null, false, SENTINEL,
                List.of(-1L), 0, false, SENTINEL, 50);

        assertThat(r.get(0).getReviewCount()).isGreaterThanOrEqualTo(r.get(1).getReviewCount());
    }
}
