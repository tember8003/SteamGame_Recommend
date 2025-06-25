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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

            log.info("Game ID: Raw Steam API response: {}", response);
            JsonNode responseNode = objectMapper.readTree(response);
            log.info("Game ID: Parsed response node: {}", responseNode);

            return parseOwnedGamesResponse(response, steamId);
        } catch (Exception e) {
            log.error("Unexpected error while retrieving owned games: steamId={}", steamId, e);
            throw new SteamApiException("Failed to retrieve owned games", steamId, e);
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

            log.info("Recent Play: Raw Steam API response: {}", response);
            JsonNode responseNode = objectMapper.readTree(response);
            log.info("Recent Play: Parsed response node: {}", responseNode);

            return parseRecentGamesResponse(response, steamId);
        } catch (Exception e) {
            log.error("Unexpected error while retrieving recently played games: steamId={}", steamId, e);
            throw new SteamApiException("Failed to retrieve recently played games", steamId, e);
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
            log.error("Unexpected error while retrieving playtime data: steamId={}", steamId, e);
            throw new SteamApiException("Failed to retrieve playtime data", steamId, e);
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
            log.error("Unexpected error while retrieving player statistics: steamId={}", steamId, e);
            throw new SteamApiException("Failed to retrieve player statistics", steamId, e);
        }
    }

    // Steam ID validation
    private void validateSteamId(String steamId) {
        if (steamId == null || steamId.trim().isEmpty()) {
            throw new SteamApiException("Steam ID is missing", steamId);
        }

        if (!steamId.matches("\\d{17}")) {
            throw new SteamApiException("Invalid Steam ID format", steamId);
        }
    }

    // Response parsing methods
    private List<Long> parseOwnedGamesResponse(String response, String steamId) {
        try {
            JsonNode games = objectMapper
                    .readTree(response)
                    .path("response")
                    .path("games");

            if (games.isMissingNode() || !games.isArray()) {
                log.warn("Owned games list is empty: steamId={}", steamId);
                return Collections.emptyList();
            }

            List<Long> appids = new ArrayList<>();
            for (JsonNode game : games) {
                Long appId = game.path("appid").asLong(0);
                if (appId > 0) {
                    appids.add(appId);
                }
            }

            log.info("Retrieved {} owned games successfully: steamId={}", appids.size(), steamId);
            return appids;

        } catch (Exception e) {
            log.error("Failed to parse owned games response: steamId={}", steamId, e);
            throw new SteamApiException("Failed to parse owned games data", steamId, e);
        }
    }

    private List<Long> parseRecentGamesResponse(String response, String steamId) {
        try {
            JsonNode games = objectMapper.readTree(response)
                    .path("response")
                    .path("games");

            if (games.isMissingNode() || !games.isArray()) {
                log.info("No recently played games found: steamId={}", steamId);
                return Collections.emptyList();
            }

            List<Long> recentAppIds = new ArrayList<>();
            for (JsonNode game : games) {
                Long appId = game.path("appid").asLong(0);
                if (appId > 0) {
                    recentAppIds.add(appId);
                }
            }

            log.info("Retrieved {} recently played games successfully: steamId={}", recentAppIds.size(), steamId);
            return recentAppIds;

        } catch (Exception e) {
            log.error("Failed to parse recent games response: steamId={}", steamId, e);
            throw new SteamApiException("Failed to parse recent games data", steamId, e);
        }
    }

    private Map<Long, GamePlaytimeInfo> parsePlaytimeResponse(String response, String steamId) {
        try {
            JsonNode games = objectMapper
                    .readTree(response)
                    .path("response")
                    .path("games");

            if (games.isMissingNode() || !games.isArray()) {
                log.warn("Playtime data is empty: steamId={}", steamId);
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

                    if (playtime2Weeks > 0) {
                        log.info("Recent game found: steamId={}, appId={}, 2weeks={}min ({}h)",
                                steamId, appId, playtime2Weeks, playtime2Weeks / 60.0);
                    }

                    GamePlaytimeInfo info = new GamePlaytimeInfo(
                            appId,
                            BigDecimal.valueOf(playtimeForever / 60.0),
                            BigDecimal.valueOf(playtime2Weeks / 60.0),
                            // lastPlayed (Unix timestamp → LocalDate)
                            lastPlayed > 0 ?
                                    LocalDate.ofInstant(
                                            Instant.ofEpochSecond(lastPlayed),
                                            ZoneId.systemDefault()
                                    ) : null
                    );

                    playtimeMap.put(appId, info);

                    log.debug("Game parsed: appId={}, forever={}h, 2weeks={}h, lastPlayed={}",
                            appId,
                            info.getTotalHours(),
                            info.getRecentHours(),
                            info.getLastPlayedDate());

                } catch (Exception e) {
                    log.warn("Failed to parse individual game playtime: steamId={}, game={}", steamId, game, e);
                    // Continue processing despite individual game errors
                }
            }

            long recentPlayCount = playtimeMap.values().stream()
                    .filter(info -> info.getRecentHours().compareTo(BigDecimal.ZERO) > 0)
                    .count();

            log.info("Retrieved {} playtime data entries successfully: steamId={}, recentPlayGames={}",
                    playtimeMap.size(), steamId, recentPlayCount);

            return playtimeMap;

        } catch (Exception e) {
            log.error("Failed to parse playtime response: steamId={}", steamId, e);
            throw new SteamApiException("Failed to parse playtime data", steamId, e);
        }
    }

    private PlayerStatsInfo parsePlayerStatsResponse(String response, String steamId) {
        try {
            JsonNode players = objectMapper
                    .readTree(response)
                    .path("response")
                    .path("players");

            if (players.isMissingNode() || !players.isArray() || players.size() == 0) {
                log.warn("No player information found: steamId={}", steamId);
                return new PlayerStatsInfo("Unknown", 0L);
            }

            JsonNode player = players.get(0);
            String playerName = player.path("personaname").asText("Unknown");
            Long accountCreated = player.path("timecreated").asLong(0L);

            log.info("Retrieved player information successfully: steamId={}, name={}", steamId, playerName);
            return new PlayerStatsInfo(playerName, accountCreated);

        } catch (Exception e) {
            log.error("Failed to parse player statistics response: steamId={}", steamId, e);
            throw new SteamApiException("Failed to parse player data", steamId, e);
        }
    }

    // Fallback methods
    private List<Long> fallbackOwnedGames(String steamId, Throwable ex) {
        log.error("Owned games retrieval fallback executed: steamId={}, reason={}", steamId, ex.getMessage());
        return Collections.emptyList();
    }

    private List<Long> fallbackRecentGames(String steamId, Throwable ex) {
        log.error("Recent games retrieval fallback executed: steamId={}, reason={}", steamId, ex.getMessage());
        return Collections.emptyList();
    }

    private Map<Long, GamePlaytimeInfo> fallbackPlaytime(String steamId, Throwable ex) {
        log.error("Playtime retrieval fallback executed: steamId={}, reason={}", steamId, ex.getMessage());
        return Collections.emptyMap();
    }

    private PlayerStatsInfo fallbackPlayerStats(String steamId, Throwable ex) {
        log.error("Player statistics retrieval fallback executed: steamId={}, reason={}", steamId, ex.getMessage());
        return new PlayerStatsInfo("Unknown", 0L);
    }
}