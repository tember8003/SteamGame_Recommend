package SteamGame.recommend.domain.choice.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.dto.ReviewResponseDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.recommendation.service.CooccurrenceService;
import SteamGame.recommend.domain.recommendation.service.InMemoryCacheService;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.domain.choice.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GameChoiceService {

    private final GameRepository gameRepository;
    private final TagRepository tagRepository;
    private final CooccurrenceService cooccurrenceService;
    private final GameService gameService;
    private final InMemoryCacheService cacheService;

    private static final int CHOICE_ROUNDS = 7;
    private static final Duration SESSION_TTL = Duration.ofHours(1);
    private static final String SESSION_KEY_PREFIX = "choice:";
    private static final int FIXED_REVIEW_COUNT = 5000;

    public GameChoiceService(GameRepository gameRepository,
                             TagRepository tagRepository,
                             CooccurrenceService cooccurrenceService,
                             GameService gameService,
                             InMemoryCacheService cacheService) {
        this.gameRepository = gameRepository;
        this.tagRepository = tagRepository;
        this.cooccurrenceService = cooccurrenceService;
        this.gameService = gameService;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public ChoiceStartResponseDTO startChoice() {
        final Boolean KOREAN_CHECK = null;
        final Boolean FREE_CHECK = null;

        // 랜덤 게임 선택 (14개 - 7라운드를 위해)
        List<Game> randomGames = getRandomGamesForChoice(CHOICE_ROUNDS * 2, FIXED_REVIEW_COUNT, KOREAN_CHECK, FREE_CHECK);

        if (randomGames.size() < CHOICE_ROUNDS * 2) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Not enough games found for choice session");
        }

        // 라운드 생성 (리뷰 정보 포함)
        List<ChoiceRoundDTO> rounds = new ArrayList<>();
        for (int i = 0; i < CHOICE_ROUNDS; i++) {
            Game game1 = randomGames.get(i * 2);
            Game game2 = randomGames.get(i * 2 + 1);

            rounds.add(new ChoiceRoundDTO(
                    i,
                    convertToRecommendationResult(game1),  // 리뷰 정보 포함
                    convertToRecommendationResult(game2)   // 리뷰 정보 포함
            ));
        }

        // 선택 세션 생성 및 저장
        String sessionId = UUID.randomUUID().toString();
        ChoiceSession session = new ChoiceSession(
                sessionId,
                rounds,
                new ArrayList<>(),
                false
        );

        saveChoiceSession(session);

        return new ChoiceStartResponseDTO(sessionId, rounds);
    }

    private RecommendationResultDTO convertToRecommendationResult(Game game) {
        try {
            // 1. SteamAppDTO 변환
            SteamAppDTO steamAppDTO = GameMapper.convertToSteamDTO(game);

            // 2. 리뷰 정보 조회
            List<ReviewResponseDTO> reviews = gameService.ResponseList(List.of(steamAppDTO));

            // 3. 게임의 태그 조회 (선택사항 - 빈 리스트로도 가능)
            List<String> gameTags = new ArrayList<>();
            try {
                gameTags = tagRepository.findTagNamesByAppIds(List.of(game.getAppid()));
            } catch (Exception e) {
                log.warn("Failed to get tags for game {}: {}", game.getAppid(), e.getMessage());
            }

            return new RecommendationResultDTO(
                    gameTags,                    // 게임의 태그들
                    List.of(steamAppDTO),        // 단일 게임
                    reviews                      // 리뷰 정보
            );

        } catch (Exception e) {
            log.error("Failed to convert game {} to RecommendationResultDTO: {}", game.getAppid(), e.getMessage());

            // 실패 시 기본 정보만으로 생성
            SteamAppDTO steamAppDTO = GameMapper.convertToSteamDTO(game);
            return new RecommendationResultDTO(
                    new ArrayList<>(),           // 빈 태그 리스트
                    List.of(steamAppDTO),        // 게임 정보
                    new ArrayList<>()            // 빈 리뷰 리스트
            );
        }
    }


    private Map<Long, List<String>> getGameTagsMap(List<Long> gameIds) {
        try {
            // 태그 저장소에서 배치로 조회하는 방법이 있다면 사용
            // 여기서는 간단히 개별 조회로 구현
            Map<Long, List<String>> tagMap = new HashMap<>();

            for (Long gameId : gameIds) {
                try {
                    List<String> tags = tagRepository.findTagNamesByAppIds(List.of(gameId));
                    tagMap.put(gameId, tags);
                } catch (Exception e) {
                    log.warn("Failed to get tags for game {}: {}", gameId, e.getMessage());
                    tagMap.put(gameId, new ArrayList<>());
                }
            }

            return tagMap;

        } catch (Exception e) {
            log.error("Failed to get game tags map", e);
            return new HashMap<>();
        }
    }

    public ChoiceSelectionResponseDTO submitSelection(String sessionId, int roundIndex, Long selectedGameId) {
        ChoiceSession session = getChoiceSession(sessionId);

        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Choice session not found");
        }

        if (roundIndex < 0 || roundIndex >= session.getRounds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round index");
        }

        if (session.getSelections().size() != roundIndex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round sequence");
        }

        ChoiceRoundDTO round = session.getRounds().get(roundIndex);

        // 선택된 게임이 유효한지 확인 (RecommendationResultDTO에서 게임 ID 추출)
        Long game1Id = round.getGame1().getRecommendedGame().get(0).getAppid();
        Long game2Id = round.getGame2().getRecommendedGame().get(0).getAppid();

        if (!selectedGameId.equals(game1Id) && !selectedGameId.equals(game2Id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid game selection");
        }

        // 선택 저장
        session.getSelections().add(selectedGameId);

        // 마지막 라운드인지 확인
        boolean isComplete = session.getSelections().size() == CHOICE_ROUNDS;
        session.setCompleted(isComplete);

        saveChoiceSession(session);

        ChoiceSelectionResponseDTO response = new ChoiceSelectionResponseDTO();
        response.setSuccess(true);
        response.setCompleted(isComplete);
        response.setNextRoundIndex(isComplete ? -1 : roundIndex + 1);

        return response;
    }

    @Transactional(readOnly = true, noRollbackFor = {ResponseStatusException.class})
    public ChoiceResultDTO getChoiceResult(String sessionId, int review, Boolean koreanCheck, Boolean freeCheck) {
        ChoiceSession session = getChoiceSession(sessionId);

        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Choice session not found");
        }

        if (!session.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choice session not completed");
        }

        try {
            log.info("Analyzing choice result for {} selected games: {}", session.getSelections().size(), session.getSelections());

            // 태그 수집
            List<String> allTags = collectTagsFromSelectedGames(session.getSelections());

            if (allTags.isEmpty()) {
                log.warn("No tags found, using fallback recommendation");
                return createFallbackResult(sessionId, session.getSelections(), review, koreanCheck, freeCheck);
            }

            // 태그 분석 및 최적화
            List<String> optimizedTags = analyzeAndOptimizeTags(allTags);
            log.info("Final optimized tags ({}): {}", optimizedTags.size(), optimizedTags);

            // 추천 게임 생성 (강건한 방식)
            Set<Long> excludedGameIds = new HashSet<>(session.getSelections());
            List<SteamAppDTO> recommendedGames = getRecommendedGamesExcluding(
                    optimizedTags, excludedGameIds, review, koreanCheck, freeCheck);

            log.info("Generated {} recommended games", recommendedGames.size());

            return new ChoiceResultDTO(
                    sessionId,
                    optimizedTags,
                    recommendedGames,
                    session.getSelections()
            );

        } catch (Exception e) {
            log.error("Error in getChoiceResult, falling back to basic recommendation", e);
            return createFallbackResult(sessionId, session.getSelections(), review, koreanCheck, freeCheck);
        }
    }

    private List<String> collectTagsFromSelectedGames(List<Long> selectedGameIds) {
        try {
            List<String> allTags = tagRepository.findTagNamesByAppIds(selectedGameIds);
            log.info("Collected {} total tags from selected games", allTags.size());
            return allTags;
        } catch (Exception e) {
            log.warn("Bulk tag collection failed, trying individual queries", e);

            List<String> allTags = new ArrayList<>();
            for (Long gameId : selectedGameIds) {
                try {
                    List<String> individualTags = tagRepository.findTagNamesByAppIds(List.of(gameId));
                    allTags.addAll(individualTags);
                } catch (Exception ex) {
                    log.warn("Failed to get tags for game {}: {}", gameId, ex.getMessage());
                }
            }
            return allTags;
        }
    }

    private List<String> analyzeAndOptimizeTags(List<String> allTags) {
        // 태그 빈도 계산
        Map<String, Long> tagFrequency = allTags.stream()
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        log.info("Tag frequency analysis: {}", tagFrequency);

        // 상위 태그 선별 (최대 8개)
        List<String> topTags = tagFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("Top {} tags before optimization: {}", topTags.size(), topTags);

        // 태그 조합 최적화
        return findOptimalTagCombination(topTags);
    }

    private ChoiceResultDTO createFallbackResult(String sessionId, List<Long> selectedGameIds,
                                                 int review, Boolean koreanCheck, Boolean freeCheck) {
        log.info("Creating fallback result");

        List<String> fallbackTags = List.of("Singleplayer", "Action");
        Set<Long> excludedGameIds = new HashSet<>(selectedGameIds);

        List<SteamAppDTO> fallbackGames = tryRecommendation(
                fallbackTags, excludedGameIds, review, koreanCheck, freeCheck, "fallback"
        );

        return new ChoiceResultDTO(
                sessionId,
                fallbackTags,
                fallbackGames,
                selectedGameIds
        );
    }

    public ChoiceStatusDTO getChoiceStatus(String sessionId) {
        ChoiceSession session = getChoiceSession(sessionId);

        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Choice session not found");
        }

        return new ChoiceStatusDTO(
                sessionId,
                session.getSelections().size(),
                CHOICE_ROUNDS,
                session.isCompleted()
        );
    }

    private List<Game> getRandomGamesForChoice(int count, int review, Boolean koreanCheck, Boolean freeCheck) {
        try {
            List<Game> allGames = getAllValidGames(review, koreanCheck, freeCheck);

            log.info("Found {} total games for choice", allGames.size());

            if (allGames.size() <= count) {
                return allGames;
            }

            Collections.shuffle(allGames);
            return allGames.subList(0, count);

        } catch (Exception e) {
            log.error("Error getting games for choice", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve games for choice session");
        }
    }

    private List<Game> getAllValidGames(int review, Boolean koreanCheck, Boolean freeCheck) {
        try {
            return gameRepository.findRandomGamesWithoutTags(review, koreanCheck, freeCheck);
        } catch (Exception e) {
            log.warn("Failed to get games with new method, trying fallback", e);
            return gameRepository.findRandomGameByTags(
                    List.of("Singleplayer"),
                    1,
                    review,
                    koreanCheck,
                    freeCheck,
                    false,
                    List.of()
            );
        }
    }

    private ChoiceGameDTO convertToChoiceGame(Game game) {
        return new ChoiceGameDTO(
                game.getAppid(),
                game.getName(),
                game.getImageUrl(),
                game.getDescription()
        );
    }

    private List<String> findOptimalTagCombination(List<String> topTags) {
        log.info("Finding optimal tag combination from {} tags: {}", topTags.size(), topTags);

        // 실용적 접근: 최대 6개 태그까지만 사용
        int maxTags = Math.min(6, topTags.size());

        try {
            // Cooccurrence 서비스로 최적의 태그 쌍 찾기 시도
            var optimalPair = cooccurrenceService.findOptimalPairKey(topTags, 5);

            if (optimalPair.isPresent()) {
                List<String> result = new ArrayList<>();

                // 최적 태그 쌍 추가
                result.add(optimalPair.get().getFirstTag());
                result.add(optimalPair.get().getSecondTag());

                // 추가 태그를 조심스럽게 추가 (최대 6개까지)
                for (String tag : topTags) {
                    if (!result.contains(tag) && result.size() < maxTags) {
                        result.add(tag);
                    }
                }

                log.info("Cooccurrence optimization successful. Final tags ({}): {}", result.size(), result);
                return result;
            }
        } catch (Exception e) {
            log.warn("Cooccurrence optimization failed: {}", e.getMessage());
        }

        // Cooccurrence 실패 시: 상위 3-6개 태그만 사용
        List<String> result = topTags.subList(0, Math.min(3, topTags.size()));
        log.info("Using top {} tags without cooccurrence: {}", result.size(), result);

        return result;
    }

    private List<SteamAppDTO> getRecommendedGamesExcluding(List<String> tags, Set<Long> excludedGameIds,
                                                           int review, Boolean koreanCheck, Boolean freeCheck) {
        log.info("Starting recommendation with {} tags: {}", tags.size(), tags);

        // 전략 1: 원본 태그로 시도 (최대 6개까지만)
        List<String> primaryTags = tags.subList(0, Math.min(6, tags.size()));
        List<SteamAppDTO> games = tryRecommendation(primaryTags, excludedGameIds, review, koreanCheck, freeCheck, "primary");
        if (!games.isEmpty()) return games;

        // 전략 2: 상위 3개 태그로 시도
        if (tags.size() >= 3) {
            List<String> topThreeTags = tags.subList(0, 3);
            games = tryRecommendation(topThreeTags, excludedGameIds, review, koreanCheck, freeCheck, "top-3");
            if (!games.isEmpty()) return games;
        }

        // 전략 3: 상위 2개 태그로 시도
        if (tags.size() >= 2) {
            List<String> topTwoTags = tags.subList(0, 2);
            games = tryRecommendation(topTwoTags, excludedGameIds, review, koreanCheck, freeCheck, "top-2");
            if (!games.isEmpty()) return games;
        }

        // 전략 4: 가장 빈도 높은 단일 태그로 시도
        if (!tags.isEmpty()) {
            List<String> singleTag = List.of(tags.get(0));
            games = tryRecommendation(singleTag, excludedGameIds, review, koreanCheck, freeCheck, "single");
            if (!games.isEmpty()) return games;
        }

        // 전략 5: Cooccurrence 기반 추천 시도
        games = tryCooccurrenceRecommendation(tags, excludedGameIds, review, koreanCheck, freeCheck);
        if (!games.isEmpty()) return games;

        // 전략 6: 폴백 태그 사용
        List<String> fallbackTags = List.of("Singleplayer", "Action");
        games = tryRecommendation(fallbackTags, excludedGameIds, review, koreanCheck, freeCheck, "fallback");
        if (!games.isEmpty()) return games;

        // 전략 7: 최종 폴백 - 조건 완화
        log.warn("All recommendation strategies failed, trying with relaxed conditions");
        return tryRelaxedRecommendation(excludedGameIds, review, koreanCheck, freeCheck);
    }

    private List<SteamAppDTO> tryRecommendation(List<String> tags, Set<Long> excludedGameIds,
                                                int review, Boolean koreanCheck, Boolean freeCheck, String strategy) {
        try {
            log.info("Trying {} strategy with tags: {}", strategy, tags);

            List<SteamAppDTO> allGames = gameService.findNonDuplicate(
                    tags.toArray(new String[0]),
                    review,
                    koreanCheck,
                    freeCheck,
                    false,
                    null
            );

            List<SteamAppDTO> filteredGames = allGames.stream()
                    .filter(game -> !excludedGameIds.contains(game.getAppid()))
                    .collect(Collectors.toList());

            log.info("{} strategy found {} games (after exclusion: {})",
                    strategy, allGames.size(), filteredGames.size());

            return filteredGames;

        } catch (Exception e) {
            log.warn("{} strategy failed: {}", strategy, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SteamAppDTO> tryCooccurrenceRecommendation(List<String> tags, Set<Long> excludedGameIds,
                                                            int review, Boolean koreanCheck, Boolean freeCheck) {
        try {
            log.info("Trying cooccurrence-based recommendation");

            // RecommendService의 로직 활용
            List<SteamAppDTO> games = gameService.findNonDuplicate(
                    new String[]{tags.get(0)}, // 첫 번째 태그만 사용
                    review,
                    koreanCheck,
                    freeCheck,
                    false,
                    null
            );

            List<SteamAppDTO> filteredGames = games.stream()
                    .filter(game -> !excludedGameIds.contains(game.getAppid()))
                    .collect(Collectors.toList());

            log.info("Cooccurrence strategy found {} games", filteredGames.size());
            return filteredGames;

        } catch (Exception e) {
            log.warn("Cooccurrence strategy failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SteamAppDTO> tryRelaxedRecommendation(Set<Long> excludedGameIds,
                                                       int review, Boolean koreanCheck, Boolean freeCheck) {
        try {
            // 가장 완화된 조건으로 게임 검색
            List<SteamAppDTO> games = gameService.findNonDuplicate(
                    new String[]{"Singleplayer"},
                    Math.max(10, review / 2), // 리뷰 수 절반으로 완화
                    koreanCheck,
                    freeCheck,
                    false,
                    null
            );

            List<SteamAppDTO> filteredGames = games.stream()
                    .filter(game -> !excludedGameIds.contains(game.getAppid()))
                    .limit(10) // 최소 10개라도 반환
                    .collect(Collectors.toList());

            log.info("Relaxed strategy found {} games", filteredGames.size());
            return filteredGames;

        } catch (Exception e) {
            log.error("Even relaxed strategy failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveChoiceSession(ChoiceSession session) {
        String key = SESSION_KEY_PREFIX + session.getSessionId();
        cacheService.setObject(key, session, SESSION_TTL);
    }

    private ChoiceSession getChoiceSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        return cacheService.getObject(key, ChoiceSession.class).orElse(null);
    }
}