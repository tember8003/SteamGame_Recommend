package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.repository.TasteCandidate;
import SteamGame.recommend.domain.taste.repository.TasteCandidateRepository;
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

// 희소도를 곱하면 흔한 태그만 맞는 게임이 드문 태그가 맞는 게임보다 앞서지 않음을 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
class TasteRarityRankingTest extends PostgresTestBase {

    private static final long BASE = 9_300_000L;
    private static final long COMMON_ONLY = BASE + 1;
    private static final long RARE_ONLY = BASE;

    @Autowired TagRarityCache cache;
    @Autowired TasteCandidateRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO tags (name) VALUES ('RK_COMMON'), ('RK_RARE')");
        // 게임 0은 드문 태그만, 게임 1~9는 흔한 태그만 갖는다
        for (int i = 0; i < 10; i++) {
            long appid = BASE + i;
            jdbc.update("INSERT INTO games (appid, name) VALUES (?, ?)", appid, "g" + i);
            jdbc.update("INSERT INTO reviews (appid, total_reviews) VALUES (?, 1000)", appid);
            String tag = i == 0 ? "RK_RARE" : "RK_COMMON";
            jdbc.update("INSERT INTO game_tags (game_id, tag_id, votes) VALUES ((SELECT id FROM games WHERE appid=?), (SELECT id FROM tags WHERE name=?), 1)",
                    appid, tag);
        }
        cache.refresh();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM game_tags WHERE game_id IN (SELECT id FROM games WHERE appid BETWEEN ? AND ?)", BASE, BASE + 99);
        jdbc.update("DELETE FROM reviews WHERE appid BETWEEN ? AND ?", BASE, BASE + 99);
        jdbc.update("DELETE FROM games WHERE appid BETWEEN ? AND ?", BASE, BASE + 99);
        jdbc.update("DELETE FROM tags WHERE name LIKE 'RK_%'");
    }

    private int rank(List<TasteCandidate> list, long appid) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).appid() == appid) {
                return i;
            }
        }
        throw new AssertionError("not found: " + appid);
    }

    private List<TasteCandidate> find(double commonWeight, double rareWeight) {
        return repository.findCandidates(List.of("RK_COMMON", "RK_RARE"), List.of(commonWeight, rareWeight),
                0, null, null, Set.of(), 100);
    }

    @Test
    void rareTagMatchOutranksCommonTagMatchAfterRarityIsApplied() {
        // 흔한 태그의 원래 가중치가 더 높다
        double commonRaw = 1.0;
        double rareRaw = 0.9;

        // 희소도를 곱하지 않으면 흔한 쪽이 앞선다(이 테스트가 희소도 없이는 실패한다는 증거)
        List<TasteCandidate> raw = find(commonRaw, rareRaw);
        assertThat(rank(raw, COMMON_ONLY)).isLessThan(rank(raw, RARE_ONLY));

        List<TasteCandidate> weighted = find(commonRaw * cache.rarity("RK_COMMON"), rareRaw * cache.rarity("RK_RARE"));
        assertThat(rank(weighted, RARE_ONLY)).isLessThan(rank(weighted, COMMON_ONLY));
    }
}
