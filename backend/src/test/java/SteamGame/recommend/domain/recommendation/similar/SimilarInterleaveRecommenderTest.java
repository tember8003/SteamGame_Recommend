package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.impression.entity.ImpressionSource;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarInterleaveRecommenderTest {

    GameRepository gameRepository = mock(GameRepository.class);
    EmbeddingRepository embeddingRepository = mock(EmbeddingRepository.class);
    EmbeddingProperties embeddingProperties = new EmbeddingProperties();
    SimilarInterleaveRecommender recommender =
            new SimilarInterleaveRecommender(gameRepository, embeddingRepository, embeddingProperties, () -> new Random(7));

    private static Game game(long appid, int reviews) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName("game " + appid);
        g.setReviewCount(reviews);
        return g;
    }

    // 가까울수록 리뷰도 많게 두면 2b 순서가 가까운 순서와 같다
    private static List<Game> nearestWithDescendingReviews(long fromAppid, int count) {
        return LongStream.range(fromAppid, fromAppid + count)
                .mapToObj(id -> game(id, (int) (100_000 - id)))
                .toList();
    }

    private void geminiSeed(List<Game> pool) {
        when(embeddingRepository.findEmbeddingByAppid(1L)).thenReturn(new float[768]);
        when(gameRepository.findNearestByVector(anyString(), anyInt(), any(), any(), anyList(), anyInt(),
                anyBoolean(), anyList(), anyInt())).thenReturn(pool);
    }

    private void gnnSeed(List<Game> nearest) {
        when(embeddingRepository.findTagEmbeddingText(1L)).thenReturn("[0.1]");
        when(gameRepository.findNearestByTagVector(anyString(), anyInt(), any(), any(), anyList(), anyInt(), anyInt()))
                .thenReturn(nearest);
    }

    @Test
    void mixesTwoFromEachSide() {
        geminiSeed(nearestWithDescendingReviews(100, 40));
        gnnSeed(nearestWithDescendingReviews(200, 30));

        List<SteamAppDTO> result = recommender.recommend(1L, "seed", 50, null, null, Set.of(1L));

        assertThat(result).hasSize(4);
        assertThat(result).filteredOn(r -> r.getSource() == ImpressionSource.GEMINI).hasSize(2)
                .allSatisfy(r -> assertThat(r.getAppid()).isBetween(100L, 139L));
        assertThat(result).filteredOn(r -> r.getSource() == ImpressionSource.GNN).hasSize(2)
                .allSatisfy(r -> assertThat(r.getAppid()).isBetween(200L, 209L));
    }

    @Test
    void asksGnnForThirtyCandidatesWithTheSameFilters() {
        geminiSeed(nearestWithDescendingReviews(100, 40));
        gnnSeed(nearestWithDescendingReviews(200, 30));

        recommender.recommend(1L, "seed", 50, true, false, Set.of(1L));

        verify(gameRepository).findNearestByTagVector(eq("[0.1]"), eq(50), eq(true), eq(false),
                eq(List.of(1L)), eq(1), eq(30));
    }

    @Test
    void onlyGeminiWhenSeedHasNoTagVector() {
        geminiSeed(nearestWithDescendingReviews(100, 40));

        List<SteamAppDTO> result = recommender.recommend(1L, "seed", 50, null, null, Set.of(1L));

        assertThat(result).hasSize(4).allSatisfy(r -> assertThat(r.getSource()).isEqualTo(ImpressionSource.GEMINI));
        verify(gameRepository, never()).findNearestByTagVector(anyString(), anyInt(), any(), any(), anyList(), anyInt(), anyInt());
    }

    @Test
    void onlyGnnWhenSeedHasNoGeminiVector() {
        gnnSeed(nearestWithDescendingReviews(200, 30));

        List<SteamAppDTO> result = recommender.recommend(1L, "seed", 50, null, null, Set.of(1L));

        assertThat(result).hasSize(4).allSatisfy(r -> assertThat(r.getSource()).isEqualTo(ImpressionSource.GNN));
    }

    @Test
    void emptyWhenSeedHasNoVectors() {
        assertThat(recommender.recommend(1L, "seed", 50, null, null, Set.of(1L))).isEmpty();
    }

    @Test
    void usesFewerGnnCandidatesWithoutFailing() {
        gnnSeed(nearestWithDescendingReviews(200, 3));

        List<SteamAppDTO> result = recommender.recommend(1L, "seed", 50, null, null, Set.of(1L));

        assertThat(result).extracting(SteamAppDTO::getAppid).containsExactlyInAnyOrder(200L, 201L, 202L);
    }

    // 실험 중인 GNN 쪽 조회가 실패해도 요청 전체가 실패하지 않고 설명문 쪽 결과만 낸다
    @Test
    void gnnFailureFallsBackToGeminiOnly() {
        geminiSeed(nearestWithDescendingReviews(100, 40));
        when(embeddingRepository.findTagEmbeddingText(1L)).thenReturn("[0.1]");
        when(gameRepository.findNearestByTagVector(anyString(), anyInt(), any(), any(), anyList(), anyInt(), anyInt()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        List<SteamAppDTO> result = recommender.recommend(1L, "seed", 50, null, null, Set.of(1L));

        assertThat(result).hasSize(4).allSatisfy(r -> assertThat(r.getSource()).isEqualTo(ImpressionSource.GEMINI));
    }

    // 먼저 고를 쪽은 매번 동전으로 정한다, 늘 한쪽이 먼저 고르면 그쪽이 윗자리를 차지해 비교가 치우친다
    @Test
    void coinFlipDecidesWhoPicksFirst() {
        geminiSeed(nearestWithDescendingReviews(100, 40));
        gnnSeed(nearestWithDescendingReviews(200, 30));

        for (boolean geminiFirst : new boolean[]{true, false}) {
            SimilarInterleaveRecommender forced = new SimilarInterleaveRecommender(
                    gameRepository, embeddingRepository, embeddingProperties, () -> new Random(7) {
                        @Override
                        public boolean nextBoolean() {
                            return geminiFirst;
                        }
                    });

            List<SteamAppDTO> result = forced.recommend(1L, "seed", 50, null, null, Set.of(1L));

            assertThat(result.get(0).getSource())
                    .isEqualTo(geminiFirst ? ImpressionSource.GEMINI : ImpressionSource.GNN);
        }
    }

    // GNN 쪽은 2b 상위 10개 안에서만 고르고, 그 안에서 섞여 매번 다른 게임이 나온다
    @Test
    void gnnPicksStayInTopTenAndVaryAcrossRequests() {
        gnnSeed(nearestWithDescendingReviews(200, 30));
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.Set<Long> firstPicks = new java.util.HashSet<>();

        for (int seed = 0; seed < 200; seed++) {
            int s = seed;
            SimilarInterleaveRecommender seeded = new SimilarInterleaveRecommender(
                    gameRepository, embeddingRepository, embeddingProperties, () -> new Random(s));
            List<SteamAppDTO> result = seeded.recommend(1L, "seed", 50, null, null, Set.of(1L));
            result.forEach(r -> seen.add(r.getAppid()));
            firstPicks.add(result.get(0).getAppid());
        }

        assertThat(seen).allSatisfy(id -> assertThat(id).isBetween(200L, 209L)).hasSizeGreaterThan(4);
        assertThat(firstPicks).hasSizeGreaterThan(1);
    }

    // 빈 제외 집합은 [-1] 과 개수 0 으로 넘겨 NOT IN 을 끈다, 두 쿼리가 같은 필터를 받는다
    @Test
    void bindsSameFiltersAndEmptyExcludeSentinelToBothQueries() {
        geminiSeed(nearestWithDescendingReviews(100, 40));
        gnnSeed(nearestWithDescendingReviews(200, 30));

        recommender.recommend(1L, "seed", 70, true, false, Set.of());

        verify(gameRepository).findNearestByVector(anyString(), eq(70), eq(true), eq(false), eq(List.of(-1L)), eq(0),
                eq(false), eq(List.of()), eq(40));
        verify(gameRepository).findNearestByTagVector(eq("[0.1]"), eq(70), eq(true), eq(false), eq(List.of(-1L)), eq(0),
                eq(30));
    }
}
