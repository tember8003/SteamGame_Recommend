package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

// 태그 구조 최근접 후보를 인기와 섞어 다시 줄 세운다
// 인기만으로 줄 세우면 옆 장르의 초대형 게임이 끌려오므로 유사도 순위를 반 섞는다
public final class GnnRanker {

    private GnnRanker() {}

    // nearest 는 가까운 순서로 받는다
    // 유사도 순위와 인기 순위(리뷰 수 많을수록 1)를 반씩 더해 작은 순서로 정렬하고, 같으면 가까운 쪽을 앞에 둔다
    public static List<Game> rankByPopularityBlend(List<Game> nearest) {
        int n = nearest.size();
        int[] popularityRank = new int[n];
        List<Integer> byPopularity = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingInt((Integer i) -> reviews(nearest.get(i))).reversed()
                        .thenComparingInt(i -> i))
                .toList();
        for (int r = 0; r < n; r++) {
            popularityRank[byPopularity.get(r)] = r + 1;
        }
        return IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> 0.5 * (i + 1) + 0.5 * popularityRank[i])
                        .thenComparingInt(i -> i))
                .map(nearest::get)
                .toList();
    }

    private static int reviews(Game game) {
        return game.getReviewCount() == null ? 0 : game.getReviewCount();
    }
}
