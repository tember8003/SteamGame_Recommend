package SteamGame.recommend.domain.library.steam;

import SteamGame.recommend.domain.library.steam.SteamLibraryParser.GamesParse;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.SummaryParse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;
import reactor.core.Exceptions;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

// 기존 SteamApiService와 별개로, 비공개와 장애를 구분해 돌려주는 라이브러리 읽기 클라이언트
// 예외 메시지에는 키가 든 주소가 섞이므로 어떤 로그에도 예외 메시지를 남기지 않는다
@Slf4j
@Component
public class SteamLibraryClient {

    private static final String HOST = "api.steampowered.com";
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    // 성공 결과는 게임 수 합계로 메모리를 제한하고, 비공개나 없음 결과는 짧게만 기억한다
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(2);
    private static final int NEGATIVE_MAX_SIZE = 10_000;
    private static final String BUSY = "BUSY";

    private final WebClient webClient;
    private final String apiKey;
    private final Duration timeout;
    private final Semaphore permits;
    private final Cache<String, SteamLibraryResult.Found> cache;
    private final Cache<String, SteamLibraryResult> negativeCache;

    @Autowired
    public SteamLibraryClient(WebClient.Builder webClientBuilder,
                              @Value("${steam.api.key}") String apiKey,
                              @Value("${app.library.steam-timeout-seconds:5}") int timeoutSeconds,
                              @Value("${app.library.steam-cache-minutes:10}") int cacheMinutes,
                              @Value("${app.library.steam-max-concurrent:8}") int maxConcurrent,
                              @Value("${app.library.steam-cache-max-games:200000}") long cacheMaxGames) {
        this(webClientBuilder, apiKey, timeoutSeconds, cacheMinutes, new Semaphore(maxConcurrent), cacheMaxGames,
                Ticker.systemTicker());
    }

    // 테스트에서 기본 한도로 만들기 위한 생성자
    SteamLibraryClient(WebClient.Builder webClientBuilder, String apiKey, int timeoutSeconds, int cacheMinutes) {
        this(webClientBuilder, apiKey, timeoutSeconds, cacheMinutes, new Semaphore(8), 200_000L,
                Ticker.systemTicker());
    }

