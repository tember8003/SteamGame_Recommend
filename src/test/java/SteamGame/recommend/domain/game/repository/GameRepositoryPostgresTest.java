package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.GameTag;
import SteamGame.recommend.domain.tag.entity.Tag;
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
class GameRepositoryPostgresTest extends PostgresTestBase {

    @Autowired GameRepository gameRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private Game persistGame(long appid, String name, boolean korean, boolean free) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName(name);
        g.setKoreanSupport(korean);
        g.setFree(free);
        g.setReviewCount(100);
        em.persist(g);
        return g;
    }

    private Tag persistTag(String name) {
        Tag t = new Tag();
        t.setName(name);
        em.persist(t);
        return t;
    }

    private void link(Game g, Tag t, int votes) {
        GameTag gt = new GameTag();
        gt.setGame(g);
        gt.setTag(t);
        gt.setVotes(votes);
        em.persist(gt);
    }

    private void persistReview(long appid, int totalReviews) {
        jdbc.update("INSERT INTO reviews (appid, total_reviews) VALUES (?, ?)", appid, totalReviews);
    }

    @Test
    void findRandomGameByTags_returnsGameMatchingSingleTag_onPostgres() {
        Game g = persistGame(10L, "Hades", true, false);
        Tag roguelike = persistTag("Roguelike");
        link(g, roguelike, 4200);
        persistReview(10L, 100);
        em.flush();

        List<Game> result = gameRepository.findRandomGameByTags(
                List.of("Roguelike"), 1L, 50,
                null, null, false, List.of());

        assertThat(result).extracting(Game::getAppid).contains(10L);
    }

    @Test
    void gameTagVotes_arePersistedAndReadable() {
        Game g = persistGame(20L, "Slay the Spire", false, false);
        Tag cardGame = persistTag("Card Game");
        link(g, cardGame, 3100);
        em.flush();
        em.clear();

        Integer votes = jdbc.queryForObject(
                "SELECT votes FROM game_tags gt JOIN games g ON g.id = gt.game_id WHERE g.appid = 20",
                Integer.class);

        assertThat(votes).isEqualTo(3100);
    }
}
