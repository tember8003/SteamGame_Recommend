package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GnnRankerTest {

    private static Game game(long appid, Integer reviews) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName("g" + appid);
        g.setReviewCount(reviews);
        return g;
    }

    // 유사도 순위 A1 B2 C3, 인기 순위 B1 C2 A3 → 점수 A2.0 B1.5 C2.5
    @Test
    void blendsSimilarityRankAndPopularityRank() {
        List<Game> nearest = List.of(game(1, 10), game(2, 1000), game(3, 500));

        assertThat(GnnRanker.rankByPopularityBlend(nearest)).extracting(Game::getAppid).containsExactly(2L, 1L, 3L);
    }

    // 세 점수가 모두 2.0 으로 같으면 가까운 순서를 지킨다
    @Test
    void tieKeepsSimilarityOrder() {
        List<Game> nearest = List.of(game(1, 10), game(2, 50), game(3, 1000));

        assertThat(GnnRanker.rankByPopularityBlend(nearest)).extracting(Game::getAppid).containsExactly(1L, 2L, 3L);
    }

    // null 을 0 으로 보면 인기 순위 2번 1, 3번 2, 1번 3 → 점수 1번 2.0, 2번 1.5, 3번 2.5
    // null 을 가장 많은 쪽으로 잘못 보면 1, 2, 3 순서가 나와 구분된다
    @Test
    void missingReviewCountCountsAsZero() {
        List<Game> nearest = List.of(game(1, null), game(2, 5), game(3, 3));

        assertThat(GnnRanker.rankByPopularityBlend(nearest)).extracting(Game::getAppid).containsExactly(2L, 1L, 3L);
    }

    @Test
    void emptyStaysEmpty() {
        assertThat(GnnRanker.rankByPopularityBlend(List.of())).isEmpty();
    }
}
