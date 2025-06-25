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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardService {

    private final SteamApiService steamApiService;
    private final GameRepository gameRepository;
    private final TagRepository tagRepository;
    private final DashboardCacheService cacheService;

    private static final String[] CHART_COLORS = {
            "#00d4ff", "#7c3aed", "#f59e0b", "#10b981", "#ef4444",
            "#8b5cf6", "#06b6d4", "#84cc16", "#f97316", "#ec4899"
    };

    public DashboardService(SteamApiService steamApiService,
                            GameRepository gameRepository,
                            TagRepository tagRepository,
                            DashboardCacheService cacheService) {
        this.steamApiService = steamApiService;
        this.gameRepository = gameRepository;
        this.tagRepository = tagRepository;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public DashboardDTO generateDashboard(String steamId) {
        // check Cache
        Optional<DashboardDTO> cachedDashboard = cacheService.getCachedDashboard(steamId);
        if (cachedDashboard.isPresent()) {
            log.info("Dashboard returned from cache: steamId={}", steamId);
            return cachedDashboard.get();
        }

        try {
            log.info("Starting new dashboard generation: steamId={}", steamId);

            // Steam api data
            List<Long> ownedGameIds = Collections.emptyList();
            List<Long> recentGameIds = Collections.emptyList();
            Map<Long, GamePlaytimeInfo> playtimeMap = Collections.emptyMap();
            PlayerStatsInfo playerStats = new PlayerStatsInfo("Unknown", 0L);

            // get Game list
            try {
                ownedGameIds = steamApiService.getOwnedGameIds(steamId);
            } catch (SteamApiException e) {
                log.warn("Failed to get owned games: steamId={}, statusCode={}, message={}",
                        steamId, e.getStatusCode(), e.getMessage());

                if (e.getStatusCode() == 403) {
                    return createPrivateProfileDashboard(steamId);
                } else if (e.getStatusCode() == 404) {
                    return createNotFoundDashboard(steamId);
                } else if (e.getStatusCode() == 429) {
                    DashboardDTO rateLimitDashboard = createRateLimitDashboard(steamId);
                    cacheService.cacheDashboardWithTTL(steamId, rateLimitDashboard, 60);
                    return rateLimitDashboard;
                }

            }

            // Recent Played Games
            try {
                recentGameIds = steamApiService.getRecentPlayedGameIds(steamId);
            } catch (SteamApiException e) {
                log.warn("Failed to get recent games (continuing with empty): steamId={}, message={}",
                        steamId, e.getMessage());
                recentGameIds = Collections.emptyList();
            } catch (Exception e) {
                log.warn("Unexpected error getting recent games: steamId={}", steamId, e);
                recentGameIds = Collections.emptyList();
            }

            // Playtime Info
            try {
                playtimeMap = steamApiService.getDetailedPlaytimes(steamId);
            } catch (SteamApiException e) {
                log.warn("Failed to get playtime data (continuing with empty): steamId={}, message={}",
                        steamId, e.getMessage());
                playtimeMap = Collections.emptyMap();
            } catch (Exception e) {
                log.warn("Unexpected error getting playtime data: steamId={}", steamId, e);
                playtimeMap = Collections.emptyMap();
            }

            // Player stats
            try {
                playerStats = steamApiService.getPlayerStats(steamId);
            } catch (SteamApiException e) {
                log.warn("Failed to get player stats (continuing with default): steamId={}, statusCode={}, message={}",
                        steamId, e.getStatusCode(), e.getMessage());

                // when 429 error
                if (e.getStatusCode() == 429) {
                    DashboardDTO rateLimitDashboard = createRateLimitDashboard(steamId);
                    cacheService.cacheDashboardWithTTL(steamId, rateLimitDashboard, 300); // 5분 TTL
                    return rateLimitDashboard;
                }

                playerStats = new PlayerStatsInfo("Unknown", 0L);
            } catch (Exception e) {
                log.warn("Unexpected error getting player stats: steamId={}", steamId, e);
                playerStats = new PlayerStatsInfo("Unknown", 0L);
            }

            // Empty dashboard (if User data is empty)
            if (ownedGameIds.isEmpty()) {
                DashboardDTO emptyDashboard = createEmptyDashboard(steamId);
                cacheService.cacheDashboard(steamId, emptyDashboard);
                return emptyDashboard;
            }

            // Database query
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

            // create Data
            try {
                UserProfileSummary profileSummary = createProfileSummary(ownedGames, recentGameIds, playtimeMap, playerStats);
                Recent2WeeksActivityDTO recentActivity = createRecent2WeeksActivity(playtimeMap, ownedGames, gameTagsMap);
                List<TagPreferenceDTO> topTags = createTagPreferences(gameTagsMap, ownedGames, playtimeMap);
                List<GenreDistributionDTO> genreDistribution = createGenreDistribution(topTags);
                GameLibraryStatsDTO libraryStats = createLibraryStats(ownedGames, gameTagsMap, playtimeMap);

                DashboardDTO dashboard = new DashboardDTO(
                        steamId,
                        profileSummary,
                        topTags,
                        genreDistribution,
                        recentActivity,
                        libraryStats
                );

                // Cache
                try {
                    cacheService.cacheDashboard(steamId, dashboard);
                    log.info("Dashboard generation and cache storage completed: steamId={}", steamId);

                } catch (Exception e) {
                    log.warn("Cache storage failed (returning data normally): steamId={}", steamId, e);
                }

                return dashboard;

            } catch (Exception e) {
                log.error("Dashboard data processing failed: steamId={}", steamId, e);
                throw new DashboardException("Failed to generate dashboard data", steamId,
                        DashboardException.ErrorType.DATA_PROCESSING_ERROR, e);
            }

        } catch (DashboardException e) {
            throw e;

        } catch (DataAccessException e) {
            log.error("Database access error: steamId={}", steamId, e);
            DashboardDTO errorDashboard = createDatabaseErrorDashboard(steamId);
            cacheService.cacheDashboardWithTTL(steamId, errorDashboard, 300);
            return errorDashboard;

        } catch (Exception e) {
            log.error("Unexpected dashboard generation error: steamId={}", steamId, e);
            DashboardDTO errorDashboard = createUnknownErrorDashboard(steamId);
            cacheService.cacheDashboardWithTTL(steamId, errorDashboard, 60);
            return errorDashboard;
        }
    }

    private Recent2WeeksActivityDTO createRecent2WeeksActivity(Map<Long, GamePlaytimeInfo> playtimeMap,
                                                               List<Game> ownedGames,
                                                               Map<Long, List<String>> gameTagsMap) {

        // recent 2 weeks
        List<GamePlaytimeInfo> recentlyPlayedGames = playtimeMap.values().stream()
                .filter(info -> info.getRecentHours().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        if (recentlyPlayedGames.isEmpty()) {
            return new Recent2WeeksActivityDTO(
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, null
            );
        }

        // All playtime for 2 weeks
        BigDecimal totalRecentPlaytime = recentlyPlayedGames.stream()
                .map(GamePlaytimeInfo::getRecentHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int activeGamesCount = recentlyPlayedGames.size();

        //Average Game counts
        BigDecimal averageSessionTime = activeGamesCount > 0 ?
                totalRecentPlaytime.divide(BigDecimal.valueOf(activeGamesCount), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // Most played Game in 2 weeks
        GameStatsDTO mostPlayedGame = recentlyPlayedGames.stream()
                .max(Comparator.comparing(GamePlaytimeInfo::getRecentHours))
                .map(info -> {
                    Game game = ownedGames.stream()
                            .filter(g -> g.getAppid().equals(info.getAppId()))
                            .findFirst()
                            .orElse(null);

                    if (game != null) {
                        return new GameStatsDTO(
                                game.getAppid(),
                                game.getName(),
                                game.getImageUrl(),
                                info.getRecentHours(),//Recent 2 weeks
                                info.getLastPlayedDate(),
                                gameTagsMap.getOrDefault(game.getAppid(), List.of())
                        );
                    }
                    return null;
                })
                .orElse(null);

        return new Recent2WeeksActivityDTO(
                totalRecentPlaytime,
                activeGamesCount,
                averageSessionTime,
                mostPlayedGame
        );
    }

    private List<TagPreferenceDTO> createTagPreferences(Map<Long, List<String>> gameTagsMap,
                                                        List<Game> ownedGames,
                                                        Map<Long, GamePlaytimeInfo> playtimeMap) {
        Map<String, Integer> tagCounts = new HashMap<>();
        Map<String, BigDecimal> tagPlaytimes = new HashMap<>();

        // Only played games
        for (Map.Entry<Long, List<String>> entry : gameTagsMap.entrySet()) {
            Long appId = entry.getKey();
            GamePlaytimeInfo playtimeInfo = playtimeMap.get(appId);

            //Except for games that have not been played
            if (playtimeInfo == null || playtimeInfo.getTotalHours().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal gamePlaytime = playtimeInfo.getTotalHours();

            for (String tag : entry.getValue()) {
                tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
                tagPlaytimes.put(tag, tagPlaytimes.getOrDefault(tag, BigDecimal.ZERO).add(gamePlaytime));
            }
        }

        return tagCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)  // At least 2 games required
                .sorted((e1, e2) -> {
                    // Prioritize playtime, with the number of games as a secondary factor
                    BigDecimal playtime1 = tagPlaytimes.get(e1.getKey());
                    BigDecimal playtime2 = tagPlaytimes.get(e2.getKey());
                    int playtimeCompare = playtime2.compareTo(playtime1);
                    if (playtimeCompare != 0) return playtimeCompare;
                    return e2.getValue().compareTo(e1.getValue());
                })
                .limit(12)
                .map(entry -> {
                    String tag = entry.getKey();
                    int count = entry.getValue();
                    BigDecimal playtime = tagPlaytimes.getOrDefault(tag, BigDecimal.ZERO);

                    // Preference Score (Based on Playtime + Number of Games)
                    double score = Math.min(100,
                            (playtime.doubleValue() * 2) + (count * 5));

                    return new TagPreferenceDTO(tag, count, playtime, score);
                })
                .collect(Collectors.toList());
    }

    private GameLibraryStatsDTO createLibraryStats(List<Game> ownedGames,
                                                   Map<Long, List<String>> gameTagsMap,
                                                   Map<Long, GamePlaytimeInfo> playtimeMap) {

        // Statistics based on Playtime
        int neverPlayed = 0, shortPlayed = 0, mediumPlayed = 0, longPlayed = 0;

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

        // Top Played Games
        List<GameStatsDTO> topPlayed = ownedGames.stream()
                .filter(game -> playtimeMap.containsKey(game.getAppid()))
                .filter(game -> playtimeMap.get(game.getAppid()).getTotalHours().compareTo(BigDecimal.ZERO) > 0)
                .sorted((g1, g2) -> {
                    BigDecimal time1 = playtimeMap.get(g1.getAppid()).getTotalHours();
                    BigDecimal time2 = playtimeMap.get(g2.getAppid()).getTotalHours();
                    return time2.compareTo(time1);
                })
                .limit(10)  // Top 10
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

        // ====== 디버깅 로그 추가 ======
        log.info("=== DEBUGGING Recently Played Games ===");
        log.info("Total owned games: {}", ownedGames.size());
        log.info("Total playtime map entries: {}", playtimeMap.size());

        // 최근 플레이타임이 있는 게임들 확인
        long recentGamesCount = playtimeMap.values().stream()
                .filter(info -> info.getRecentHours().compareTo(BigDecimal.ZERO) > 0)
                .count();
        log.info("Games with recent hours > 0: {}", recentGamesCount);

        // 실제 최근 플레이 게임들 출력
        playtimeMap.entrySet().stream()
                .filter(entry -> entry.getValue().getRecentHours().compareTo(BigDecimal.ZERO) > 0)
                .forEach(entry -> {
                    Long appId = entry.getKey();
                    GamePlaytimeInfo info = entry.getValue();

                    // 해당 게임이 ownedGames 리스트에 있는지 확인
                    boolean gameExists = ownedGames.stream()
                            .anyMatch(game -> game.getAppid().equals(appId));

                    log.info("Recent game - AppID: {}, Recent hours: {}, Game exists in ownedGames: {}",
                            appId, info.getRecentHours(), gameExists);

                    if (gameExists) {
                        Game game = ownedGames.stream()
                                .filter(g -> g.getAppid().equals(appId))
                                .findFirst()
                                .orElse(null);
                        if (game != null) {
                            log.info("  -> Game name: {}", game.getName());
                        }
                    }
                });

        // Recently Played Games
        log.info("=== Starting Recently Played Games filtering ===");
        List<GameStatsDTO> recentlyPlayed = ownedGames.stream()
                .filter(game -> {
                    boolean containsKey = playtimeMap.containsKey(game.getAppid());
                    log.info("Game: {} ({}), playtimeMap contains: {}",
                            game.getName(), game.getAppid(), containsKey);
                    return containsKey;
                })
                .filter(game -> {
                    GamePlaytimeInfo info = playtimeMap.get(game.getAppid());
                    boolean hasRecentHours = info.getRecentHours().compareTo(BigDecimal.ZERO) > 0;
                    log.info("Game: {} ({}), recent hours: {}, hasRecentHours: {}",
                            game.getName(), game.getAppid(), info.getRecentHours(), hasRecentHours);
                    return hasRecentHours;
                })
                .sorted((g1, g2) -> {
                    GamePlaytimeInfo info1 = playtimeMap.get(g1.getAppid());
                    GamePlaytimeInfo info2 = playtimeMap.get(g2.getAppid());
                    return info2.getRecentHours().compareTo(info1.getRecentHours());
                })
                .limit(8)
                .map(game -> {
                    GamePlaytimeInfo info = playtimeMap.get(game.getAppid());
                    log.info("Adding to recentlyPlayed: {} - {} recent hours",
                            game.getName(), info.getRecentHours());
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

        log.info("=== Final recently played count: {} ===", recentlyPlayed.size());

        return new GameLibraryStatsDTO(
                neverPlayed, shortPlayed, mediumPlayed, longPlayed,
                topPlayed, recentlyPlayed
        );
    }


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

    //==== Error Dashboard ====

    private DashboardDTO createEmptyDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "None", BigDecimal.ZERO),
                List.of(),
                List.of(),
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of())
        );
    }

    private DashboardDTO createPrivateProfileDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Private", BigDecimal.ZERO),
                List.of(),
                List.of(),
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of())
        );
    }

    private DashboardDTO createNotFoundDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "None", BigDecimal.ZERO),
                List.of(),
                List.of(),
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of())
        );
    }

    private DashboardDTO createRateLimitDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Rate Limited", BigDecimal.ZERO),
                List.of(),
                List.of(),
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of())
        );
    }

    private DashboardDTO createDatabaseErrorDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "DB Error", BigDecimal.ZERO),
                List.of(),
                List.of(),
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of())
        );
    }

    private DashboardDTO createUnknownErrorDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Error", BigDecimal.ZERO),
                List.of(),
                List.of(),
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of())
        );
    }

    // === cache Methods ===

    public void invalidateDashboard(String steamId) {
        cacheService.invalidateDashboard(steamId);
        log.info("Dashboard cache invalidated: steamId={}", steamId);
    }

    public boolean isDashboardCached(String steamId) {
        return cacheService.isCacheValid(steamId);
    }

    @Transactional(readOnly = true)
    public DashboardDTO refreshDashboard(String steamId) {
        log.info("Dashboard force refresh: steamId={}", steamId);
        cacheService.invalidateDashboard(steamId);
        return generateDashboard(steamId);
    }
}