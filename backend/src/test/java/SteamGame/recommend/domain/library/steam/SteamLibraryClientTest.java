package SteamGame.recommend.domain.library.steam;

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
import java.util.Optional;

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

    @BeforeEach
    void attachAppender() {
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        clientLogger.detachAppender(logs);
    }

    private SteamLibraryClient client() {
        ExchangeFunction exchange = request -> {
            calls.add(request.url());
            String path = request.url().getPath();
            String body = path.contains("GetPlayerSummaries") ? summaryBody
                    : path.contains("GetOwnedGames") ? gamesBody : vanityBody;
            return Mono.just(ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body).build());
        };
        return new SteamLibraryClient(WebClient.builder().exchangeFunction(exchange), "test-key", 5, 10);
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
    void privateIsNotCached() {
        SteamLibraryClient client = client();
        summaryBody = fixture("summary-private.json");
        client.fetch(ID);
        summaryBody = fixture("summary-public.json");

        assertThat(client.fetch(ID)).isInstanceOf(SteamLibraryResult.Found.class);
        assertThat(calls).hasSize(3);
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
