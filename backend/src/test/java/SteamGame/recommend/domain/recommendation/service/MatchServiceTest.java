package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;
import SteamGame.recommend.domain.recommendation.dto.MatchExplanation;
import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.MatchResultDTO;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.infra.api.GeminiEmbeddingService;
import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceTest {

    GameService gameService = mock(GameService.class);
    GeminiEmbeddingService embed = mock(GeminiEmbeddingService.class);
    ConstraintResolver resolver = mock(ConstraintResolver.class);
    ReviewRepository reviewRepository = mock(ReviewRepository.class);
    TagRepository tagRepository = mock(TagRepository.class);
    ExplanationBuilder explanationBuilder = new ExplanationBuilder();
    EmbeddingProperties props = new EmbeddingProperties();
    RecommendationFeedbackRepository feedbackRepository = mock(RecommendationFeedbackRepository.class);
    EmbeddingRepository embeddingRepository = mock(EmbeddingRepository.class);
    FeedbackSuppressionService suppressionService = new FeedbackSuppressionService(feedbackRepository);
    GameRepository gameRepository = mock(GameRepository.class);

    MatchService service = new MatchService(resolver, gameService, embed, explanationBuilder, props,
            reviewRepository, tagRepository, feedbackRepository, embeddingRepository, suppressionService,
            gameRepository);

    @Test
    void usesQualityPathAndFlagsMoodUnavailableWhenEmbeddingNull() {
        when(resolver.resolve(any())).thenReturn(criteriaWithMood("cozy"));
        when(embed.embed(any())).thenReturn(null);
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchResultDTO r = service.recommend(reqWithMood("cozy"));

        assertThat(r.explanations().get(0).caveats()).anyMatch(i -> i.code().equals("MOOD_UNAVAILABLE"));
        verify(gameService, never()).matchNearestPool(any(), any(), anyInt());
    }

    @Test
    void fallbackNeverReturnsConstraintViolatingGame() {
        // nearest 가 2개만 -> quality topup 으로 채움, 두 풀 다 필터된 쿼리라 위반이 불가능함
        when(resolver.resolve(any())).thenReturn(criteriaWithBudgetCoopAndMood());
        when(embed.embed(any())).thenReturn(new float[]{1f});
        when(gameService.matchNearestPool(any(), any(), anyInt())).thenReturn(pool2());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchResultDTO r = service.recommend(reqWithBudgetAndCoop());

        assertThat(r.games()).hasSize(4);
        verify(gameService, atLeastOnce()).matchQualityPool(any(), anyInt());
        verify(gameService, never()).findNonDuplicate(any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void emptyRequestReturnsPopular() {
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchResultDTO r = service.recommend(new MatchRequestDTO());

        assertThat(r.games()).isNotEmpty();
        verify(embed, never()).embed(any());
    }

    @Test
    void shortfallWhenStillUnderNeed() {
        when(resolver.resolve(any())).thenReturn(criteriaWithMood("cozy"));
        when(embed.embed(any())).thenReturn(null);
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool1());

        MatchResultDTO r = service.recommend(reqWithBudgetAndCoop());

        assertThat(r.shortfall()).isTrue();
        assertThat(r.games()).hasSize(1);
    }

    @Test
    void moodPathFlagAppliesOnlyToNearestSourcedGames() {
        // 최종 4개 중 앞 2개는 nearest 출처, 뒤 2개는 quality topup 출처라 뒤쪽에만 MOOD_UNAVAILABLE 이 붙어야 함
        when(resolver.resolve(any())).thenReturn(criteriaWithBudgetCoopAndMood());
        when(embed.embed(any())).thenReturn(new float[]{1f});
        when(gameService.matchNearestPool(any(), any(), anyInt())).thenReturn(pool2());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchResultDTO r = service.recommend(reqWithBudgetAndCoop());

        List<MatchExplanation> explanations = r.explanations();
        assertThat(explanations).hasSize(4);
        assertThat(codes(explanations.get(0).reasons())).contains("MOOD_MATCH");
        assertThat(codes(explanations.get(1).reasons())).contains("MOOD_MATCH");
        assertThat(codes(explanations.get(2).reasons())).doesNotContain("MOOD_MATCH");
        assertThat(codes(explanations.get(2).caveats())).contains("MOOD_UNAVAILABLE");
        assertThat(codes(explanations.get(3).reasons())).doesNotContain("MOOD_MATCH");
    }

    @Test
    void reviewRelaxedFlagAppliesOnlyToThirdTierGames() {
        // 1차 1개, 2차 topup 2개, 3차 완화 topup 으로 마지막 1개를 채워 REVIEW_RELAXED 는 마지막에만 붙어야 함
        RecommendationCriteria criteria = RecommendationCriteria.builder().players(2).review(50).build();
        when(resolver.resolve(any())).thenReturn(criteria);
        when(gameService.matchQualityPool(any(), anyInt()))
                .thenReturn(pool(1, 1L), pool(2, 300L), pool(2, 400L));

        MatchResultDTO r = service.recommend(new MatchRequestDTO());

        assertThat(r.shortfall()).isFalse();
        assertThat(r.games()).hasSize(4);
        List<MatchExplanation> explanations = r.explanations();
        assertThat(codes(explanations.get(0).caveats())).doesNotContain("REVIEW_RELAXED");
        assertThat(codes(explanations.get(1).caveats())).doesNotContain("REVIEW_RELAXED");
        assertThat(codes(explanations.get(2).caveats())).doesNotContain("REVIEW_RELAXED");
        assertThat(codes(explanations.get(3).caveats())).contains("REVIEW_RELAXED");
        verify(gameService, times(3)).matchQualityPool(any(), anyInt());
    }

    @Test
    void volumeNudgePrefersBucketMatchingCandidatesFirst() {
        RecommendationCriteria criteria = RecommendationCriteria.builder().sessionLength(SessionLength.SHORT).build();
        when(resolver.resolve(any())).thenReturn(criteria);

        List<Game> matched = pool(2, 900L);
        List<Game> mismatched = pool(4, 910L);
        List<Game> wholePool = new ArrayList<>();
        wholePool.addAll(matched);
        wholePool.addAll(mismatched);
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(wholePool);

        List<Review> reviews = new ArrayList<>();
        for (Game g : matched) {
            reviews.add(review(g.getAppid(), new BigDecimal("5")));
        }
        for (Game g : mismatched) {
            reviews.add(review(g.getAppid(), new BigDecimal("60")));
        }
        when(reviewRepository.findByAppidIn(any())).thenReturn(reviews);

        // 실제 셔플이 일어나는 불일치 파트만 시드로 고정, 결과 집합 검증에는 순서가 필요 없음
        service.useRandom(new Random(7));

        MatchResultDTO r = service.recommend(new MatchRequestDTO());

        List<Long> resultAppids = r.games().stream().map(SteamAppDTO::getAppid).toList();
        assertThat(resultAppids).hasSize(4);
        assertThat(resultAppids).contains(900L, 901L);
    }

    @Test
    void noClientId_skipsTaste_neverQueriesFeedback() {
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchResultDTO r = service.recommend(new MatchRequestDTO()); // clientId 없음

        assertThat(r.games()).hasSize(4);
        verify(feedbackRepository, never()).findAppidsByClientIdAndSignal(any(), any());
        verify(embeddingRepository, never()).findEmbeddingsByAppids(any());
    }

    @Test
    void emptyUpFeedback_fallsBackToUniform() {
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of());

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(r.games()).hasSize(4);
        verify(embeddingRepository, never()).findMeanEmbeddingByAppids(any());
        verify(embeddingRepository, never()).findEmbeddingsByAppids(any());
    }

    @Test
    void nullMeanEmbeddingOmitsTasteReason() {
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(embeddingRepository.findMeanEmbeddingByAppids(List.of(900L))).thenReturn(null);

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(r.games()).hasSize(4);
        // 평균 임베딩이 null 이면 샘플링은 균일로 떨어지고 좋아요 게임 임베딩도 없어 취향 지명 사유가 안 붙음
        assertThat(r.explanations()).allSatisfy(e ->
                assertThat(codes(e.reasons())).doesNotContain("BECAUSE_YOU_LIKED"));
    }

    @Test
    void tasteBoost_tinyTau_picksNearestCandidate() {
        when(resolver.resolve(any())).thenReturn(emptyCriteria()); // sessionLength 없음 → 전체 풀에서 뽑음
        List<Game> pool = new ArrayList<>();
        pool.add(game(501L));
        pool.add(game(502L));
        pool.add(game(503L));
        pool.add(game(504L));
        pool.add(game(505L));
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool);

        props.setResultSize(1);   // need=1 로 좁혀 결정성 확보
        props.setTasteTau(1e-6);  // 그리디

        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(embeddingRepository.findMeanEmbeddingByAppids(List.of(900L)))
                .thenReturn(new float[]{1f, 0f});
        Map<Long, float[]> vecs = new HashMap<>();
        vecs.put(501L, new float[]{1f, 0f});   // cos 1.0
        vecs.put(502L, new float[]{0f, 1f});   // cos 0.0
        vecs.put(503L, new float[]{-1f, 0f});  // cos -1.0
        // 504, 505 임베딩 없음 → cos 0
        when(embeddingRepository.findEmbeddingsByAppids(any())).thenReturn(vecs);

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        List<Long> ids = r.games().stream().map(SteamAppDTO::getAppid).toList();
        assertThat(ids).containsExactly(501L);
    }

    @Test
    void tasteBoost_resultStaysWithinPool() {
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        List<Game> pool = pool(5, 600L); // 600..604
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool);
        props.setTasteTau(0.25);
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(embeddingRepository.findMeanEmbeddingByAppids(List.of(900L)))
                .thenReturn(new float[]{1f, 0f});
        when(embeddingRepository.findEmbeddingsByAppids(any())).thenReturn(new HashMap<>());
        service.useRandom(new Random(11));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        List<Long> ids = r.games().stream().map(SteamAppDTO::getAppid).toList();
        assertThat(ids).hasSize(4);
        assertThat(List.of(600L, 601L, 602L, 603L, 604L)).containsAll(ids);
    }

    @Test
    void tasteBoost_withSessionLength_picksNearestFromMatchedBucket() {
        // 세션길이 넛지와 취향 가중 합성, matched 버킷 우선 + 그 안에서 취향 최근접이 뽑혀야 함
        RecommendationCriteria criteria = RecommendationCriteria.builder().sessionLength(SessionLength.SHORT).build();
        when(resolver.resolve(any())).thenReturn(criteria);

        List<Game> matched = new ArrayList<>();
        matched.add(game(700L));
        matched.add(game(701L));
        matched.add(game(702L));
        List<Game> mismatched = new ArrayList<>();
        mismatched.add(game(710L));
        mismatched.add(game(711L));
        List<Game> wholePool = new ArrayList<>();
        wholePool.addAll(matched);
        wholePool.addAll(mismatched);
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(wholePool);

        List<Review> reviews = new ArrayList<>();
        for (Game g : matched) {
            reviews.add(review(g.getAppid(), new BigDecimal("5")));    // SHORT 버킷
        }
        for (Game g : mismatched) {
            reviews.add(review(g.getAppid(), new BigDecimal("60")));   // SHORT 아님
        }
        when(reviewRepository.findByAppidIn(any())).thenReturn(reviews);

        props.setResultSize(1);   // need=1 로 좁혀 matched 버킷만으로 채워짐
        props.setTasteTau(1e-6);  // 그리디
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(embeddingRepository.findMeanEmbeddingByAppids(List.of(900L)))
                .thenReturn(new float[]{1f, 0f});
        Map<Long, float[]> vecs = new HashMap<>();
        vecs.put(700L, new float[]{0f, 1f});    // cos 0
        vecs.put(701L, new float[]{1f, 0f});    // cos 1, matched 중 최근접
        vecs.put(702L, new float[]{-1f, 0f});   // cos -1
        vecs.put(710L, new float[]{1f, 0f});    // mismatched 최근접이지만 버킷 우선이라 안 뽑힘
        when(embeddingRepository.findEmbeddingsByAppids(any())).thenReturn(vecs);
        service.useRandom(new Random(3));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        List<Long> ids = r.games().stream().map(SteamAppDTO::getAppid).toList();
        assertThat(ids).containsExactly(701L);
    }

    @Test
    void downFeedbackAppidsAreExcludedFromMatchPool() {
        // 클라이언트가 보낸 제외 999 와 싫어요 777,888 이 풀 조회 제외 집합에 함께 실려야 함
        RecommendationCriteria criteria = RecommendationCriteria.builder()
                .players(2).review(50).excludeAppids(Set.of(999L)).build();
        when(resolver.resolve(any())).thenReturn(criteria);
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.DOWN))
                .thenReturn(List.of(777L, 888L));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        ArgumentCaptor<RecommendationCriteria> captor = ArgumentCaptor.forClass(RecommendationCriteria.class);
        verify(gameService).matchQualityPool(captor.capture(), anyInt());
        assertThat(captor.getValue().getExcludeAppids()).contains(999L, 777L, 888L);
        // 응답의 criteria 는 사용자가 보낸 제외만 되비치고 DOWN 이력은 노출하지 않음
        assertThat(r.criteria().getExcludeAppids()).containsExactly(999L);
    }

    @Test
    void downSuppressionAppliesToNearestPool() {
        // 분위기 임베딩 경로(nearest 풀)에도 DOWN 억제가 걸려야 함
        when(resolver.resolve(any())).thenReturn(criteriaWithMood("cozy"));
        when(embed.embed(any())).thenReturn(new float[]{1f});
        when(gameService.matchNearestPool(any(), any(), anyInt())).thenReturn(pool4());
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.DOWN))
                .thenReturn(List.of(777L));

        MatchRequestDTO req = reqWithMood("cozy");
        req.setClientId("cid");
        service.recommend(req);

        ArgumentCaptor<RecommendationCriteria> captor = ArgumentCaptor.forClass(RecommendationCriteria.class);
        verify(gameService).matchNearestPool(any(), captor.capture(), anyInt());
        assertThat(captor.getValue().getExcludeAppids()).contains(777L);
    }

    @Test
    void downSuppressionPropagatesThroughFallbackTiers() {
        // 1차 1개 -> 2차, 3차 topup 이 돌아 풀 조회가 세 번, 세 조회 모두 DOWN 을 제외해야 함
        RecommendationCriteria criteria = RecommendationCriteria.builder().players(2).review(50).build();
        when(resolver.resolve(any())).thenReturn(criteria);
        when(gameService.matchQualityPool(any(), anyInt()))
                .thenReturn(pool(1, 1L), pool(2, 300L), pool(2, 400L));
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.DOWN))
                .thenReturn(List.of(777L));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        service.recommend(req);

        ArgumentCaptor<RecommendationCriteria> captor = ArgumentCaptor.forClass(RecommendationCriteria.class);
        verify(gameService, times(3)).matchQualityPool(captor.capture(), anyInt());
        assertThat(captor.getAllValues())
                .allSatisfy(c -> assertThat(c.getExcludeAppids()).contains(777L));
    }

    @Test
    void noDownFeedback_leavesPoolExcludesEmpty() {
        // DOWN 이 없으면 제외 집합에 억제로 끼어드는 appid 가 없어야 함
        RecommendationCriteria criteria = RecommendationCriteria.builder().players(2).review(50).build();
        when(resolver.resolve(any())).thenReturn(criteria);
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        service.recommend(req);

        ArgumentCaptor<RecommendationCriteria> captor = ArgumentCaptor.forClass(RecommendationCriteria.class);
        verify(gameService).matchQualityPool(captor.capture(), anyInt());
        assertThat(captor.getValue().getExcludeAppids()).isEmpty();
    }

    @Test
    void tasteNamesCandidateInLikedNeighborsAndOmitsOthers() {
        // 후보 200 은 좋아요 900 의 이웃 top-N 에 들어 지명되고, 이웃이 아닌 201 은 안 붙어야 함
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(List.of(game(200L), game(201L)));
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(gameRepository.findByAppidIn(List.of(900L))).thenReturn(List.of(named(900L, "Hollow Knight")));
        stubLikedVecs(900L);
        when(embeddingRepository.findNeighborAppids(anyString(), eq(900L), anyInt())).thenReturn(List.of(200L));
        service.useRandom(new Random(1));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(reasonCodesFor(r, 200L)).contains("BECAUSE_YOU_LIKED");
        assertThat(itemFor(r, 200L, "BECAUSE_YOU_LIKED").params()).isEqualTo(Map.of("gameName", "Hollow Knight"));
        assertThat(reasonCodesFor(r, 201L)).doesNotContain("BECAUSE_YOU_LIKED");
    }

    @Test
    void tastePicksNearerLikedGameAmongMultiple() {
        // 후보 200 이 두 좋아요의 이웃에 다 들면 랭크가 더 앞선(가까운) 좋아요를 지명함, up 순서와 무관
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(List.of(game(200L)));
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L, 901L));
        when(gameRepository.findByAppidIn(List.of(900L, 901L)))
                .thenReturn(List.of(named(900L, "Far"), named(901L, "Near")));
        stubLikedVecs(900L, 901L);
        when(embeddingRepository.findNeighborAppids(anyString(), eq(900L), anyInt())).thenReturn(List.of(300L, 200L)); // 200 은 랭크 1
        when(embeddingRepository.findNeighborAppids(anyString(), eq(901L), anyInt())).thenReturn(List.of(200L));       // 200 은 랭크 0, 더 가까움
        service.useRandom(new Random(1));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(itemFor(r, 200L, "BECAUSE_YOU_LIKED").params()).isEqualTo(Map.of("gameName", "Near"));
    }

    @Test
    void tasteKeepsFirstSeenLikedGameOnEqualRank() {
        // 후보 200 이 두 좋아요의 같은 랭크에 들면 up 에서 먼저 나온 좋아요를 유지함
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(List.of(game(200L)));
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L, 901L));
        when(gameRepository.findByAppidIn(List.of(900L, 901L)))
                .thenReturn(List.of(named(900L, "First"), named(901L, "Second")));
        stubLikedVecs(900L, 901L);
        when(embeddingRepository.findNeighborAppids(anyString(), eq(900L), anyInt())).thenReturn(List.of(200L)); // 랭크 0
        when(embeddingRepository.findNeighborAppids(anyString(), eq(901L), anyInt())).thenReturn(List.of(200L)); // 같은 랭크 0
        service.useRandom(new Random(1));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(itemFor(r, 200L, "BECAUSE_YOU_LIKED").params()).isEqualTo(Map.of("gameName", "First"));
    }

    @Test
    void tasteQueriesNeighborsWithConfiguredK() {
        // 이웃 조회 개수는 explainNeighborK 설정을 그대로 쓴다
        props.setExplainNeighborK(12);
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(List.of(game(200L)));
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(gameRepository.findByAppidIn(List.of(900L))).thenReturn(List.of(named(900L, "Seed")));
        stubLikedVecs(900L);
        when(embeddingRepository.findNeighborAppids(anyString(), eq(900L), anyInt())).thenReturn(List.of(200L));
        service.useRandom(new Random(1));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        service.recommend(req);

        verify(embeddingRepository).findNeighborAppids(anyString(), eq(900L), eq(12));
    }

    @Test
    void coldStartWithoutUpFeedbackOmitsTasteReason() {
        // 좋아요가 없으면 이웃 조회도 안 하고 어떤 후보에도 지명 사유가 붙지 않음
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(pool4());

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(r.explanations()).allSatisfy(e ->
                assertThat(codes(e.reasons())).doesNotContain("BECAUSE_YOU_LIKED"));
        verify(embeddingRepository, never()).findNeighborAppids(anyString(), anyLong(), anyInt());
    }

    @Test
    void selfMatchDoesNotNameTheGameByItself() {
        // 좋아요한 900 은 이웃 쿼리가 자기를 빼므로 자기 자신을 지명하지 않고, 900 의 이웃인 201 은 900 을 지명함
        when(resolver.resolve(any())).thenReturn(emptyCriteria());
        when(gameService.matchQualityPool(any(), anyInt())).thenReturn(List.of(game(900L), game(201L)));
        when(feedbackRepository.findAppidsByClientIdAndSignal("cid", FeedbackSignal.UP))
                .thenReturn(List.of(900L));
        when(gameRepository.findByAppidIn(List.of(900L))).thenReturn(List.of(named(900L, "Self")));
        stubLikedVecs(900L);
        when(embeddingRepository.findNeighborAppids(anyString(), eq(900L), anyInt())).thenReturn(List.of(201L)); // seed 900 은 쿼리에서 빠짐
        service.useRandom(new Random(1));

        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");
        MatchResultDTO r = service.recommend(req);

        assertThat(reasonCodesFor(r, 900L)).doesNotContain("BECAUSE_YOU_LIKED");
        assertThat(reasonCodesFor(r, 201L)).contains("BECAUSE_YOU_LIKED");
        assertThat(itemFor(r, 201L, "BECAUSE_YOU_LIKED").params()).isEqualTo(Map.of("gameName", "Self"));
    }

    // 좋아요 appid 마다 시드 벡터가 있는 것처럼 보이게 함, 값은 이웃 조회를 스텁으로 덮으니 더미로 둠
    private void stubLikedVecs(Long... appids) {
        Map<Long, String> vecs = new HashMap<>();
        for (Long appid : appids) {
            vecs.put(appid, "[0]");
        }
        when(embeddingRepository.findEmbeddingTextsByAppids(any())).thenReturn(vecs);
    }

    private List<String> codes(List<ExplanationItem> items) {
        return items.stream().map(ExplanationItem::code).toList();
    }

    // 결과에서 특정 appid 의 사유 코드 목록을 뽑음, games 와 explanations 는 같은 순서라 인덱스로 짝지음
    private List<String> reasonCodesFor(MatchResultDTO r, long appid) {
        return codes(r.explanations().get(indexOfAppid(r, appid)).reasons());
    }

    private ExplanationItem itemFor(MatchResultDTO r, long appid, String code) {
        return r.explanations().get(indexOfAppid(r, appid)).reasons().stream()
                .filter(i -> i.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("코드가 없음: " + code));
    }

    private int indexOfAppid(MatchResultDTO r, long appid) {
        List<Long> ids = r.games().stream().map(SteamAppDTO::getAppid).toList();
        int idx = ids.indexOf(appid);
        if (idx < 0) {
            throw new AssertionError("appid 가 결과에 없음: " + appid);
        }
        return idx;
    }

    private Game named(long appid, String name) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName(name);
        g.setReviewCount(100);
        return g;
    }

    private Game game(long appid) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName("Game" + appid);
        g.setReviewCount(100);
        return g;
    }

    private List<Game> pool(int count, long startAppid) {
        List<Game> games = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            games.add(game(startAppid + i));
        }
        return games;
    }

    private List<Game> pool1() {
        return pool(1, 1L);
    }

    private List<Game> pool2() {
        return pool(2, 100L);
    }

    private List<Game> pool4() {
        return pool(4, 200L);
    }

    private Review review(long appid, BigDecimal positiveGameHour) {
        Review r = new Review();
        r.setAppid(appid);
        r.setPositiveGameHour(positiveGameHour);
        return r;
    }

    private RecommendationCriteria criteriaWithMood(String mood) {
        return RecommendationCriteria.builder().moodText(mood).build();
    }

    private RecommendationCriteria criteriaWithBudgetCoopAndMood() {
        return RecommendationCriteria.builder().players(2).budgetCents(2000).moodText("cozy").build();
    }

    private RecommendationCriteria emptyCriteria() {
        return RecommendationCriteria.builder().build();
    }

    private MatchRequestDTO reqWithMood(String mood) {
        MatchRequestDTO req = new MatchRequestDTO();
        req.setMoodTags(List.of(mood));
        return req;
    }

    private MatchRequestDTO reqWithBudgetAndCoop() {
        MatchRequestDTO req = new MatchRequestDTO();
        req.setPlayers(2);
        req.setBudgetUsd(20);
        req.setMoodTags(List.of("cozy"));
        return req;
    }
}
