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

        // Select random games (14 games for 7 rounds)
        List<Game> randomGames = getRandomGamesForChoice(CHOICE_ROUNDS * 2, FIXED_REVIEW_COUNT, KOREAN_CHECK, FREE_CHECK);

        if (randomGames.size() < CHOICE_ROUNDS * 2) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Not enough games found for choice session");
        }

        // Generate rounds (including review information)
        List<ChoiceRoundDTO> rounds = new ArrayList<>();
        for (int i = 0; i < CHOICE_ROUNDS; i++) {
            Game game1 = randomGames.get(i * 2);
            Game game2 = randomGames.get(i * 2 + 1);

            rounds.add(new ChoiceRoundDTO(
                    i,
                    convertToRecommendationResult(game1),  // Including review information
                    convertToRecommendationResult(game2)   // Including review information
            ));
        }

        // Create and save choice session
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
            // 1. Convert to SteamAppDTO
            SteamAppDTO steamAppDTO = GameMapper.convertToSteamDTO(game);

            // 2. Query review information
            List<ReviewResponseDTO> reviews = gameService.ResponseList(List.of(steamAppDTO));

            // 3. Query game tags (optional - can be empty list)
            List<String> gameTags = new ArrayList<>();
            try {
                gameTags = tagRepository.findTagNamesByAppIds(List.of(game.getAppid()));
            } catch (Exception e) {
                log.warn("Failed to get tags for game {}: {}", game.getAppid(), e.getMessage());
            }

            return new RecommendationResultDTO(
                    gameTags,                    // Game tags
                    List.of(steamAppDTO),        // Single game
                    reviews                      // Review information
            );

        } catch (Exception e) {
            log.error("Failed to convert game {} to RecommendationResultDTO: {}", game.getAppid(), e.getMessage());

            // Create with basic information only on failure
            SteamAppDTO steamAppDTO = GameMapper.convertToSteamDTO(game);
            return new RecommendationResultDTO(
                    new ArrayList<>(),           // Empty tag list
                    List.of(steamAppDTO),        // Game information
                    new ArrayList<>()            // Empty review list
            );
        }
    }


    private Map<Long, List<String>> getGameTagsMap(List<Long> gameIds) {
        try {
            // Use batch query method if available from tag repository
            // Here implemented with simple individual queries
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

        // Verify if selected game is valid (extract game ID from RecommendationResultDTO)
        Long game1Id = round.getGame1().getRecommendedGame().get(0).getAppid();
        Long game2Id = round.getGame2().getRecommendedGame().get(0).getAppid();

        if (!selectedGameId.equals(game1Id) && !selectedGameId.equals(game2Id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid game selection");
        }

        // Save selection
        session.getSelections().add(selectedGameId);

        // Check if this is the last round
        boolean isComplete = session.getSelections().size() == CHOICE_ROUNDS;
        session.setCompleted(isComplete);

        saveChoiceSession(session);

        ChoiceSelectionResponseDTO response = new ChoiceSelectionResponseDTO();
        response.setSuccess(true);
        response.setCompleted(isComplete);
        response.setNextRoundIndex(isComplete ? -1 : roundIndex + 1);

        return response;
    }

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

            // Collect tags
            List<String> allTags = collectTagsFromSelectedGames(session.getSelections());

            if (allTags.isEmpty()) {
                log.warn("No tags found, using fallback recommendation");
                return createFallbackResult(sessionId, session.getSelections(), review, koreanCheck, freeCheck);
            }

            // Analyze and optimize tags
            List<String> optimizedTags = analyzeAndOptimizeTags(allTags);
            log.info("Final optimized tags ({}): {}", optimizedTags.size(), optimizedTags);

            // Generate recommended games (robust approach)
            Set<Long> excludedGameIds = new HashSet<>(session.getSelections());
            List<SteamAppDTO> recommendedGames = getRecommendedGamesExcluding(
                    optimizedTags, excludedGameIds, review, koreanCheck, freeCheck);

            log.info("Generated {} recommended games", recommendedGames.size());

            log.info("=== Parameter Debug ===");
            log.info("review: {}", review);
            log.info("koreanCheck: {}", koreanCheck);
            log.info("freeCheck: {}", freeCheck);
            log.info("optimizedTags: {}", optimizedTags);
            log.info("excludedGameIds: {}", excludedGameIds);
            log.info("========================");

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
        // Calculate tag frequency
        Map<String, Long> tagFrequency = allTags.stream()
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        log.info("Tag frequency analysis: {}", tagFrequency);

        // Select top tags (maximum 8)
        List<String> topTags = tagFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("Top {} tags before optimization: {}", topTags.size(), topTags);

        // Optimize tag combinations
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

        // Practical approach: use only up to 6 tags
        int maxTags = Math.min(6, topTags.size());

        try {
            // Try to find optimal tag pairs using Cooccurrence service
            var optimalPair = cooccurrenceService.findOptimalPairKey(topTags, 5);

            if (optimalPair.isPresent()) {
                List<String> result = new ArrayList<>();

                // Add optimal tag pair
                result.add(optimalPair.get().getFirstTag());
                result.add(optimalPair.get().getSecondTag());

                // Carefully add additional tags (up to 6 maximum)
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

        // If Cooccurrence fails: use only top 3-6 tags
        List<String> result = topTags.subList(0, Math.min(3, topTags.size()));
        log.info("Using top {} tags without cooccurrence: {}", result.size(), result);

        return result;
    }

    private List<SteamAppDTO> getRecommendedGamesExcluding(List<String> tags, Set<Long> excludedGameIds,
                                                           int review, Boolean koreanCheck, Boolean freeCheck) {
        log.info("Starting recommendation with {} tags: {}", tags.size(), tags);

        // Strategy 1: Try with original tags (up to 6 maximum)
        List<String> primaryTags = tags.subList(0, Math.min(6, tags.size()));
        List<SteamAppDTO> games = tryRecommendation(primaryTags, excludedGameIds, review, koreanCheck, freeCheck, "primary");
        if (!games.isEmpty()) return games;

        // Strategy 2: Try with top 3 tags
        if (tags.size() >= 3) {
            List<String> topThreeTags = tags.subList(0, 3);
            games = tryRecommendation(topThreeTags, excludedGameIds, review, koreanCheck, freeCheck, "top-3");
            if (!games.isEmpty()) return games;
        }

        // Strategy 3: Try with top 2 tags
        if (tags.size() >= 2) {
            List<String> topTwoTags = tags.subList(0, 2);
            games = tryRecommendation(topTwoTags, excludedGameIds, review, koreanCheck, freeCheck, "top-2");
            if (!games.isEmpty()) return games;
        }

        // Strategy 4: Try with most frequent single tag
        if (!tags.isEmpty()) {
            List<String> singleTag = List.of(tags.get(0));
            games = tryRecommendation(singleTag, excludedGameIds, review, koreanCheck, freeCheck, "single");
            if (!games.isEmpty()) return games;
        }

        // Strategy 5: Try Cooccurrence-based recommendation
        games = tryCooccurrenceRecommendation(tags, excludedGameIds, review, koreanCheck, freeCheck);
        if (!games.isEmpty()) return games;

        // Strategy 6: Use fallback tags
        List<String> fallbackTags = List.of("Singleplayer", "Action");
        games = tryRecommendation(fallbackTags, excludedGameIds, review, koreanCheck, freeCheck, "fallback");
        if (!games.isEmpty()) return games;

        // Strategy 7: Final fallback - relax conditions
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
                    null,
                    excludedGameIds
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

            // Utilize RecommendService logic
            List<SteamAppDTO> games = gameService.findNonDuplicate(
                    new String[]{tags.get(0)}, // Use only first tag
                    review,
                    koreanCheck,
                    freeCheck,
                    false,
                    null,
                    excludedGameIds
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
            // Search games with most relaxed conditions
            List<SteamAppDTO> games = gameService.findNonDuplicate(
                    new String[]{"Singleplayer"},
                    Math.max(10, review / 2), // Relax review count to half
                    koreanCheck,
                    freeCheck,
                    false,
                    null,
                    excludedGameIds
            );

            List<SteamAppDTO> filteredGames = games.stream()
                    .filter(game -> !excludedGameIds.contains(game.getAppid()))
                    .limit(10) // Return at least 10 games
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