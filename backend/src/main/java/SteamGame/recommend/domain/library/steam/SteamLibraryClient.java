package SteamGame.recommend.domain.library.steam;

import SteamGame.recommend.domain.library.steam.SteamLibraryParser.GamesParse;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.SummaryParse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

// 기존 SteamApiService와 별개로, 비공개와 장애를 구분해 돌려주는 라이브러리 읽기 클라이언트
@Slf4j
@Component
public class SteamLibraryClient {

    private static final String HOST = "api.steampowered.com";
    private static final int CACHE_MAX_SIZE = 1_000;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    private final WebClient webClient;
    private final String apiKey;
    private final Duration timeout;
    private final Cache<String, SteamLibraryResult.Found> cache;

    public SteamLibraryClient(WebClient.Builder webClientBuilder,
                              @Value("${steam.api.key}") String apiKey,
                              @Value("${app.library.steam-timeout-seconds:5}") int timeoutSeconds,
                              @Value("${app.library.steam-cache-minutes:10}") int cacheMinutes) {
        // 보유 게임이 많으면 응답이 기본 버퍼(256KB)를 넘어 16MB까지 허용함
        this.webClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_BODY_BYTES))
                .build();
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.cache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(cacheMinutes))
                .build();
    }

    // 예외는 밖으로 던지지 않고 모두 결과 타입으로 바꿈
    public SteamLibraryResult fetch(String steamId64) {
        SteamLibraryResult.Found cached = cache.getIfPresent(steamId64);
        if (cached != null) {
            return cached;
        }
        try {
            SummaryParse summary = SteamLibraryParser.parseSummary(
                    get("/ISteamUser/GetPlayerSummaries/v2/", "steamids", steamId64, false));
            switch (summary.status()) {
                case NOT_FOUND:
                    return new SteamLibraryResult.NotFound();
                case PRIVATE:
                    return new SteamLibraryResult.Private();
                case UNAVAILABLE:
                    return unavailable(steamId64, summary.reason());
                default:
                    break;
            }

            GamesParse games = SteamLibraryParser.parseOwnedGames(
                    get("/IPlayerService/GetOwnedGames/v1/", "steamid", steamId64, true));
            return switch (games.status()) {
                case PRIVATE -> new SteamLibraryResult.Private();
                case EMPTY -> new SteamLibraryResult.Empty();
                case UNAVAILABLE -> unavailable(steamId64, games.reason());
                case FOUND -> {
                    SteamLibraryResult.Found found = new SteamLibraryResult.Found(summary.profile(), games.games());
                    cache.put(steamId64, found);
                    yield found;
                }
            };
        } catch (Exception e) {
            // 예외 메시지에 응답 내용이 섞일 수 있어 예외 종류만 남김
            return unavailable(steamId64, e.getClass().getSimpleName());
        }
    }

    // 사용자 지정 주소 이름을 SteamID64로 바꿈, 없으면 빈 값, Steam 장애는 503
    public Optional<String> resolveVanity(String name) {
        try {
            String body = webClient.get()
                    .uri(uri -> uri.scheme("https").host(HOST)
                            .path("/ISteamUser/ResolveVanityURL/v1/")
                            .queryParam("key", apiKey)
                            .queryParam("vanityurl", name)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout);
            return Optional.ofNullable(SteamLibraryParser.parseVanity(body));
        } catch (Exception e) {
            log.warn("Steam vanity resolve failed: cause={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STEAM_UNAVAILABLE");
        }
    }

    private String get(String path, String idParam, String steamId64, boolean ownedGames) {
        String body = webClient.get()
                .uri(uri -> {
                    var builder = uri.scheme("https").host(HOST).path(path)
                            .queryParam("key", apiKey)
                            .queryParam(idParam, steamId64);
                    if (ownedGames) {
                        builder = builder.queryParam("include_appinfo", "true")
                                .queryParam("include_played_free_games", "true");
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block(timeout);
        if (body == null) {
            throw new IllegalStateException("empty body");
        }
        return body;
    }

    private SteamLibraryResult unavailable(String steamId64, String reason) {
        log.warn("Steam library fetch failed: steamId=***{}, reason={}", last4(steamId64), reason);
        return new SteamLibraryResult.Unavailable(reason);
    }

    private static String last4(String steamId64) {
        return steamId64.length() <= 4 ? steamId64 : steamId64.substring(steamId64.length() - 4);
    }
}
