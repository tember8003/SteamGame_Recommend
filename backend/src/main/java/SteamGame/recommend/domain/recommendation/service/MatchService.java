package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.MatchExplanation;
import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.MatchResultDTO;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.ReviewResponseDTO;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.recommendation.util.VolumeBuckets;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.infra.api.GeminiEmbeddingService;
import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import SteamGame.recommend.infra.embedding.TasteWeightedSampler;
import SteamGame.recommend.infra.embedding.VectorSampler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

// 제약 병합부터 랭킹, 폴백 사다리, 설명 조립까지 하나로 엮는 오케스트레이션 서비스임
// 후보는 항상 GameService 의 예산, 코옵, 제외 필터가 걸린 두 풀(nearest, quality)에서만 얻고 다른 경로로 채우지 않음
@Service
public class MatchService {

    // 3차 완화에서 리뷰 하한이 내려갈 수 있는 최저값을 나타냄
    private static final int REVIEW_RELAX_FLOOR = 10;

    private final ConstraintResolver resolver;
    private final GameService gameService;
    private final GeminiEmbeddingService embeddingService;
    private final ExplanationBuilder explanationBuilder;
    private final EmbeddingProperties props;
    private final ReviewRepository reviewRepository;
    private final TagRepository tagRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final EmbeddingRepository embeddingRepository;
    private final FeedbackSuppressionService suppressionService;
    private final GameRepository gameRepository;

    // 운영 기본값은 매 호출 새 Random, 테스트는 useRandom 으로 시드를 주입해 샘플링을 결정적으로 만듦
    private Random random = new Random();

    public MatchService(ConstraintResolver resolver,
                         GameService gameService,
                         GeminiEmbeddingService embeddingService,
                         ExplanationBuilder explanationBuilder,
                         EmbeddingProperties props,
                         ReviewRepository reviewRepository,
                         TagRepository tagRepository,
                         RecommendationFeedbackRepository feedbackRepository,
                         EmbeddingRepository embeddingRepository,
                         FeedbackSuppressionService suppressionService,
                         GameRepository gameRepository) {
        this.resolver = resolver;
        this.gameService = gameService;
        this.embeddingService = embeddingService;
        this.explanationBuilder = explanationBuilder;
        this.props = props;
        this.reviewRepository = reviewRepository;
        this.tagRepository = tagRepository;
        this.feedbackRepository = feedbackRepository;
        this.embeddingRepository = embeddingRepository;
        this.suppressionService = suppressionService;
        this.gameRepository = gameRepository;
    }

    // 같은 패키지 테스트 전용 통로, 운영 경로에서는 호출하지 않음
    void useRandom(Random random) {
        this.random = random;
    }

