package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static SteamGame.recommend.domain.impression.entity.ImpressionSource.GEMINI;
import static SteamGame.recommend.domain.impression.entity.ImpressionSource.GNN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TeamDraftInterleaverTest {

    private static Game game(long appid, String name) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName(name);
        return g;
    }

    private static List<Game> games(long... appids) {
        return Arrays.stream(appids).mapToObj(id -> game(id, "game " + id)).toList();
    }

    @Test
    void alternatesStartingWithGemini() {
        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(games(1, 2, 3), games(4, 5, 6), "seed", 4, true);

        assertThat(picks).extracting(p -> p.game().getAppid(), InterleavedPick::source)
                .containsExactly(tuple(1L, GEMINI), tuple(4L, GNN), tuple(2L, GEMINI), tuple(5L, GNN));
    }

    @Test
    void alternatesStartingWithGnn() {
        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(games(1, 2, 3), games(4, 5, 6), "seed", 4, false);

        assertThat(picks).extracting(p -> p.game().getAppid()).containsExactly(4L, 1L, 5L, 2L);
    }

    @Test
    void sameGameIsPickedOnce() {
        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(games(1, 2), games(1, 3), "seed", 3, true);

        assertThat(picks).extracting(p -> p.game().getAppid(), InterleavedPick::source)
                .containsExactly(tuple(1L, GEMINI), tuple(3L, GNN), tuple(2L, GEMINI));
    }

    @Test
    void skipsOtherEditionsOfPickedGames() {
        List<Game> gemini = List.of(game(1, "Ori and the Blind Forest"));
        List<Game> gnn = List.of(game(2, "Ori and the Blind Forest: Definitive Edition"), game(3, "Hollow Knight"));

        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(gemini, gnn, "seed", 2, true);

        assertThat(picks).extracting(p -> p.game().getAppid()).containsExactly(1L, 3L);
    }

    @Test
    void skipsGamesNamedLikeTheSeed() {
        List<Game> gnn = List.of(game(1, "PORTAL™"), game(2, "Portal 2"));

        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(List.of(), gnn, "Portal", 2, true);

        assertThat(picks).extracting(p -> p.game().getAppid()).containsExactly(2L);
    }

    @Test
    void otherSideFillsWhenOneRunsOut() {
        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(games(1, 2, 3, 4), List.of(), "seed", 4, false);

        assertThat(picks).extracting(p -> p.game().getAppid(), InterleavedPick::source)
                .containsExactly(tuple(1L, GEMINI), tuple(2L, GEMINI), tuple(3L, GEMINI), tuple(4L, GEMINI));
    }

    @Test
    void returnsFewerWhenBothRunOut() {
        assertThat(TeamDraftInterleaver.interleave(games(1), games(2), "seed", 4, true)).hasSize(2);
        assertThat(TeamDraftInterleaver.interleave(List.of(), List.of(), "seed", 4, true)).isEmpty();
    }
}
