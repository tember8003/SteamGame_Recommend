package SteamGame.recommend.domain.library.steam;

import SteamGame.recommend.domain.library.steam.SteamLibraryParser.GamesParse;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.GamesStatus;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.SummaryParse;
import SteamGame.recommend.domain.library.steam.SteamLibraryParser.SummaryStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
}