    public MatchResultDTO recommend(MatchRequestDTO req) {
        RecommendationCriteria resolved = resolver.resolve(req);
        // 싫어요한 appid 는 풀 조회,폴백 세 티어에만 반영하고 응답 criteria 에는 사용자가 보낸 값만 남긴다, 억제로 못 채우면 shortfall 이 그대로 표면화됨
        Set<Long> suppressedExcludes = suppressionService.resolveExcludes(req.getClientId(), resolved.getExcludeAppids());
        RecommendationCriteria criteria = withExcludeAndReview(resolved, resolved.getReview(), suppressedExcludes);
        int need = props.getResultSize();
        int poolK = props.getCandidatePoolK();

        Pool initialPool = resolvePool(criteria, poolK);

        Map<Long, Review> reviewMap = new HashMap<>();
        loadReviews(reviewMap, appidsOfGames(initialPool.games()));

        float[] taste = resolveTasteVector(req.getClientId());
        List<Selected> selected = sampleWithVolumeNudge(initialPool.games(), criteria, need, reviewMap,
                initialPool.moodPath(), taste);

        // 폴백 사다리 전체에서 이미 뽑힌 appid 를 누적해 다음 조회의 excludeAppids 에 반영함
        Set<Long> pickedAppids = selected.stream()
                .map(s -> s.game().getAppid())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (selected.size() < need) {
            RecommendationCriteria expanded = withExcludeAndReview(criteria, criteria.getReview(), mergeExclude(criteria.getExcludeAppids(), pickedAppids));
            List<Game> topupPool = gameService.matchQualityPool(expanded, poolK);
            selected = appendTopup(selected, topupPool, pickedAppids, need, false);
        }

        if (selected.size() < need) {
            int relaxedReview = Math.min(criteria.getReview(), REVIEW_RELAX_FLOOR);
            RecommendationCriteria relaxed = withExcludeAndReview(criteria, relaxedReview, mergeExclude(criteria.getExcludeAppids(), pickedAppids));
            List<Game> relaxedPool = gameService.matchQualityPool(relaxed, poolK);
            selected = appendTopup(selected, relaxedPool, pickedAppids, need, true);
        }

        boolean shortfall = selected.size() < need;

        // 최종 목록 중 아직 리뷰를 못 가져온 appid 만 보충 조회함
        loadReviews(reviewMap, missingAppids(selected, reviewMap));
        Map<Long, List<String>> tagMap = loadTags(appidsOfSelected(selected));

        // 후보마다 가장 비슷한 좋아요 게임 이름을 구해 취향 설명에 실음, 없으면 빈 맵이라 설명이 안 붙음
        List<Game> selectedGames = selected.stream().map(Selected::game).collect(Collectors.toList());
        Map<Long, String> tasteMatches = resolveTasteMatches(req.getClientId(), selectedGames);

        // 설명은 excludeAppids 를 읽지 않아 resolved 로 만들어도 결과가 같음, 응답 criteria 는 억제 전 값으로 되비침
        return buildResult(resolved, selected, reviewMap, tagMap, shortfall, tasteMatches);
    }

    // 빈 요청은 popular, 그 외엔 분위기 임베딩 최근접이 되면 nearest, 안 되면 quality 로 감
    private Pool resolvePool(RecommendationCriteria criteria, int poolK) {
        if (criteria.isEmpty()) {
            return new Pool(gameService.matchQualityPool(criteria, poolK), false);
        }
        if (criteria.hasMood()) {
            float[] qvec = embeddingService.embed(criteria.getMoodText());
            if (qvec != null) {
                List<Game> nearest = gameService.matchNearestPool(qvec, criteria, poolK);
                if (!nearest.isEmpty()) {
                    return new Pool(nearest, true);
                }
            }
        }
        return new Pool(gameService.matchQualityPool(criteria, poolK), false);
    }

    // 세션 길이가 지정되면 리뷰 볼륨 버킷이 맞는 후보를 먼저 샘플하고 모자라면 불일치에서 채움
    // taste 가 있으면 각 버킷을 취향 가중으로 뽑고 없으면 균일로 뽑음
    private List<Selected> sampleWithVolumeNudge(List<Game> pool, RecommendationCriteria criteria, int need,
                                                  Map<Long, Review> reviewMap, boolean moodPath, float[] taste) {
        // 취향이 있을 때만 풀 후보 임베딩을 배치로 읽음, 없으면 빈 맵
        Map<Long, float[]> vecs = (taste == null)
                ? Map.of()
                : embeddingRepository.findEmbeddingsByAppids(appidsOfGames(pool));

        List<Game> chosen;
        if (criteria.getSessionLength() != null) {
            List<Game> matched = new ArrayList<>();
            List<Game> unmatched = new ArrayList<>();
            for (Game game : pool) {
                Review review = reviewMap.get(game.getAppid());
                SessionLength bucket = review == null ? null : VolumeBuckets.classify(review.getPositiveGameHour());
                if (bucket == criteria.getSessionLength()) {
                    matched.add(game);
                } else {
                    unmatched.add(game);
                }
            }
            chosen = new ArrayList<>(pickFromPool(matched, need, taste, vecs, random));
            if (chosen.size() < need) {
                chosen.addAll(pickFromPool(unmatched, need - chosen.size(), taste, vecs, random));
            }
        } else {
            chosen = pickFromPool(pool, need, taste, vecs, random);
        }

        List<Selected> result = new ArrayList<>();
        for (Game game : chosen) {
            result.add(new Selected(game, moodPath, false));
        }
        return result;
    }

