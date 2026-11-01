package SteamGame.recommend.domain.taste.repository;

import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import SteamGame.recommend.domain.taste.TasteDbTestConfig;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
class TasteCandidateRepositoryTest extends PostgresTestBase {

    // 다른 테스트 데이터와 겹치지 않게 큰 appid를 쓴다
    private static final long BASE = 9_100_000L;

    @Autowired TasteCandidateRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        game(1, 1000, false, false, "Strategy", "Management");
        game(2, 5000, false, false, "Strategy");
        game(3, 1000, false, false, "Relaxing");
        game(4, 100, false, false, "Strategy", "Management");
        game(5, 1000, false, false, "Cute");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM game_tags WHERE game_id IN (SELECT id FROM games WHERE appid BETWEEN ? AND ?)", BASE, BASE + 99);
        jdbc.update("DELETE FROM reviews WHERE appid BETWEEN ? AND ?", BASE, BASE + 99);
        jdbc.update("DELETE FROM games WHERE appid BETWEEN ? AND ?", BASE, BASE + 99);
        jdbc.update("DELETE FROM tags WHERE name LIKE 'TT_%'");
    }

    private void game(int n, int reviews, boolean korean, boolean free, String... tags) {
        long appid = BASE + n;
        jdbc.update("INSERT INTO games (appid, name, korean_support, is_free) VALUES (?,?,?,?)", appid, "g" + n, korean, free);
        jdbc.update("INSERT INTO reviews (appid, total_reviews) VALUES (?,?)", appid, reviews);
        for (String tag : tags) {
            String name = "TT_" + tag;
            jdbc.update("INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
            jdbc.update("INSERT INTO game_tags (game_id, tag_id, votes) VALUES ((SELECT id FROM games WHERE appid=?), (SELECT id FROM tags WHERE name=?), 1)",
                    appid, name);
        }
    }

    private List<TasteCandidate> find(List<String> tags, List<Double> weights, int review, Boolean korean, Boolean free,
                                      Set<Long> exclude, int k) {
        return repository.findCandidates(tags.stream().map(t -> "TT_" + t).toList(), weights, review, korean, free, exclude, k);
    }

    private List<Long> appids(List<TasteCandidate> list) {
        return list.stream().map(c -> c.appid() - BASE).toList();
    }

    @Test
    void scoresBySumOfWeights() {
        List<TasteCandidate> result = find(List.of("Strategy", "Management"), List.of(1.0, 0.5), 300, null, null, Set.of(), 60);

        assertThat(appids(result)).containsExactly(1L, 2L);
        assertThat(result.get(0).score()).isEqualTo(1.5);
        assertThat(result.get(0).matchedTags()).containsExactly("TT_Strategy", "TT_Management");
    }

    @Test
    void tieBreaksByReviewCount() {
        List<TasteCandidate> result = find(List.of("Strategy"), List.of(1.0), 300, null, null, Set.of(), 60);

        assertThat(appids(result)).containsExactly(2L, 1L);
        assertThat(result.get(0).score()).isEqualTo(1.0);
        assertThat(result.get(1).score()).isEqualTo(1.0);
    }

    @Test
    void excludesZeroMatch() {
        List<TasteCandidate> result = find(List.of("Strategy", "Management"), List.of(1.0, 0.5), 0, null, null, Set.of(), 60);

        assertThat(appids(result)).doesNotContain(5L, 3L);
    }

    @Test
    void appliesReviewFloor() {
        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 300, null, null, Set.of(), 60))).doesNotContain(4L);
        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 0, null, null, Set.of(), 60))).contains(4L);
    }

    @Test
    void appliesExcludeAppids() {
        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 0, null, null, Set.of(BASE + 1), 60))).doesNotContain(1L);
        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 0, null, null, Set.of(), 60))).contains(1L);
    }

    @Test
    void respectsKoreanAndFreeFilters() {
        jdbc.update("UPDATE games SET korean_support = true WHERE appid = ?", BASE + 2);
        jdbc.update("UPDATE games SET is_free = true WHERE appid = ?", BASE + 4);

        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 0, true, null, Set.of(), 60))).containsExactly(2L);
        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 0, null, true, Set.of(), 60))).containsExactly(4L);
        assertThat(appids(find(List.of("Strategy"), List.of(1.0), 0, false, false, Set.of(), 60))).containsExactly(1L);
    }

    @Test
    void excludesZeroScoreGames() {
        assertThat(find(List.of("Strategy"), List.of(0.0), 0, null, null, Set.of(), 60)).isEmpty();
    }

    @Test
    void limitsToK() {
        assertThat(find(List.of("Strategy"), List.of(1.0), 0, null, null, Set.of(), 1)).hasSize(1);
    }
}
