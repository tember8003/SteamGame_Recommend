package SteamGame.recommend.domain.library.steam;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static SteamGame.recommend.domain.library.steam.SteamLibraryParserTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteamLibraryClientTest {

    private static final String ID = "76561198000000000";

    private final List<URI> calls = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger clientLogger = (Logger) LoggerFactory.getLogger(SteamLibraryClient.class);

    private String summaryBody = fixture("summary-public.json");
    private String gamesBody = fixture("owned-normal.json");
    private String vanityBody = fixture("vanity-ok.json");
    private HttpStatus status = HttpStatus.OK;
    private final AtomicLong nanos = new AtomicLong();

    @BeforeEach
    void attachAppender() {
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        clientLogger.detachAppender(logs);
    }

    private ExchangeFunction exchange() {
        return request -> {
            calls.add(request.url());
            String path = request.url().getPath();
            String body = path.contains("GetPlayerSummaries") ? summaryBody
                    : path.contains("GetOwnedGames") ? gamesBody : vanityBody;
            return Mono.just(ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body).build());
        };
    }

    private SteamLibraryClient client() {
        return new SteamLibraryClient(WebClient.builder().exchangeFunction(exchange()), "test-key", 5, 10);
    }

    // 가짜 시계, 동시 호출 허용 수, 캐시 용량(게임 수 합계)을 직접 정하는 클라이언트
    private SteamLibraryClient client(Semaphore permits, long cacheMaxGames) {
        return new SteamLibraryClient(WebClient.builder().exchangeFunction(exchange()), "test-key", 1, 10,
                permits, cacheMaxGames, nanos::get);
    }

    @Test
    void publicProfileCallsTwiceAndFound() {
        SteamLibraryResult r = client().fetch(ID);

        assertThat(r).isInstanceOf(SteamLibraryResult.Found.class);
        SteamLibraryResult.Found found = (SteamLibraryResult.Found) r;
        assertThat(found.profile().personaName()).isEqualTo("SecretNick");
        assertThat(found.games()).hasSize(3);
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).getPath()).contains("GetPlayerSummaries");
        assertThat(calls.get(0).getQuery()).contains("steamids=" + ID);
        assertThat(calls.get(1).getPath()).contains("GetOwnedGames");
        assertThat(calls.get(1).getQuery())
                .contains("include_appinfo=true", "include_played_free_games=true", "steamid=" + ID);
    }

    @Test
    void privateSummarySkipsGamesCall() {
        summaryBody = fixture("summary-private.json");

        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.Private.class);
        assertThat(calls).hasSize(1);
    }

    @Test
    void noPlayerIsNotFound() {
        summaryBody = fixture("summary-no-player.json");

        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.NotFound.class);
    }

    @Test
    void gameDetailsPrivateAndEmpty() {
        gamesBody = fixture("owned-details-private.json");
        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.Private.class);

        gamesBody = fixture("owned-zero.json");
        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.Empty.class);
    }

    @Test
    void serverErrorIsUnavailableAndNeverThrows() {
        status = HttpStatus.INTERNAL_SERVER_ERROR;

        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.Unavailable.class);
    }

    @Test
    void brokenBodyIsUnavailable() {
        summaryBody = "{not json";

        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.Unavailable.class);
    }

    @Test
    void foundIsCached() {
        SteamLibraryClient client = client();
        SteamLibraryResult first = client.fetch(ID);
        int callsAfterFirst = calls.size();

        SteamLibraryResult second = client.fetch(ID);

        assertThat(second).isSameAs(first);
        assertThat(calls).hasSize(callsAfterFirst);
    }

    @Test
    void privateIsCachedBrieflyThenRefetched() {
        SteamLibraryClient client = client(new Semaphore(8), 200_000);
        summaryBody = fixture("summary-private.json");
        assertThat(client.fetch(ID)).isInstanceOf(SteamLibraryResult.Private.class);
        summaryBody = fixture("summary-public.json");

        nanos.set(Duration.ofSeconds(110).toNanos());
        assertThat(client.fetch(ID)).isInstanceOf(SteamLibraryResult.Private.class);
        assertThat(calls).hasSize(1);

        nanos.set(Duration.ofSeconds(125).toNanos());
        assertThat(client.fetch(ID)).isInstanceOf(SteamLibraryResult.Found.class);
        assertThat(calls).hasSize(3);
    }

    @Test
    void notFoundAndEmptyAreCachedBriefly() {
        SteamLibraryClient client = client(new Semaphore(8), 200_000);
        summaryBody = fixture("summary-no-player.json");
        client.fetch(ID);
        client.fetch(ID);
        assertThat(calls).hasSize(1);

        gamesBody = fixture("owned-zero.json");
        summaryBody = fixture("summary-public.json");
        String other = "76561198000000555";
        assertThat(client.fetch(other)).isInstanceOf(SteamLibraryResult.Empty.class);
        int after = calls.size();
        assertThat(client.fetch(other)).isInstanceOf(SteamLibraryResult.Empty.class);
        assertThat(calls).hasSize(after);
    }

    @Test
    void gameDetailsPrivateLogsInfoThatSummaryWasPublic() {
        gamesBody = fixture("owned-details-private.json");

        client().fetch(ID);

        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).contains("public").contains("0000");
        });
    }

    @Test
    void foundExpiresAfterCacheMinutes() {
        SteamLibraryClient client = client(new Semaphore(8), 200_000);
        client.fetch(ID);
        assertThat(calls).hasSize(2);

        nanos.set(Duration.ofMinutes(9).toNanos());
        client.fetch(ID);
        assertThat(calls).hasSize(2);

        nanos.set(Duration.ofMinutes(11).toNanos());
        client.fetch(ID);
        assertThat(calls).hasSize(4);
    }

    @Test
    void foundCacheIsBoundedByTotalGameCount() {
        // 게임 3개짜리 두 명이면 합계 6이라 용량 5를 넘어 한 명은 밀려난다
        SteamLibraryClient small = client(new Semaphore(8), 5);
        small.fetch(ID);
        small.fetch("76561198000000777");
        int before = calls.size();
        small.fetch(ID);
        small.fetch("76561198000000777");
        assertThat(calls.size()).isGreaterThan(before).isLessThan(before + 4);

        calls.clear();
        SteamLibraryClient roomy = client(new Semaphore(8), 6);
        roomy.fetch(ID);
        roomy.fetch("76561198000000777");
        int roomyBefore = calls.size();
        roomy.fetch(ID);
        roomy.fetch("76561198000000777");
        assertThat(calls).hasSize(roomyBefore);
    }

    @Test
    void exhaustedPermitsAreUnavailableBusyWithoutCallingSteam() {
        SteamLibraryClient busy = client(new Semaphore(0), 200_000);

        SteamLibraryResult r = busy.fetch(ID);

        assertThat(r).isEqualTo(new SteamLibraryResult.Unavailable("BUSY"));
        assertThat(calls).isEmpty();
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("BUSY"));
    }

    @Test
    void permitsLimitConcurrentSteamCalls() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        // 처음 두 호출은 서로를 기다려 반드시 겹치게 한다, 허용 수가 1이면 여기서 시간 초과로 실패함
        CountDownLatch overlap = new CountDownLatch(2);
        // 카운터는 응답을 내보내기 전에 줄여서 허가 반납보다 늦게 줄어드는 일이 없게 한다
        ExchangeFunction slow = request -> Mono.fromCallable(() -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                overlap.countDown();
                assertThat(overlap.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(50);
            } finally {
                inFlight.decrementAndGet();
            }
            String body = request.url().getPath().contains("GetPlayerSummaries") ? summaryBody : gamesBody;
            return ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build();
        });
        SteamLibraryClient limited = new SteamLibraryClient(WebClient.builder().exchangeFunction(slow), "test-key",
                5, 10, new Semaphore(2), 200_000, nanos::get);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<SteamLibraryResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String id = "7656119800000" + (1000 + i);
                futures.add(pool.submit(() -> limited.fetch(id)));
            }
            for (Future<SteamLibraryResult> f : futures) {
                assertThat(f.get(10, TimeUnit.SECONDS)).isInstanceOf(SteamLibraryResult.Found.class);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(peak.get()).isEqualTo(2);
    }

    @Test
    void failureReasonsAreStatusOrTimeoutAndNeverContainTheKey() {
        status = HttpStatus.BAD_GATEWAY;
        assertThat(client().fetch(ID)).isEqualTo(new SteamLibraryResult.Unavailable("HTTP_502"));
        assertThat(logs.list.get(logs.list.size() - 1).getLevel()).isEqualTo(Level.WARN);

        SteamLibraryClient slow = new SteamLibraryClient(
                WebClient.builder().exchangeFunction(request -> Mono.never()), "test-key", 1, 10);
        assertThat(slow.fetch(ID)).isEqualTo(new SteamLibraryResult.Unavailable("TIMEOUT"));

        for (ILoggingEvent e : logs.list) {
            assertThat(e.getFormattedMessage()).doesNotContain("test-key", "key=");
            assertThat(e.getThrowableProxy()).isNull();
        }
    }

    @Test
    void keyRejectedStatusesLogError() {
        for (HttpStatus rejected : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN}) {
            logs.list.clear();
            status = rejected;

            assertThat(client().fetch(ID)).isEqualTo(new SteamLibraryResult.Unavailable("HTTP_" + rejected.value()));

            assertThat(logs.list).anySatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.ERROR));
        }
    }

    @Test
    void emptyOrUnexpectedVanityIs503() {
        vanityBody = fixture("vanity-unexpected.json");
        assertThatThrownBy(() -> client().resolveVanity("Gabe")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        vanityBody = fixture("vanity-empty.json");
        assertThatThrownBy(() -> client().resolveVanity("Gabe")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void inconsistentOwnedGamesAreUnavailableAndNotCached() {
        SteamLibraryClient client = client(new Semaphore(8), 200_000);
        for (String name : new String[] {"owned-count-positive-empty.json", "owned-count-string.json"}) {
            calls.clear();
            gamesBody = fixture(name);
            assertThat(client.fetch(ID)).as(name).isInstanceOf(SteamLibraryResult.Unavailable.class);
            assertThat(client.fetch(ID)).as(name).isInstanceOf(SteamLibraryResult.Unavailable.class);
            assertThat(calls).as(name).hasSize(4);
        }
    }

    @Test
    void summaryWithoutPlayersKeyIsUnavailableNotNotFound() {
        summaryBody = fixture("summary-no-players-key.json");
        assertThat(client().fetch(ID)).isInstanceOf(SteamLibraryResult.Unavailable.class);
    }

    @Test
    void unavailableIsNotCached() {
        SteamLibraryClient client = client();
        status = HttpStatus.BAD_GATEWAY;
        client.fetch(ID);
        status = HttpStatus.OK;

        assertThat(client.fetch(ID)).isInstanceOf(SteamLibraryResult.Found.class);
    }

    @Test
    void logsHaveNoPersonaNoGamesAndMaskedId() {
        // 성공 흐름과 실패 흐름을 모두 거치게 함
        SteamLibraryClient client = client();
        client.fetch(ID);
        status = HttpStatus.INTERNAL_SERVER_ERROR;
        client.fetch("76561198000000999");
        summaryBody = "{\"response\":{\"players\":[{\"personaname\":\"SecretNick\"";
        status = HttpStatus.OK;
        client.fetch("76561198000000998");

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent e : logs.list) {
            String line = e.getFormattedMessage();
            assertThat(line).doesNotContain("SecretNick", "SecretGame", "76561198000000");
        }
        assertThat(logs.list.get(0).getFormattedMessage()).contains("0999");
    }

    @Test
    void resolveVanityFound() {
        assertThat(client().resolveVanity("Gabe")).contains("76561198000000001");
        assertThat(calls.get(0).getQuery()).contains("vanityurl=Gabe");
    }

    @Test
    void resolveVanityNoMatch() {
        vanityBody = fixture("vanity-nomatch.json");

        assertThat(client().resolveVanity("nobody")).isEqualTo(Optional.empty());
    }

    @Test
    void resolveVanityServerErrorIs503() {
        status = HttpStatus.INTERNAL_SERVER_ERROR;

        assertThatThrownBy(() -> client().resolveVanity("Gabe"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void largeLibraryOver256KbIsFound() {
        StringBuilder sb = new StringBuilder("{\"response\":{\"game_count\":4000,\"games\":[");
        for (int i = 0; i < 4000; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"appid\":").append(100000 + i)
                    .append(",\"name\":\"Some Fairly Long Realistic Game Title Number ").append(i).append("\"")
                    .append(",\"playtime_forever\":").append(i)
                    .append(",\"img_icon_url\":\"0123456789abcdef0123456789abcdef01234567\"")
                    .append(",\"playtime_windows_forever\":0,\"playtime_mac_forever\":0,\"playtime_linux_forever\":0")
                    .append(",\"rtime_last_played\":1700000000,\"playtime_disconnected\":0}");
        }
        gamesBody = sb.append("]}}").toString();
        assertThat(gamesBody.length()).isGreaterThan(1_000_000);

        // ExchangeFunction은 코덱 설정을 우회하므로 실제 교환 경로를 타는 가짜 커넥터를 씀
        String summary = summaryBody;
        String games = gamesBody;
        org.springframework.http.client.reactive.ClientHttpConnector connector = (method, uri, callback) -> {
            var response = new org.springframework.mock.http.client.reactive.MockClientHttpResponse(200);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.setBody(uri.getPath().contains("GetPlayerSummaries") ? summary : games);
            return callback.apply(new org.springframework.mock.http.client.reactive.MockClientHttpRequest(method, uri))
                    .then(Mono.just(response));
        };
        SteamLibraryClient big = new SteamLibraryClient(
                WebClient.builder().clientConnector(connector), "test-key", 5, 10);

        SteamLibraryResult r = big.fetch(ID);

        assertThat(r).isInstanceOf(SteamLibraryResult.Found.class);
        assertThat(((SteamLibraryResult.Found) r).games()).hasSize(4000);
    }

    @Test
    void neverRespondingSteamTimesOutAsUnavailable() {
        SteamLibraryClient slow = new SteamLibraryClient(
                WebClient.builder().exchangeFunction(request -> Mono.never()), "test-key", 1, 10);

        SteamLibraryResult r = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(4), () -> slow.fetch(ID));

        assertThat(r).isInstanceOf(SteamLibraryResult.Unavailable.class);
    }
}