    // taste 가 null 이면 균일 VectorSampler, 아니면 취향 가중 TasteWeightedSampler 로 need 개를 뽑음
    private List<Game> pickFromPool(List<Game> bucket, int need, float[] taste,
                                    Map<Long, float[]> vecs, Random rnd) {
        if (taste == null) {
            return VectorSampler.sample(bucket, need, rnd);
        }
        return TasteWeightedSampler.sample(bucket, g -> vecs.get(g.getAppid()), taste, need,
                props.getTasteTau(), rnd);
    }

    // client_id 가 없거나 UP 이 없거나 매칭 임베딩이 없으면 null 을 돌려줌, 호출부는 null 이면 균일 샘플로 감
    private float[] resolveTasteVector(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        List<Long> up = feedbackRepository.findAppidsByClientIdAndSignal(clientId, FeedbackSignal.UP);
        if (up.isEmpty()) {
            return null;
        }
        return embeddingRepository.findMeanEmbeddingByAppids(up);
    }

    // client_id 의 좋아요 게임마다 임베딩 최근접 이웃 top-N 을 구해, 후보가 그 안에 들면 후보 appid 를 그 게임 이름에 매핑함
    // 여러 좋아요에 걸리면 후보가 더 가까운(랭크 작은) 쪽을 지명함
    // 임베딩이 좁은 대역에 몰려 절대 코사인은 변별력이 없으므로 랭크로 거른다, 이웃 쿼리가 seed 를 빼 자기 자신은 지명 안 됨
    // client_id 나 좋아요가 없거나 좋아요 게임에 이웃이 없으면 빈 맵이라 설명이 안 붙음
    private Map<Long, String> resolveTasteMatches(String clientId, List<Game> selected) {
        if (clientId == null || clientId.isBlank() || selected.isEmpty()) {
            return Map.of();
        }
        List<Long> up = feedbackRepository.findAppidsByClientIdAndSignal(clientId, FeedbackSignal.UP);
        if (up.isEmpty()) {
            return Map.of();
        }
        Set<Long> selectedAppids = selected.stream().map(Game::getAppid).collect(Collectors.toSet());
        Map<Long, String> likedNames = new HashMap<>();
        for (Game liked : gameRepository.findByAppidIn(up)) {
            likedNames.put(liked.getAppid(), liked.getName());
        }
        // 좋아요 시드 벡터를 배치로 한 번에 읽음, 좋아요마다 점 조회를 반복하지 않으려는 것임
        Map<Long, String> likedVecs = embeddingRepository.findEmbeddingTextsByAppids(up);

        int k = props.getExplainNeighborK();
        Map<Long, String> matches = new HashMap<>();
        Map<Long, Integer> bestRank = new HashMap<>();
        for (Long liked : up) {
            String name = likedNames.get(liked);
            String vec = likedVecs.get(liked);
            if (name == null || name.isBlank() || vec == null) {
                continue;
            }
            List<Long> neighbors = embeddingRepository.findNeighborAppids(vec, liked, k);
            for (int rank = 0; rank < neighbors.size(); rank++) {
                Long cand = neighbors.get(rank);
                if (!selectedAppids.contains(cand)) {
                    continue;
                }
                Integer prev = bestRank.get(cand);
                if (prev == null || rank < prev) {   // 더 가까운 좋아요를 지명, 동점이면 먼저 나온 것을 유지함
                    bestRank.put(cand, rank);
                    matches.put(cand, name);
                }
            }
        }
        return matches;
    }

    // 후보에서 아직 안 뽑힌 appid 만 need 에 닿을 때까지 채워 넣음, moodPath 는 topup 경로라 항상 거짓임
    private List<Selected> appendTopup(List<Selected> current, List<Game> candidates, Set<Long> pickedAppids,
                                        int need, boolean reviewRelaxed) {
        List<Selected> updated = new ArrayList<>(current);
        for (Game game : candidates) {
            if (updated.size() >= need) {
                break;
            }
            if (pickedAppids.add(game.getAppid())) {
                updated.add(new Selected(game, false, reviewRelaxed));
            }
        }
        return updated;
    }

