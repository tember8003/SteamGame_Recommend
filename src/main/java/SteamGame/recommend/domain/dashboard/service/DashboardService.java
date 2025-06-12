package SteamGame.recommend.domain.dashboard.service;

import SteamGame.recommend.domain.dashboard.dto.*;
import SteamGame.recommend.domain.game.dto.GamePlaytimeInfo;
import SteamGame.recommend.domain.game.dto.PlayerStatsInfo;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.exception.DashboardException;
import SteamGame.recommend.exception.SteamApiException;
import SteamGame.recommend.infra.api.SteamApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardService {

    private final SteamApiService steamApiService;
    private final GameRepository gameRepository;
    private final TagRepository tagRepository;
    private final DashboardCacheService cacheService; // Cache service added

    private static final String[] CHART_COLORS = {
            "#00d4ff", "#7c3aed", "#f59e0b", "#10b981", "#ef4444",
            "#8b5cf6", "#06b6d4", "#84cc16", "#f97316", "#ec4899"
    };

    public DashboardService(SteamApiService steamApiService,
                            GameRepository gameRepository,
                            TagRepository tagRepository,
                            DashboardCacheService cacheService) { // Dependency injection
        this.steamApiService = steamApiService;
        this.gameRepository = gameRepository;
        this.tagRepository = tagRepository;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public DashboardDTO generateDashboard(String steamId) {
        // 1. Check cache
        Optional<DashboardDTO> cachedDashboard = cacheService.getCachedDashboard(steamId);
        if (cachedDashboard.isPresent()) {
            log.info("Dashboard returned from cache: steamId={}", steamId);
            return cachedDashboard.get();
        }

        try {
            log.info("Starting new dashboard generation: steamId={}", steamId);

            // 2. Steam API data retrieval
            List<Long> ownedGameIds = Collections.emptyList();
            List<Long> recentGameIds = Collections.emptyList();
            Map<Long, GamePlaytimeInfo> playtimeMap = Collections.emptyMap();
            PlayerStatsInfo playerStats = new PlayerStatsInfo("Unknown", 0L);

            try {
                ownedGameIds = steamApiService.getOwnedGameIds(steamId);
                recentGameIds = steamApiService.getRecentPlayedGameIds(steamId);
                playtimeMap = steamApiService.getDetailedPlaytimes(steamId);
                playerStats = steamApiService.getPlayerStats(steamId);

            } catch (SteamApiException e) {
                log.warn("Partial failure due to Steam API error: steamId={}, type={}, message={}",
                        steamId, e.getClass().getSimpleName(), e.getMessage());

                // Continue with available data instead of empty dashboard on Steam API error
                if (e.getStatusCode() == 403) {
                    // Special message for private profile
                    return createPrivateProfileDashboard(steamId);
                } else if (e.getStatusCode() == 404) {
                    // User not found
                    return createNotFoundDashboard(steamId);
                } else if (e.getStatusCode() == 429) {
                    // Rate limit exceeded - cache for 1 minute
                    DashboardDTO rateLimitDashboard = createRateLimitDashboard(steamId);
                    cacheService.cacheDashboardWithTTL(steamId, rateLimitDashboard, 60);
                    return rateLimitDashboard;
                }
                // Continue for other Steam API errors
            }

            if (ownedGameIds.isEmpty()) {
                DashboardDTO emptyDashboard = createEmptyDashboard(steamId);
                cacheService.cacheDashboard(steamId, emptyDashboard);
                return emptyDashboard;
            }

            // 3. Database query
            List<Game> ownedGames = Collections.emptyList();
            Map<Long, List<String>> gameTagsMap = Collections.emptyMap();

            try {
                ownedGames = gameRepository.findByAppidIn(ownedGameIds);
                gameTagsMap = getGameTagsMap(ownedGameIds);

            } catch (Exception e) {
                log.error("Database query failed: steamId={}", steamId, e);
                throw new DashboardException("Failed to retrieve game data", steamId,
                        DashboardException.ErrorType.DATABASE_ERROR, e);
            }

            // 4. Dashboard generation
            try {
                UserProfileSummary profileSummary = createProfileSummary(ownedGames, recentGameIds, playtimeMap, playerStats);
                List<TagPreferenceDTO> topTags = createTagPreferences(gameTagsMap, ownedGames, playtimeMap);
                List<GenreDistributionDTO> genreDistribution = createGenreDistribution(topTags);
                List<MonthlyActivityDTO> monthlyActivity = createMonthlyActivity(playtimeMap);
                GameLibraryStatsDTO libraryStats = createLibraryStats(ownedGames, gameTagsMap, playtimeMap);
                List<RecommendationInsightDTO> insights = createInsights(topTags, profileSummary);

                DashboardDTO dashboard = new DashboardDTO(
                        steamId,
                        profileSummary,
                        topTags,
                        genreDistribution,
                        monthlyActivity,
                        libraryStats,
                        insights
                );

                // 5. Cache storage
                try {
                    cacheService.cacheDashboard(steamId, dashboard);
                    log.info("New dashboard generation and cache storage completed: steamId={}", steamId);

                } catch (Exception e) {
                    log.warn("Dashboard cache storage failed (data returned normally): steamId={}", steamId, e);
                    // Return data even if cache storage fails
                }

                return dashboard;

            } catch (Exception e) {
                log.error("Dashboard data processing failed: steamId={}", steamId, e);
                throw new DashboardException("Failed to generate dashboard data", steamId,
                        DashboardException.ErrorType.DATA_PROCESSING_ERROR, e);
            }

        } catch (DashboardException e) {
            // Re-throw already processed custom exceptions
            throw e;

        } catch (DataAccessException e) {
            log.error("Database access error: steamId={}", steamId, e);
            DashboardDTO errorDashboard = createDatabaseErrorDashboard(steamId);
            cacheService.cacheDashboardWithTTL(steamId, errorDashboard, 300); // 5 minute cache
            return errorDashboard;

        } catch (Exception e) {
            log.error("Unexpected dashboard generation error: steamId={}", steamId, e);
            DashboardDTO errorDashboard = createUnknownErrorDashboard(steamId);
            cacheService.cacheDashboardWithTTL(steamId, errorDashboard, 60); // 1 minute cache
            return errorDashboard;
        }
    }

    private DashboardDTO createPrivateProfileDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Private", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "PRIVATE_PROFILE",
                        "Profile is private",
                        "If you set your Steam profile to public, we can provide personalized dashboard features.",
                        "Change profile settings",
                        "https://steamcommunity.com/my/edit/settings"
                ))
        );
    }

    /**
     * Dashboard cache invalidation (called when user purchases new games or data updates)
     */
    public void invalidateDashboard(String steamId) {
        cacheService.invalidateDashboard(steamId);
        log.info("Dashboard cache invalidated: steamId={}", steamId);
    }

    /**
     * Check cache status
     */
    public boolean isDashboardCached(String steamId) {
        return cacheService.isCacheValid(steamId);
    }

    /**
     * Force refresh (ignore cache and generate new)
     */
    @Transactional(readOnly = true)
    public DashboardDTO refreshDashboard(String steamId) {
        log.info("Dashboard force refresh: steamId={}", steamId);

        // Invalidate cache
        cacheService.invalidateDashboard(steamId);

        // Generate new
        return generateDashboard(steamId);
    }

    // Existing methods maintained as is...
    private Map<Long, List<String>> getGameTagsMap(List<Long> appIds) {
        if (appIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Object[]> gameTagPairs = tagRepository.findGameTagPairs(appIds);
        Map<Long, List<String>> result = new HashMap<>();

        for (Object[] pair : gameTagPairs) {
            Long appId = (Long) pair[0];
            String tagName = (String) pair[1];
            result.computeIfAbsent(appId, k -> new ArrayList<>()).add(tagName);
        }

        for (Long appId : appIds) {
            result.putIfAbsent(appId, new ArrayList<>());
        }

        return result;
    }

    private UserProfileSummary createProfileSummary(List<Game> ownedGames,
                                                    List<Long> recentGameIds,
                                                    Map<Long, GamePlaytimeInfo> playtimeMap,
                                                    PlayerStatsInfo playerStats) {
        int totalGames = ownedGames.size();

        BigDecimal totalPlaytime = playtimeMap.values().stream()
                .map(GamePlaytimeInfo::getTotalHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int gamesPlayedRecently = recentGameIds.size();
        String favoriteGenre = calculateFavoriteGenreFromPlaytime(playtimeMap,
                getGameTagsMap(playtimeMap.keySet().stream().collect(Collectors.toList())));

        BigDecimal averagePlaytime = totalGames > 0 ?
                totalPlaytime.divide(BigDecimal.valueOf(totalGames), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        return new UserProfileSummary(
                totalGames,
                totalPlaytime,
                gamesPlayedRecently,
                favoriteGenre,
                averagePlaytime
        );
    }

    private List<TagPreferenceDTO> createTagPreferences(Map<Long, List<String>> gameTagsMap,
                                                        List<Game> ownedGames,
                                                        Map<Long, GamePlaytimeInfo> playtimeMap) {
        Map<String, Integer> tagCounts = new HashMap<>();
        Map<String, BigDecimal> tagPlaytimes = new HashMap<>();

        for (Map.Entry<Long, List<String>> entry : gameTagsMap.entrySet()) {
            Long appId = entry.getKey();
            GamePlaytimeInfo playtimeInfo = playtimeMap.get(appId);
            BigDecimal gamePlaytime = playtimeInfo != null ? playtimeInfo.getTotalHours() : BigDecimal.ZERO;

            for (String tag : entry.getValue()) {
                tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
                tagPlaytimes.put(tag, tagPlaytimes.getOrDefault(tag, BigDecimal.ZERO).add(gamePlaytime));
            }
        }

        return tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .map(entry -> {
                    String tag = entry.getKey();
                    int count = entry.getValue();
                    BigDecimal playtime = tagPlaytimes.getOrDefault(tag, BigDecimal.ZERO);
                    double score = Math.min(100, (count * 8) + playtime.doubleValue() / 5);

                    return new TagPreferenceDTO(tag, count, playtime, score);
                })
                .collect(Collectors.toList());
    }

    private List<GenreDistributionDTO> createGenreDistribution(List<TagPreferenceDTO> topTags) {
        List<GenreDistributionDTO> distribution = new ArrayList<>();
        int totalGames = topTags.stream().mapToInt(TagPreferenceDTO::getGameCount).sum();

        for (int i = 0; i < Math.min(topTags.size(), 8); i++) {
            TagPreferenceDTO tag = topTags.get(i);
            double percentage = totalGames > 0 ?
                    (double) tag.getGameCount() / totalGames * 100 : 0;

            distribution.add(new GenreDistributionDTO(
                    tag.getTagName(),
                    tag.getGameCount(),
                    tag.getTotalPlaytime(),
                    Math.round(percentage * 100.0) / 100.0,
                    CHART_COLORS[i % CHART_COLORS.length]
            ));
        }

        return distribution;
    }

    private List<MonthlyActivityDTO> createMonthlyActivity(Map<Long, GamePlaytimeInfo> playtimeMap) {
        List<MonthlyActivityDTO> activity = new ArrayList<>();
        LocalDate now = LocalDate.now();

        BigDecimal recentTotalHours = playtimeMap.values().stream()
                .map(GamePlaytimeInfo::getRecentHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long recentActiveGames = playtimeMap.values().stream()
                .filter(info -> info.getRecentHours().compareTo(BigDecimal.ZERO) > 0)
                .count();

        for (int i = 5; i >= 0; i--) {
            LocalDate month = now.minusMonths(i);
            String monthStr = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            double monthlyFactor = i == 0 ? 2.0 : (1.0 - i * 0.1);

            int estimatedGamesPlayed = (int) Math.max(1, recentActiveGames * monthlyFactor);
            BigDecimal estimatedPlaytime = recentTotalHours.multiply(BigDecimal.valueOf(monthlyFactor));
            int estimatedNewGames = Math.max(0, (int)(Math.random() * 3));

            activity.add(new MonthlyActivityDTO(monthStr, estimatedGamesPlayed, estimatedPlaytime, estimatedNewGames));
        }

        return activity;
    }

    private GameLibraryStatsDTO createLibraryStats(List<Game> ownedGames,
                                                   Map<Long, List<String>> gameTagsMap,
                                                   Map<Long, GamePlaytimeInfo> playtimeMap) {
        int neverPlayed = 0;
        int shortPlayed = 0;
        int mediumPlayed = 0;
        int longPlayed = 0;

        for (Game game : ownedGames) {
            GamePlaytimeInfo playtimeInfo = playtimeMap.get(game.getAppid());
            if (playtimeInfo == null || playtimeInfo.isNeverPlayed()) {
                neverPlayed++;
            } else if (playtimeInfo.isShortPlayed()) {
                shortPlayed++;
            } else if (playtimeInfo.isMediumPlayed()) {
                mediumPlayed++;
            } else {
                longPlayed++;
            }
        }

        List<GameStatsDTO> topPlayed = ownedGames.stream()
                .filter(game -> playtimeMap.containsKey(game.getAppid()))
                .sorted((g1, g2) -> {
                    BigDecimal time1 = playtimeMap.get(g1.getAppid()).getTotalHours();
                    BigDecimal time2 = playtimeMap.get(g2.getAppid()).getTotalHours();
                    return time2.compareTo(time1);
                })
                .limit(5)
                .map(game -> {
                    GamePlaytimeInfo info = playtimeMap.get(game.getAppid());
                    return new GameStatsDTO(
                            game.getAppid(),
                            game.getName(),
                            game.getImageUrl(),
                            info.getTotalHours(),
                            info.getLastPlayedDate(),
                            gameTagsMap.getOrDefault(game.getAppid(), List.of())
                    );
                })
                .collect(Collectors.toList());

        List<GameStatsDTO> recentlyPlayed = ownedGames.stream()
                .filter(game -> playtimeMap.containsKey(game.getAppid()))
                .filter(game -> playtimeMap.get(game.getAppid()).getLastPlayedDate() != null)
                .sorted((g1, g2) -> {
                    LocalDate date1 = playtimeMap.get(g1.getAppid()).getLastPlayedDate();
                    LocalDate date2 = playtimeMap.get(g2.getAppid()).getLastPlayedDate();
                    return date2.compareTo(date1);
                })
                .limit(5)
                .map(game -> {
                    GamePlaytimeInfo info = playtimeMap.get(game.getAppid());
                    return new GameStatsDTO(
                            game.getAppid(),
                            game.getName(),
                            game.getImageUrl(),
                            info.getTotalHours(),
                            info.getLastPlayedDate(),
                            gameTagsMap.getOrDefault(game.getAppid(), List.of())
                    );
                })
                .collect(Collectors.toList());

        return new GameLibraryStatsDTO(
                neverPlayed, shortPlayed, mediumPlayed, longPlayed,
                topPlayed, recentlyPlayed
        );
    }

    private List<RecommendationInsightDTO> createInsights(List<TagPreferenceDTO> topTags,
                                                          UserProfileSummary summary) {
        List<RecommendationInsightDTO> insights = new ArrayList<>();

        if (topTags.size() >= 3) {
            insights.add(new RecommendationInsightDTO(
                    "GENRE_EXPANSION",
                    "Explore new genres",
                    String.format("You mainly play %s games. Try exploring %s genre too!",
                            topTags.get(0).getTagName(), getComplementaryGenre(topTags.get(0).getTagName())),
                    "Get recommendations",
                    "/api/recommend/input"
            ));
        }

        if (summary.getAveragePlaytimePerGame().compareTo(BigDecimal.valueOf(10)) < 0) {
            insights.add(new RecommendationInsightDTO(
                    "PLAYTIME_ANALYSIS",
                    "Immersive game recommendations",
                    "Your average playtime is relatively short. We recommend longer, more immersive games.",
                    "Find long games",
                    "/api/recommend/random"
            ));
        }

        return insights;
    }

    private String calculateFavoriteGenreFromPlaytime(Map<Long, GamePlaytimeInfo> playtimeMap,
                                                      Map<Long, List<String>> gameTagsMap) {
        Map<String, BigDecimal> genrePlaytime = new HashMap<>();

        for (Map.Entry<Long, GamePlaytimeInfo> entry : playtimeMap.entrySet()) {
            Long appId = entry.getKey();
            BigDecimal playtime = entry.getValue().getTotalHours();
            List<String> tags = gameTagsMap.getOrDefault(appId, List.of());

            for (String tag : tags) {
                genrePlaytime.put(tag, genrePlaytime.getOrDefault(tag, BigDecimal.ZERO).add(playtime));
            }
        }

        return genrePlaytime.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    private String getComplementaryGenre(String currentGenre) {
        Map<String, String> complementary = Map.of(
                "Action", "Puzzle",
                "RPG", "Strategy",
                "Simulation", "Action",
                "Strategy", "RPG",
                "Puzzle", "Adventure"
        );
        return complementary.getOrDefault(currentGenre, "Indie");
    }

    private DashboardDTO createEmptyDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "None", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "WELCOME",
                        "Add some games",
                        "No game data available yet. Play some games on Steam and check back!",
                        "Get game recommendations",
                        "/api/recommend/random"
                ))
        );
    }

    private DashboardDTO createNotFoundDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "None", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "USER_NOT_FOUND",
                        "User not found",
                        "Please check if the Steam ID you entered is correct.",
                        "Find Steam ID",
                        "https://steamidfinder.com/"
                ))
        );
    }

    private DashboardDTO createRateLimitDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Paused", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "RATE_LIMIT",
                        "Requests temporarily limited",
                        "Steam API request limit exceeded. Please try again in a moment.",
                        "Refresh later",
                        "#"
                ))
        );
    }

    private DashboardDTO createDatabaseErrorDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Error", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "DATABASE_ERROR",
                        "Temporary system error",
                        "There's a temporary issue with the game database. Please try again in a moment.",
                        "Try again",
                        "#"
                ))
        );
    }

    private DashboardDTO createUnknownErrorDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Error", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "UNKNOWN_ERROR",
                        "Unexpected error",
                        "An unexpected system error occurred. If the problem persists, please contact support.",
                        "Customer support",
                        "/support"
                ))
        );
    }
}