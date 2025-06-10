package SteamGame.recommend.infra.api;

import SteamGame.recommend.domain.game.dto.GamePlaytimeInfo;
import SteamGame.recommend.domain.game.dto.PlayerStatsInfo;
import SteamGame.recommend.exception.SteamApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class SteamApiService {
    private final WebClient.Builder webClientBuilder;
    private final String steam_api_key;
    private final ObjectMapper objectMapper;

    public SteamApiService(WebClient.Builder webClientBuilder,
                           @Value("${steam.api.key}") String steam_api_key,
                           ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.steam_api_key = steam_api_key;
        this.objectMapper = objectMapper;
    }

    @Retry(name = "steamApi", fallbackMethod = "fallbackOwnedGames")
    @CircuitBreaker(name = "steamApi", fallbackMethod = "fallbackOwnedGames")
    public List<Long> getOwnedGameIds(String steamId) {
        try {
            validateSteamId(steamId);

            String response = webClientBuilder.build()
                    .get()
                    .uri(uri -> uri
                            .scheme("https")
                            .host("api.steampowered.com")
                            .path("/IPlayerService/GetOwnedGames/v1/")
                            .queryParam("key", steam_api_key)
                            .queryParam("steamid", steamId)
                            .queryParam("include_appinfo", "false")
                            .queryParam("include_played_free_games", "true")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseOwnedGamesResponse(response, steamId);
        } catch (Exception e) {
            log.error("소유 게임 조회 중 예상치 못한 오류: steamId={}", steamId, e);
            throw new SteamApiException("소유 게임 조회 실패", steamId, e);
        }
    }

    @Retry(name = "steamApi", fallbackMethod = "fallbackRecentGames")
    @CircuitBreaker(name = "steamApi", fallbackMethod = "fallbackRecentGames")
    public List<Long> getRecentPlayedGameIds(String steamId) {
        try {
            validateSteamId(steamId);

            String response = webClientBuilder.build()
                    .get()
                    .uri(uri -> uri
                            .scheme("https")
                            .host("api.steampowered.com")
                            .path("/IPlayerService/GetRecentlyPlayedGames/v1/")
                            .queryParam("key", steam_api_key)
                            .queryParam("steamid", steamId)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseRecentGamesResponse(response, steamId);
        } catch (Exception e) {
            log.error("최근 플레이 게임 조회 중 예상치 못한 오류: steamId={}", steamId, e);
            throw new SteamApiException("최근 플레이 게임 조회 실패", steamId, e);
        }
    }

    @Retry(name = "steamApi", fallbackMethod = "fallbackPlaytime")
    @CircuitBreaker(name = "steamApi", fallbackMethod = "fallbackPlaytime")
    public Map<Long, GamePlaytimeInfo> getDetailedPlaytimes(String steamId) {
        try {
            validateSteamId(steamId);

            String response = webClientBuilder.build()
                    .get()
                    .uri(uri -> uri
                            .scheme("https")
                            .host("api.steampowered.com")
                            .path("/IPlayerService/GetOwnedGames/v1/")
                            .queryParam("key", steam_api_key)
                            .queryParam("steamid", steamId)
                            .queryParam("include_appinfo", "true")
                            .queryParam("include_played_free_games", "true")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parsePlaytimeResponse(response, steamId);
        } catch (Exception e) {
            log.error("플레이시간 조회 중 예상치 못한 오류: steamId={}", steamId, e);
            throw new SteamApiException("플레이시간 조회 실패", steamId, e);
        }
    }

    @Retry(name = "steamApi", fallbackMethod = "fallbackPlayerStats")
    @CircuitBreaker(name = "steamApi", fallbackMethod = "fallbackPlayerStats")
    public PlayerStatsInfo getPlayerStats(String steamId) {
        try {
            validateSteamId(steamId);

            String response = webClientBuilder.build()
                    .get()
                    .uri(uri -> uri
                            .scheme("https")
                            .host("api.steampowered.com")
                            .path("/ISteamUser/GetPlayerSummaries/v2/")
                            .queryParam("key", steam_api_key)
                            .queryParam("steamids", steamId)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parsePlayerStatsResponse(response, steamId);
        } catch (Exception e) {
            log.error("플레이어 통계 조회 중 예상치 못한 오류: steamId={}", steamId, e);
            throw new SteamApiException("플레이어 통계 조회 실패", steamId, e);
        }
    }

    // 🔧 Steam ID 유효성 검증
    private void validateSteamId(String steamId) {
        if (steamId == null || steamId.trim().isEmpty()) {
            throw new SteamApiException("Steam ID가 없습니다", steamId);
        }

        if (!steamId.matches("\\d{17}")) {
            throw new SteamApiException("유효하지 않은 Steam ID 형식입니다", steamId);
        }
    }

    // 🔧 응답 파싱 메서드들
    private List<Long> parseOwnedGamesResponse(String response, String steamId) {
        try {
            JsonNode games = objectMapper
                    .readTree(response)
                    .path("response")
                    .path("games");

            if (games.isMissingNode() || !games.isArray()) {
                log.warn("소유 게임 목록이 비어있음: steamId={}", steamId);
                return Collections.emptyList();
            }

            List<Long> appids = new ArrayList<>();
            for (JsonNode game : games) {
                Long appId = game.path("appid").asLong(0);
                if (appId > 0) {
                    appids.add(appId);
                }
            }

            log.info("소유 게임 {}개 조회 완료: steamId={}", appids.size(), steamId);
            return appids;

        } catch (Exception e) {
            log.error("소유 게임 응답 파싱 실패: steamId={}", steamId, e);
            throw new SteamApiException("소유 게임 데이터 파싱 실패", steamId, e);
        }
    }

    private List<Long> parseRecentGamesResponse(String response, String steamId) {
        try {
            JsonNode games = objectMapper.readTree(response)
                    .path("response")
                    .path("games");

            if (games.isMissingNode() || !games.isArray()) {
                log.info("최근 플레이 게임이 없음: steamId={}", steamId);
                return Collections.emptyList();
            }

            List<Long> recentAppIds = new ArrayList<>();
            for (JsonNode game : games) {
                Long appId = game.path("appid").asLong(0);
                if (appId > 0) {
                    recentAppIds.add(appId);
                }
            }

            log.info("최근 플레이 게임 {}개 조회 완료: steamId={}", recentAppIds.size(), steamId);
            return recentAppIds;

        } catch (Exception e) {
            log.error("최근 게임 응답 파싱 실패: steamId={}", steamId, e);
            throw new SteamApiException("최근 게임 데이터 파싱 실패", steamId, e);
        }
    }

    private Map<Long, GamePlaytimeInfo> parsePlaytimeResponse(String response, String steamId) {
        try {
            JsonNode games = objectMapper
                    .readTree(response)
                    .path("response")
                    .path("games");

            if (games.isMissingNode() || !games.isArray()) {
                log.warn("플레이시간 데이터가 비어있음: steamId={}", steamId);
                return Collections.emptyMap();
            }

            Map<Long, GamePlaytimeInfo> playtimeMap = new HashMap<>();

            for (JsonNode game : games) {
                try {
                    Long appId = game.path("appid").asLong(0);
                    if (appId <= 0) continue;

                    int playtimeForever = game.path("playtime_forever").asInt(0);
                    int playtime2Weeks = game.path("playtime_2weeks").asInt(0);
                    long lastPlayed = game.path("rtime_last_played").asLong(0);

                    GamePlaytimeInfo info = new GamePlaytimeInfo(
                            appId,
                            BigDecimal.valueOf(playtimeForever / 60.0),
                            BigDecimal.valueOf(playtime2Weeks / 60.0),
                            lastPlayed > 0 ? LocalDate.ofEpochDay(lastPlayed / 86400) : null
                    );

                    playtimeMap.put(appId, info);

                } catch (Exception e) {
                    log.warn("개별 게임 플레이시간 파싱 실패: steamId={}, game={}", steamId, game, e);
                    // 개별 게임 오류는 무시하고 계속 진행
                }
            }

            log.info("플레이시간 데이터 {}개 조회 완료: steamId={}", playtimeMap.size(), steamId);
            return playtimeMap;

        } catch (Exception e) {
            log.error("플레이시간 응답 파싱 실패: steamId={}", steamId, e);
            throw new SteamApiException("플레이시간 데이터 파싱 실패", steamId, e);
        }
    }

    private PlayerStatsInfo parsePlayerStatsResponse(String response, String steamId) {
        try {
            JsonNode players = objectMapper
                    .readTree(response)
                    .path("response")
                    .path("players");

            if (players.isMissingNode() || !players.isArray() || players.size() == 0) {
                log.warn("플레이어 정보가 없음: steamId={}", steamId);
                return new PlayerStatsInfo("Unknown", 0L);
            }

            JsonNode player = players.get(0);
            String playerName = player.path("personaname").asText("Unknown");
            Long accountCreated = player.path("timecreated").asLong(0L);

            log.info("플레이어 정보 조회 완료: steamId={}, name={}", steamId, playerName);
            return new PlayerStatsInfo(playerName, accountCreated);

        } catch (Exception e) {
            log.error("플레이어 통계 응답 파싱 실패: steamId={}", steamId, e);
            throw new SteamApiException("플레이어 데이터 파싱 실패", steamId, e);
        }
    }

    // 🔧 Fallback 메서드들
    private List<Long> fallbackOwnedGames(String steamId, Throwable ex) {
        log.error("소유 게임 조회 fallback 실행: steamId={}, reason={}", steamId, ex.getMessage());
        return Collections.emptyList();
    }

    private List<Long> fallbackRecentGames(String steamId, Throwable ex) {
        log.error("최근 게임 조회 fallback 실행: steamId={}, reason={}", steamId, ex.getMessage());
        return Collections.emptyList();
    }

    private Map<Long, GamePlaytimeInfo> fallbackPlaytime(String steamId, Throwable ex) {
        log.error("플레이시간 조회 fallback 실행: steamId={}, reason={}", steamId, ex.getMessage());
        return Collections.emptyMap();
    }

    private PlayerStatsInfo fallbackPlayerStats(String steamId, Throwable ex) {
        log.error("플레이어 통계 조회 fallback 실행: steamId={}, reason={}", steamId, ex.getMessage());
        return new PlayerStatsInfo("Unknown", 0L);
    }
}