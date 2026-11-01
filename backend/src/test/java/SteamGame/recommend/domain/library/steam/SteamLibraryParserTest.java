package SteamGame.recommend.domain.library.steam;

import SteamGame.recommend.domain.library.steam.SteamLibraryParser.GamesParse;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.GamesStatus;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.SummaryParse;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.SummaryStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteamLibraryParserTest {

    static String fixture(String name) {
        try (var in = SteamLibraryParserTest.class.getResourceAsStream("/library/steam/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void summaryPublic() {
        SummaryParse r = SteamLibraryParser.parseSummary(fixture("summary-public.json"));
        assertThat(r.status()).isEqualTo(SummaryStatus.PUBLIC);
        assertThat(r.profile().steamId64()).isEqualTo("76561198000000000");
        assertThat(r.profile().personaName()).isEqualTo("SecretNick");
        assertThat(r.profile().avatarUrl()).isEqualTo("https://avatars.steamstatic.com/abc_full.jpg");
    }

    @Test
    void summaryPrivate() {
        SummaryParse r = SteamLibraryParser.parseSummary(fixture("summary-private.json"));
        assertThat(r.status()).isEqualTo(SummaryStatus.PRIVATE);
        assertThat(r.profile()).isNull();
    }

    @Test
    void summaryNoPlayer() {
        assertThat(SteamLibraryParser.parseSummary(fixture("summary-no-player.json")).status())
                .isEqualTo(SummaryStatus.NOT_FOUND);
    }

    @Test
    void ownedGamesNormal() {
        GamesParse r = SteamLibraryParser.parseOwnedGames(fixture("owned-normal.json"));
        assertThat(r.status()).isEqualTo(GamesStatus.FOUND);
        assertThat(r.games()).hasSize(3);
        OwnedGame first = r.games().get(0);
        assertThat(first.appid()).isEqualTo(570L);
        assertThat(first.name()).isEqualTo("SecretGameA");
        assertThat(first.minutesTotal()).isEqualTo(600);
        assertThat(first.minutesTwoWeeks()).isEqualTo(120);
        assertThat(first.lastPlayed()).isEqualTo(Instant.ofEpochSecond(1700000000L));
        OwnedGame zero = r.games().get(1);
        assertThat(zero.minutesTotal()).isZero();
        assertThat(zero.minutesTwoWeeks()).isZero();
        assertThat(zero.lastPlayed()).isNull();
    }

    @Test
    void ownedGamesDetailsPrivate() {
        assertThat(SteamLibraryParser.parseOwnedGames(fixture("owned-details-private.json")).status())
                .isEqualTo(GamesStatus.PRIVATE);
    }

    @Test
    void ownedGamesZero() {
        assertThat(SteamLibraryParser.parseOwnedGames(fixture("owned-zero.json")).status())
                .isEqualTo(GamesStatus.EMPTY);
    }

    @Test
    void brokenJson() {
        SummaryParse s = SteamLibraryParser.parseSummary("{not json");
        assertThat(s.status()).isEqualTo(SummaryStatus.UNAVAILABLE);
        assertThat(s.reason()).isNotBlank();
        GamesParse g = SteamLibraryParser.parseOwnedGames("{not json");
        assertThat(g.status()).isEqualTo(GamesStatus.UNAVAILABLE);
        assertThat(g.reason()).isNotBlank();
    }

    @Test
    void gameCountPositiveWithoutGamesIsUnavailable() {
        GamesParse missing = SteamLibraryParser.parseOwnedGames("{\"response\":{\"game_count\":5}}");
        assertThat(missing.status()).isEqualTo(GamesStatus.UNAVAILABLE);
        GamesParse notArray = SteamLibraryParser.parseOwnedGames("{\"response\":{\"game_count\":5,\"games\":\"x\"}}");
        assertThat(notArray.status()).isEqualTo(GamesStatus.UNAVAILABLE);
    }

    @Test
    void summaryWithoutResponseOrPlayersKeyIsUnavailable() {
        for (String name : new String[] {"summary-no-response.json", "summary-no-players-key.json"}) {
            SummaryParse r = SteamLibraryParser.parseSummary(fixture(name));
            assertThat(r.status()).as(name).isEqualTo(SummaryStatus.UNAVAILABLE);
            assertThat(r.reason()).as(name).isNotBlank();
        }
        assertThat(SteamLibraryParser.parseSummary("{\"response\":{\"players\":\"x\"}}").status())
                .isEqualTo(SummaryStatus.UNAVAILABLE);
    }

    @Test
    void playerWithoutVisibilityIsUnavailable() {
        SummaryParse r = SteamLibraryParser.parseSummary(fixture("summary-no-visibility.json"));
        assertThat(r.status()).isEqualTo(SummaryStatus.UNAVAILABLE);
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void vanitySuccessOtherThanOneOr42Throws() {
        assertThatThrownBy(() -> SteamLibraryParser.parseVanity(fixture("vanity-unexpected.json")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> SteamLibraryParser.parseVanity(fixture("vanity-empty.json")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> SteamLibraryParser.parseVanity("{\"response\":{\"success\":1}}"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void vanityOneAnd42StillWork() throws Exception {
        assertThat(SteamLibraryParser.parseVanity(fixture("vanity-ok.json"))).isEqualTo("76561198000000001");
        assertThat(SteamLibraryParser.parseVanity(fixture("vanity-nomatch.json"))).isNull();
    }

    @Test
    void gamesWithoutPositiveAppidAreSkippedWithWarn() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger logger = (Logger) LoggerFactory.getLogger(SteamLibraryParser.class);
        logger.addAppender(logs);
        try {
            GamesParse r = SteamLibraryParser.parseOwnedGames(fixture("owned-some-invalid.json"));

            assertThat(r.status()).isEqualTo(GamesStatus.FOUND);
            assertThat(r.games()).extracting(OwnedGame::appid).containsExactly(570L, 730L, 440L);
            assertThat(logs.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).contains("1");
                assertThat(e.getFormattedMessage()).doesNotContain("NoAppid");
            });
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void moreThanHalfSkippedIsUnavailable() {
        GamesParse r = SteamLibraryParser.parseOwnedGames(fixture("owned-mostly-invalid.json"));
        assertThat(r.status()).isEqualTo(GamesStatus.UNAVAILABLE);
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void exactlyHalfSkippedIsStillFound() {
        GamesParse r = SteamLibraryParser.parseOwnedGames(
                "{\"response\":{\"game_count\":2,\"games\":[{\"appid\":5},{\"name\":\"x\"}]}}");
        assertThat(r.status()).isEqualTo(GamesStatus.FOUND);
        assertThat(r.games()).hasSize(1);
    }

    @Test
    void emptyResponseObjectStaysPrivate() {
        assertThat(SteamLibraryParser.parseOwnedGames("{\"response\":{}}").status()).isEqualTo(GamesStatus.PRIVATE);
    }

    @Test
    void positiveCountWithEmptyGamesOrNonNumericCountIsUnavailable() {
        for (String name : new String[] {"owned-count-positive-empty.json", "owned-count-string.json"}) {
            GamesParse r = SteamLibraryParser.parseOwnedGames(fixture(name));
            assertThat(r.status()).as(name).isEqualTo(GamesStatus.UNAVAILABLE);
            assertThat(r.reason()).as(name).isNotBlank();
        }
    }
}