    private Set<Long> mergeExclude(Set<Long> original, Set<Long> picked) {
        Set<Long> merged = new HashSet<>();
        if (original != null) {
            merged.addAll(original);
        }
        merged.addAll(picked);
        return merged;
    }

    // 리뷰 하한과 제외 appid 만 바꾼 사본을 만듦, 나머지 필터는 원본 그대로 유지해 조건 위반을 막음
    private RecommendationCriteria withExcludeAndReview(RecommendationCriteria c, int review, Set<Long> excludeAppids) {
        return RecommendationCriteria.builder()
                .players(c.getPlayers())
                .sessionLength(c.getSessionLength())
                .budgetCents(c.getBudgetCents())
                .moodText(c.getMoodText())
                .excludeTags(c.getExcludeTags())
                .review(review)
                .korean(c.getKorean())
                .free(c.getFree())
                .excludeAppids(excludeAppids)
                .nlParseFailed(c.isNlParseFailed())
                .build();
    }

    private void loadReviews(Map<Long, Review> reviewMap, List<Long> appids) {
        if (appids.isEmpty()) {
            return;
        }
        for (Review review : reviewRepository.findByAppidIn(appids)) {
            reviewMap.put(review.getAppid(), review);
        }
    }

    private List<Long> missingAppids(List<Selected> selected, Map<Long, Review> reviewMap) {
        List<Long> missing = new ArrayList<>();
        for (Selected s : selected) {
            Long appid = s.game().getAppid();
            if (!reviewMap.containsKey(appid)) {
                missing.add(appid);
            }
        }
        return missing;
    }

    // findGameTagPairs 는 appid, tagName 쌍이라 appid 별로 묶어야 ExplanationBuilder 에 넘길 수 있음
    private Map<Long, List<String>> loadTags(List<Long> appids) {
        Map<Long, List<String>> tagMap = new HashMap<>();
        if (appids.isEmpty()) {
            return tagMap;
        }
        for (Object[] row : tagRepository.findGameTagPairs(appids)) {
            Long appid = ((Number) row[0]).longValue();
            String tagName = (String) row[1];
            tagMap.computeIfAbsent(appid, k -> new ArrayList<>()).add(tagName);
        }
        return tagMap;
    }

    private List<Long> appidsOfGames(List<Game> games) {
        return games.stream().map(Game::getAppid).collect(Collectors.toList());
    }

    private List<Long> appidsOfSelected(List<Selected> selected) {
        return selected.stream().map(s -> s.game().getAppid()).collect(Collectors.toList());
    }

    private MatchResultDTO buildResult(RecommendationCriteria criteria, List<Selected> selected,
                                        Map<Long, Review> reviewMap, Map<Long, List<String>> tagMap,
                                        boolean shortfall, Map<Long, String> tasteMatches) {
        List<SteamAppDTO> games = new ArrayList<>();
        List<ReviewResponseDTO> reviews = new ArrayList<>();
        List<MatchExplanation> explanations = new ArrayList<>();

        int rank = 1;
        for (Selected s : selected) {
            Game game = s.game();
            Review review = reviewMap.get(game.getAppid());
            List<String> tags = tagMap.getOrDefault(game.getAppid(), List.of());

            games.add(GameMapper.convertToSteamDTO(game));
            reviews.add(review != null ? GameMapper.convertToReviewDTO(review) : new ReviewResponseDTO());
            String likedGameName = tasteMatches.get(game.getAppid());
            explanations.add(explanationBuilder.build(game, review, tags, criteria, s.moodPath(), rank, s.reviewRelaxed(), likedGameName));
            rank++;
        }

        return new MatchResultDTO(criteria, games, reviews, explanations, shortfall);
    }

    // 1차 풀과 그 출처(분위기 최근접 여부)를 함께 들고 다니기 위한 내부 표현임
    private record Pool(List<Game> games, boolean moodPath) {
    }

    // 게임 한 건과 그 출처 플래그(분위기 경로, 리뷰 완화 여부)를 함께 들고 다니기 위한 내부 표현임
    private record Selected(Game game, boolean moodPath, boolean reviewRelaxed) {
    }
}
