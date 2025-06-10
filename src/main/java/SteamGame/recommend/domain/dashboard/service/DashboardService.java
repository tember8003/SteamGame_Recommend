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
    private final DashboardCacheService cacheService; // 🔧 캐시 서비스 추가

    private static final String[] CHART_COLORS = {
            "#00d4ff", "#7c3aed", "#f59e0b", "#10b981", "#ef4444",
            "#8b5cf6", "#06b6d4", "#84cc16", "#f97316", "#ec4899"
    };

    public DashboardService(SteamApiService steamApiService,
                            GameRepository gameRepository,
                            TagRepository tagRepository,
                            DashboardCacheService cacheService) { // 🔧 의존성 주입
        this.steamApiService = steamApiService;
        this.gameRepository = gameRepository;
        this.tagRepository = tagRepository;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public DashboardDTO generateDashboard(String steamId) {
        // 1. 캐시 확인
        Optional<DashboardDTO> cachedDashboard = cacheService.getCachedDashboard(steamId);
        if (cachedDashboard.isPresent()) {
            log.info("캐시에서 대시보드 반환: steamId={}", steamId);
            return cachedDashboard.get();
        }

        try {
            log.info("새로운 대시보드 생성 시작: steamId={}", steamId);

            // 2. Steam API 데이터 조회
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
                log.warn("Steam API 오류로 인한 부분 실패: steamId={}, type={}, message={}",
                        steamId, e.getClass().getSimpleName(), e.getMessage());

                // Steam API 오류 시에도 빈 대시보드 대신 가능한 데이터로 진행
                if (e.getStatusCode() == 403) {
                    // 프로필 비공개 시 특별한 메시지
                    return createPrivateProfileDashboard(steamId);
                } else if (e.getStatusCode() == 404) {
                    // 존재하지 않는 사용자
                    return createNotFoundDashboard(steamId);
                } else if (e.getStatusCode() == 429) {
                    // 요청 한도 초과 시 1분 캐시
                    DashboardDTO rateLimitDashboard = createRateLimitDashboard(steamId);
                    cacheService.cacheDashboardWithTTL(steamId, rateLimitDashboard, 60);
                    return rateLimitDashboard;
                }
                // 기타 Steam API 오류는 계속 진행
            }

            if (ownedGameIds.isEmpty()) {
                DashboardDTO emptyDashboard = createEmptyDashboard(steamId);
                cacheService.cacheDashboard(steamId, emptyDashboard);
                return emptyDashboard;
            }

            // 3. 데이터베이스 조회
            List<Game> ownedGames = Collections.emptyList();
            Map<Long, List<String>> gameTagsMap = Collections.emptyMap();

            try {
                ownedGames = gameRepository.findByAppidIn(ownedGameIds);
                gameTagsMap = getGameTagsMap(ownedGameIds);

            } catch (Exception e) {
                log.error("데이터베이스 조회 실패: steamId={}", steamId, e);
                throw new DashboardException("게임 데이터 조회 실패", steamId,
                        DashboardException.ErrorType.DATABASE_ERROR, e);
            }

            // 4. 대시보드 생성
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

                // 5. 캐시 저장
                try {
                    cacheService.cacheDashboard(steamId, dashboard);
                    log.info("새 대시보드 생성 및 캐시 저장 완료: steamId={}", steamId);

                } catch (Exception e) {
                    log.warn("대시보드 캐시 저장 실패 (데이터는 정상 반환): steamId={}", steamId, e);
                    // 캐시 저장 실패해도 데이터는 반환
                }

                return dashboard;

            } catch (Exception e) {
                log.error("대시보드 데이터 처리 실패: steamId={}", steamId, e);
                throw new DashboardException("대시보드 데이터 생성 실패", steamId,
                        DashboardException.ErrorType.DATA_PROCESSING_ERROR, e);
            }

        } catch (DashboardException e) {
            // 이미 처리된 커스텀 예외는 재발생
            throw e;

        } catch (DataAccessException e) {
            log.error("데이터베이스 접근 오류: steamId={}", steamId, e);
            DashboardDTO errorDashboard = createDatabaseErrorDashboard(steamId);
            cacheService.cacheDashboardWithTTL(steamId, errorDashboard, 300); // 5분 캐시
            return errorDashboard;

        } catch (Exception e) {
            log.error("예상치 못한 대시보드 생성 오류: steamId={}", steamId, e);
            DashboardDTO errorDashboard = createUnknownErrorDashboard(steamId);
            cacheService.cacheDashboardWithTTL(steamId, errorDashboard, 60); // 1분 캐시
            return errorDashboard;
        }
    }

    private DashboardDTO createPrivateProfileDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "비공개", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "PRIVATE_PROFILE",
                        "프로필이 비공개입니다",
                        "Steam 프로필을 공개로 설정하시면 개인화된 대시보드를 제공할 수 있습니다.",
                        "프로필 설정 변경하기",
                        "https://steamcommunity.com/my/edit/settings"
                ))
        );
    }

    /**
     * 🔧 대시보드 캐시 무효화 (사용자가 새 게임 구매하거나 데이터 업데이트 시 호출)
     */
    public void invalidateDashboard(String steamId) {
        cacheService.invalidateDashboard(steamId);
        log.info("대시보드 캐시 무효화: steamId={}", steamId);
    }

    /**
     * 🔧 캐시 상태 확인
     */
    public boolean isDashboardCached(String steamId) {
        return cacheService.isCacheValid(steamId);
    }

    /**
     * 🔧 강제 새로고침 (캐시 무시하고 새로 생성)
     */
    @Transactional(readOnly = true)
    public DashboardDTO refreshDashboard(String steamId) {
        log.info("대시보드 강제 새로고침: steamId={}", steamId);

        // 캐시 무효화
        cacheService.invalidateDashboard(steamId);

        // 새로 생성
        return generateDashboard(steamId);
    }

    // 🔧 기존 메서드들은 그대로 유지...
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
                    "새로운 장르 탐험",
                    String.format("주로 %s 게임을 플레이하시네요. %s 장르도 시도해보세요!",
                            topTags.get(0).getTagName(), getComplementaryGenre(topTags.get(0).getTagName())),
                    "추천 받기",
                    "/api/recommend/input"
            ));
        }

        if (summary.getAveragePlaytimePerGame().compareTo(BigDecimal.valueOf(10)) < 0) {
            insights.add(new RecommendationInsightDTO(
                    "PLAYTIME_ANALYSIS",
                    "몰입도 높은 게임 추천",
                    "평균 플레이 시간이 짧은 편이네요. 더 몰입할 수 있는 장편 게임을 추천드려요.",
                    "장편 게임 찾기",
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
                .orElse("없음");
    }

    private String getComplementaryGenre(String currentGenre) {
        Map<String, String> complementary = Map.of(
                "액션", "퍼즐",
                "RPG", "전략",
                "시뮬레이션", "액션",
                "전략", "RPG",
                "퍼즐", "어드벤처"
        );
        return complementary.getOrDefault(currentGenre, "인디");
    }

    private DashboardDTO createEmptyDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "없음", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "WELCOME",
                        "게임을 추가해보세요",
                        "아직 게임 데이터가 없습니다. Steam에서 게임을 플레이하고 다시 확인해보세요!",
                        "게임 추천 받기",
                        "/api/recommend/random"
                ))
        );
    }

    private DashboardDTO createNotFoundDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "없음", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다",
                        "입력하신 Steam ID가 올바른지 확인해주세요.",
                        "Steam ID 찾기",
                        "https://steamidfinder.com/"
                ))
        );
    }

    private DashboardDTO createRateLimitDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "일시정지", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "RATE_LIMIT",
                        "요청이 일시적으로 제한되었습니다",
                        "Steam API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.",
                        "잠시 후 새로고침",
                        "#"
                ))
        );
    }

    private DashboardDTO createDatabaseErrorDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "오류", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "DATABASE_ERROR",
                        "일시적인 시스템 오류",
                        "게임 데이터베이스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.",
                        "다시 시도",
                        "#"
                ))
        );
    }

    private DashboardDTO createUnknownErrorDashboard(String steamId) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "오류", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        "UNKNOWN_ERROR",
                        "예상치 못한 오류",
                        "시스템에 예상치 못한 문제가 발생했습니다. 문제가 지속되면 관리자에게 문의해주세요.",
                        "고객 지원",
                        "/support"
                ))
        );
    }
}