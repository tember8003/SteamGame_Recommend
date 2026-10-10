package SteamGame.recommend.domain.recommendation.service;

import org.mockito.ArgumentCaptor;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.impression.entity.ImpressionSource;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.recommendation.similar.RecommendProperties;
import SteamGame.recommend.domain.recommendation.similar.SimilarInterleaveRecommender;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.domain.tag.service.TagService;
import SteamGame.recommend.infra.api.GeminiApiService;
import SteamGame.recommend.infra.api.GeminiEmbeddingService;
import SteamGame.recommend.infra.api.SteamApiService;
import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendServiceImplSimilarTest {

    TagService tagService = mock(TagService.class);
    GameService gameService = mock(GameService.class);
    GameRepository gameRepository = mock(GameRepository.class);
    EmbeddingRepository embeddingRepository = mock(EmbeddingRepository.class);
    SimilarInterleaveRecommender interleaveRecommender = mock(SimilarInterleaveRecommender.class);
    RecommendProperties recommendProperties = new RecommendProperties();
    EmbeddingProperties embeddingProperties = new EmbeddingProperties();

    RecommendServiceImpl service = new RecommendServiceImpl(
            mock(SteamApiService.class), mock(GeminiApiService.class), tagService, mock(TagRepository.class),
            mock(CacheService.class), gameService, gameRepository, mock(CooccurrenceService.class),
            mock(GeminiEmbeddingService.class), embeddingRepository, embeddingProperties,
            recommendProperties, interleaveRecommender);

    private static SteamAppDTO app(long appid, ImpressionSource source) {
        SteamAppDTO dto = new SteamAppDTO();
        dto.setAppid(appid);
        dto.setSource(source);
        return dto;
    }

    @BeforeEach
    void seed() {
        Game seed = new Game();
        seed.setAppid(10L);
        seed.setName("Seed Game");
        when(tagService.getTagsByGame("Seed Game")).thenReturn(List.of("Metroidvania"));
        when(tagService.getTopTags(any(), anyInt())).thenReturn(List.of("Metroidvania"));
        when(gameRepository.findByNameIgnoreCase("Seed Game")).thenReturn(Optional.of(seed));
        when(gameService.ResponseList(any())).thenReturn(List.of());
    }

    @Test
    void flagOffKeepsGeminiPathAndMarksGemini() {
        when(embeddingRepository.findEmbeddingByAppid(10L)).thenReturn(new float[768]);
        List<SteamAppDTO> sampled = new ArrayList<>(List.of(app(1, null), app(2, null), app(3, null), app(4, null)));
        when(gameService.findNearestSampled(any(), anyInt(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt()))
                .thenReturn(sampled);

        RecommendationResultDTO result = service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of());

        assertThat(result.getRecommendedGame()).extracting(SteamAppDTO::getAppid).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result.getRecommendedGame()).allSatisfy(g -> assertThat(g.getSource()).isEqualTo(ImpressionSource.GEMINI));
        verify(interleaveRecommender, never()).recommend(anyLong(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void flagOnUsesInterleavedResultWithSeedExcluded() {
        recommendProperties.setSimilarInterleaveEnabled(true);
        List<SteamAppDTO> mixed = List.of(app(1, ImpressionSource.GEMINI), app(2, ImpressionSource.GNN),
                app(3, ImpressionSource.GEMINI), app(4, ImpressionSource.GNN));
        when(interleaveRecommender.recommend(eq(10L), eq("Seed Game"), eq(50), any(), any(), any())).thenReturn(mixed);

        RecommendationResultDTO result = service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of(99L));

        assertThat(result.getRecommendedGame()).extracting(SteamAppDTO::getSource)
                .containsExactly(ImpressionSource.GEMINI, ImpressionSource.GNN, ImpressionSource.GEMINI, ImpressionSource.GNN);
        verify(interleaveRecommender).recommend(eq(10L), eq("Seed Game"), eq(50), any(), any(), eq(Set.of(99L, 10L)));
    }

    @Test
    void fallsBackToExistingPathWhenInterleaveIsEmpty() {
        recommendProperties.setSimilarInterleaveEnabled(true);
        when(interleaveRecommender.recommend(anyLong(), anyString(), anyInt(), any(), any(), any())).thenReturn(List.of());
        when(embeddingRepository.findEmbeddingByAppid(10L)).thenReturn(new float[768]);
        when(gameService.findNearestSampled(any(), anyInt(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(List.of(app(1, null), app(2, null), app(3, null), app(4, null))));

        RecommendationResultDTO result = service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of());

        assertThat(result.getRecommendedGame()).extracting(SteamAppDTO::getAppid).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result.getRecommendedGame()).allSatisfy(g -> assertThat(g.getSource()).isEqualTo(ImpressionSource.GEMINI));
    }

    private static List<SteamAppDTO> fresh(long... appids) {
        List<SteamAppDTO> out = new ArrayList<>();
        for (long id : appids) out.add(app(id, null));
        return out;
    }

    // 모자란 칸을 채운 게임은 TAG, 섞기 결과는 원래 출처를 지킨다, 채우기는 고른 게임과 제외 집합을 피한다
    @Test
    @SuppressWarnings("unchecked")
    void flagOnTopUpMarksTagAndKeepsMixedSources() {
        recommendProperties.setSimilarInterleaveEnabled(true);
        when(interleaveRecommender.recommend(anyLong(), anyString(), anyInt(), any(), any(), any()))
                .thenReturn(List.of(app(1, ImpressionSource.GNN), app(2, ImpressionSource.GEMINI)));
        when(gameService.findNonDuplicate(any(), anyInt(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(fresh(5, 6));

        RecommendationResultDTO result = service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of(99L));

        assertThat(result.getRecommendedGame()).extracting(SteamAppDTO::getAppid, SteamAppDTO::getSource)
                .containsExactly(tuple(1L, ImpressionSource.GNN), tuple(2L, ImpressionSource.GEMINI),
                        tuple(5L, ImpressionSource.TAG), tuple(6L, ImpressionSource.TAG));
        ArgumentCaptor<Set<Long>> excluded = ArgumentCaptor.forClass(Set.class);
        verify(gameService).findNonDuplicate(any(), anyInt(), any(), any(), anyBoolean(), any(), excluded.capture());
        assertThat(excluded.getValue()).contains(1L, 2L, 10L, 99L);
    }

    @Test
    void flagOffTopUpMarksGeminiThenTag() {
        when(embeddingRepository.findEmbeddingByAppid(10L)).thenReturn(new float[768]);
        when(gameService.findNearestSampled(any(), anyInt(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt()))
                .thenReturn(fresh(1, 2));
        when(gameService.findNonDuplicate(any(), anyInt(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(fresh(5, 6));

        RecommendationResultDTO result = service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of());

        assertThat(result.getRecommendedGame()).extracting(SteamAppDTO::getSource)
                .containsExactly(ImpressionSource.GEMINI, ImpressionSource.GEMINI, ImpressionSource.TAG, ImpressionSource.TAG);
    }

    // 설명문 임베딩이 없으면 태그 동시 출현 경로로 가고 그 결과는 모두 TAG 다
    @Test
    void fallbackWithoutEmbeddingMarksTag() {
        when(gameService.findNonDuplicate(any(), anyInt(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(fresh(7, 8));

        RecommendationResultDTO result = service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of());

        assertThat(result.getRecommendedGame()).extracting(SteamAppDTO::getSource)
                .containsExactly(ImpressionSource.TAG, ImpressionSource.TAG);
    }

    // 시드를 빼지 않으면 고른 게임이 자기 자신을 다시 추천받는다
    @Test
    @SuppressWarnings("unchecked")
    void flagOffExcludesSeedAndCallerIds() {
        when(embeddingRepository.findEmbeddingByAppid(10L)).thenReturn(new float[768]);
        when(gameService.findNearestSampled(any(), anyInt(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt()))
                .thenReturn(fresh(1, 2, 3, 4));

        service.recommendBySimilarGame("Seed Game", 50, null, null, Set.of(99L));

        ArgumentCaptor<Set<Long>> excluded = ArgumentCaptor.forClass(Set.class);
        verify(gameService).findNearestSampled(any(), anyInt(), any(), any(), anyBoolean(), any(), excluded.capture(),
                anyInt(), anyInt());
        assertThat(excluded.getValue()).containsExactlyInAnyOrder(10L, 99L);
    }
}