    // 테스트에서 가짜 시계, 동시 호출 허용 수, 캐시 용량을 넣기 위한 생성자
    SteamLibraryClient(WebClient.Builder webClientBuilder, String apiKey, int timeoutSeconds, int cacheMinutes,
                       Semaphore permits, long cacheMaxGames, Ticker ticker) {
        // 보유 게임이 많으면 응답이 기본 버퍼(256KB)를 넘어 16MB까지 허용함
        this.webClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_BODY_BYTES))
                .build();
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.permits = permits;
        // 정리 작업을 호출한 스레드에서 바로 하게 해서 용량 초과분이 곧바로 빠지게 함
        this.cache = Caffeine.newBuilder()
                .maximumWeight(cacheMaxGames)
                .weigher((String id, SteamLibraryResult.Found found) -> Math.max(1, found.games().size()))
                .expireAfterWrite(Duration.ofMinutes(cacheMinutes))
                .executor(Runnable::run)
                .ticker(ticker)
                .build();
        this.negativeCache = Caffeine.newBuilder()
                .maximumSize(NEGATIVE_MAX_SIZE)
                .expireAfterWrite(NEGATIVE_TTL)
                .executor(Runnable::run)
                .ticker(ticker)
                .build();
    }

    // 예외는 밖으로 던지지 않고 모두 결과 타입으로 바꿈
    public SteamLibraryResult fetch(String steamId64) {
        SteamLibraryResult.Found cached = cache.getIfPresent(steamId64);
        if (cached != null) {
            return cached;
        }
        SteamLibraryResult negative = negativeCache.getIfPresent(steamId64);
        if (negative != null) {
            return negative;
        }
        try {
            SummaryParse summary = SteamLibraryParser.parseSummary(
                    get("/ISteamUser/GetPlayerSummaries/v2/", "steamids", steamId64, false));
            switch (summary.status()) {
                case NOT_FOUND:
                    return remember(steamId64, new SteamLibraryResult.NotFound());
                case PRIVATE:
                    return remember(steamId64, new SteamLibraryResult.Private());
                case UNAVAILABLE:
                    return unavailable(steamId64, summary.reason());
                default:
                    break;
            }

            GamesParse games = SteamLibraryParser.parseOwnedGames(
                    get("/IPlayerService/GetOwnedGames/v1/", "steamid", steamId64, true));
            return switch (games.status()) {
                case PRIVATE -> {
                    // 프로필은 공개인데 게임 세부 정보만 비공개인 경우라 원인 추적용으로 남김
                    log.info("Steam owned games hidden although summary was public: steamId=***{}", last4(steamId64));
                    yield remember(steamId64, new SteamLibraryResult.Private());
                }
                case EMPTY -> remember(steamId64, new SteamLibraryResult.Empty());
                case UNAVAILABLE -> unavailable(steamId64, games.reason());
                case FOUND -> {
                    SteamLibraryResult.Found found = new SteamLibraryResult.Found(summary.profile(), games.games());
                    cache.put(steamId64, found);
                    yield found;
                }
            };
        } catch (Exception e) {
            return unavailable(steamId64, e);
        }
    }

    // 사용자 지정 주소 이름을 SteamID64로 바꿈, 없으면 빈 값, Steam 장애는 503
    public Optional<String> resolveVanity(String name) {
        try {
            String body = call(uri -> uri.scheme("https").host(HOST)
                    .path("/ISteamUser/ResolveVanityURL/v1/")
                    .queryParam("key", apiKey)
                    .queryParam("vanityurl", name)
                    .build());
            return Optional.ofNullable(SteamLibraryParser.parseVanity(body));
        } catch (Exception e) {
            logFailure("Steam vanity resolve failed", null, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STEAM_UNAVAILABLE");
        }
    }

    private String get(String path, String idParam, String steamId64, boolean ownedGames) {
        return call(uri -> {
            var builder = uri.scheme("https").host(HOST).path(path)
                    .queryParam("key", apiKey)
                    .queryParam(idParam, steamId64);
            if (ownedGames) {
                builder = builder.queryParam("include_appinfo", "true")
                        .queryParam("include_played_free_games", "true");
            }
            return builder.build();
        });
    }

    // 동시 호출 수를 제한한다, 자리를 제한 시간 안에 못 얻으면 Steam을 부르지 않고 바쁨으로 처리함
    private String call(Function<UriBuilder, URI> uri) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallFailure("INTERRUPTED");
        }
        if (!acquired) {
            throw new CallFailure(BUSY);
        }
        try {
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
            if (body == null) {
                throw new CallFailure("EMPTY_BODY");
            }
            return body;
        } finally {
            permits.release();
        }
    }

    // 로그에 남길 고정 사유가 정해진 호출 실패
    private static final class CallFailure extends RuntimeException {
        private final String reason;

        CallFailure(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }
    }

    private SteamLibraryResult remember(String steamId64, SteamLibraryResult result) {
        negativeCache.put(steamId64, result);
        return result;
    }

    private SteamLibraryResult unavailable(String steamId64, String reason) {
        log.warn("Steam library fetch failed: steamId=***{}, reason={}", last4(steamId64), reason);
        return new SteamLibraryResult.Unavailable(reason);
    }

    private SteamLibraryResult unavailable(String steamId64, Exception e) {
        return new SteamLibraryResult.Unavailable(logFailure("Steam library fetch failed", steamId64, e));
    }

    // 사유는 상태 코드나 고정 문구로만 만든다, 401과 403은 키 문제라 ERROR로 올려 알림이 가게 함
    private static String logFailure(String what, String steamId64, Exception e) {
        Throwable cause = Exceptions.unwrap(e);
        String reason = reasonOf(cause);
        String id = steamId64 == null ? "" : ": steamId=***" + last4(steamId64);
        if (cause instanceof WebClientResponseException w
                && (w.getStatusCode().value() == 401 || w.getStatusCode().value() == 403)) {
            log.error("{} (check Steam API key){}, reason={}", what, id, reason);
        } else {
            log.warn("{}{}, reason={}", what, id, reason);
        }
        return reason;
    }

    private static String reasonOf(Throwable cause) {
        if (cause instanceof TimeoutException) {
            return "TIMEOUT";
        }
        if (cause instanceof WebClientResponseException w) {
            return "HTTP_" + w.getStatusCode().value();
        }
        if (cause instanceof CallFailure f) {
            return f.reason;
        }
        if (cause instanceof SteamLibraryParser.UnexpectedResponseException u) {
            return u.reason();
        }
        // 예외 메시지에 응답 내용이나 키가 섞일 수 있어 예외 종류만 남김
        return cause.getClass().getSimpleName();
    }

    private static String last4(String steamId64) {
        return steamId64.length() <= 4 ? steamId64 : steamId64.substring(steamId64.length() - 4);
    }
}
