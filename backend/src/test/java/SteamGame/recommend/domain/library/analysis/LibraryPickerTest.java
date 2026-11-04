package SteamGame.recommend.domain.library.analysis;

import SteamGame.recommend.domain.library.LibraryFixtures;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Picks;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.PlayNext;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Revisit;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TagShare;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryPickerTest {

    private static final LibraryContent CONTENT = LibraryFixtures.validContent();
    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    // 사용자가 많이 한 태그, 비율이 큰 순서
    private static final List<TagShare> TASTE = List.of(
            new TagShare("Strategy", 30, 0.30), new TagShare("Colony Sim", 20, 0.20),
            new TagShare("Sandbox", 10, 0.10), new TagShare("Survival", 5, 0.05));
    private static final ReviewRating GOOD = new ReviewRating(1000, 95);

    private static Map<String, Integer> votes(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    private static OwnedGame unplayed(long appid) {
        return new OwnedGame(appid, "g" + appid, 0, 0, null);
    }

    private static OwnedGame played(long appid, int minutes, long idleDays) {
        return new OwnedGame(appid, "g" + appid, minutes, 0, NOW.minus(Duration.ofDays(idleDays)));
    }

    private static Picks pick(List<OwnedGame> games, Map<Long, Map<String, Integer>> v, Map<Long, ReviewRating> r) {
        return LibraryPicker.pick(games, v, r, TASTE, CONTENT, NOW);
    }

    @Test
    void playNextPicksUnplayedGamesSharingTwoTasteTagsBestFirst() {
        Map<Long, Map<String, Integer>> v = new HashMap<>();
        v.put(1L, votes("Strategy", 900, "Colony Sim", 800, "Puzzle", 10));      // 점수 0.5
        v.put(2L, votes("Sandbox", 900, "Survival", 800));                       // 점수 0.15
        v.put(3L, votes("Strategy", 900, "Sandbox", 800));                       // 점수 0.4
        v.put(4L, votes("Strategy", 900, "Puzzle", 800));                        // 겹친 태그 1개라 제외
        v.put(5L, votes("Strategy", 900, "Colony Sim", 800));                    // 플레이했으므로 제외
        Map<Long, ReviewRating> r = Map.of(1L, GOOD, 2L, GOOD, 3L, GOOD, 4L, GOOD, 5L, GOOD);

        Picks p = pick(List.of(unplayed(1), unplayed(2), unplayed(3), unplayed(4), played(5, 30, 1)), v, r);

        assertThat(p.playNext()).extracting(PlayNext::appid).containsExactly(1L, 3L, 2L);
        assertThat(p.playNext().get(0).matchedTags()).containsExactly("Strategy", "Colony Sim");
        assertThat(p.playNext().get(1).matchedTags()).containsExactly("Strategy", "Sandbox");
    }

    @Test
    void playNextShowsAtMostThree() {
        Map<Long, Map<String, Integer>> v = new HashMap<>();
        Map<Long, ReviewRating> r = new HashMap<>();
        List<OwnedGame> games = new java.util.ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            v.put(i, votes("Strategy", 900, "Colony Sim", 800));
            r.put(i, GOOD);
            games.add(unplayed(i));
        }

        assertThat(pick(games, v, r).playNext()).hasSize(3);
    }

    @Test
    void playNextIsSingleOrEmptyWhenFewMatch() {
        Map<Long, Map<String, Integer>> v = Map.of(
                1L, votes("Strategy", 900, "Colony Sim", 800),
                2L, votes("Puzzle", 900, "Racing", 800));
        Map<Long, ReviewRating> r = Map.of(1L, GOOD, 2L, GOOD);

        assertThat(pick(List.of(unplayed(1), unplayed(2)), v, r).playNext()).hasSize(1);
        assertThat(pick(List.of(unplayed(2)), v, r).playNext()).isEmpty();
    }

    // 리뷰 50개, 긍정 70%가 기준이고 기준과 같으면 통과한다, 평가를 모르는 게임은 추천하지 않는다
    @ParameterizedTest(name = "리뷰 {0}개, 긍정 {1}% -> {2}")
    @CsvSource({"50, 70, true", "49, 95, false", "1000, 69.9, false"})
    void playNextRequiresGoodReviews(int reviews, double pct, boolean picked) {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("Strategy", 900, "Colony Sim", 800));

        Picks p = pick(List.of(unplayed(1)), v, Map.of(1L, new ReviewRating(reviews, pct)));

        assertThat(p.playNext()).hasSize(picked ? 1 : 0);
        assertThat(pick(List.of(unplayed(1)), v, Map.of()).playNext()).isEmpty();
    }

    // 흔한 태그(Action)는 대표 태그 다섯 개 자리를 차지하지 않는다
    @Test
    void playNextSkipsCommonTagsWhenTakingTopFive() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("Action", 999, "Casual", 998, "A", 997, "B", 996,
                "C", 995, "Strategy", 10, "Colony Sim", 9));

        // 흔한 태그를 빼면 대표 다섯 개는 A, B, C, Strategy, Colony Sim이 된다
        assertThat(pick(List.of(unplayed(1)), v, Map.of(1L, GOOD)).playNext()).hasSize(1);
    }

    @Test
    void playNextNeedsTasteTags() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("Strategy", 900, "Colony Sim", 800));

        Picks p = LibraryPicker.pick(List.of(unplayed(1)), v, Map.of(1L, GOOD), List.of(), CONTENT, NOW);

        assertThat(p.playNext()).isEmpty();
    }

    // 10시간(600분) 이상, 365일 이상 안 켠 게임이 기준이고 기준과 같으면 들어간다
    @Test
    void revisitPicksLongPlayedGamesIdleForAYear() {
        List<OwnedGame> games = List.of(
                played(1, 600, 365), played(2, 599, 400), played(3, 3000, 364),
                new OwnedGame(4, "g4", 5000, 0, null), played(5, 1200, 800));

        Picks p = pick(games, Map.of(), Map.of());

        assertThat(p.revisit()).extracting(Revisit::appid).containsExactly(5L, 1L);
        assertThat(p.revisit().get(0)).isEqualTo(new Revisit(5, "g5", 20.0, 800));
    }

    @Test
    void revisitShowsAtMostThree() {
        List<OwnedGame> games = List.of(played(1, 1000, 400), played(2, 2000, 400), played(3, 3000, 400),
                played(4, 4000, 400));

        assertThat(pick(games, Map.of(), Map.of()).revisit()).extracting(Revisit::appid).containsExactly(4L, 3L, 2L);
    }

    // 60~120분만 하고 90일 넘게 안 켠 게임이 한 장르에 3개 이상이면 그 장르를 보여 준다
    @Test
    void abandonedGenreNeedsThreeShortIdleGamesInOneGenre() {
        Map<Long, Map<String, Integer>> v = new HashMap<>();
        for (long i = 1; i <= 6; i++) {
            v.put(i, votes(i <= 4 ? "PUZZLE_G1" : "RPG_ADVENTURE_G1", 100));
        }
        List<OwnedGame> games = List.of(played(1, 60, 90), played(2, 120, 200), played(3, 90, 100),
                played(4, 61, 89), played(5, 70, 100), played(6, 70, 100));

        LibraryAnalysis.AbandonedGenre a = pick(games, v, Map.of()).abandonedGenre();

        assertThat(a.genre()).isEqualTo("PUZZLE");
        assertThat(a.gameCount()).isEqualTo(3);
        assertThat(a.games()).containsExactly("g2", "g3", "g1");
    }

    @ParameterizedTest(name = "{0}분 -> 포함 {1}")
    @CsvSource({"59, false", "60, true", "120, true", "121, false"})
    void abandonedMinuteBoundaries(int minutes, boolean counted) {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("PUZZLE_G1", 1), 2L, votes("PUZZLE_G1", 1),
                3L, votes("PUZZLE_G1", 1));
        List<OwnedGame> games = List.of(played(1, 90, 100), played(2, 90, 100), played(3, minutes, 100));

        LibraryAnalysis.AbandonedGenre a = pick(games, v, Map.of()).abandonedGenre();

        if (counted) {
            assertThat(a.gameCount()).isEqualTo(3);
        } else {
            assertThat(a).isNull();
        }
    }

    @Test
    void abandonedTieGoesToEarlierGenreAndUnknownLastPlayedIsSkipped() {
        Map<Long, Map<String, Integer>> v = new HashMap<>();
        for (long i = 1; i <= 7; i++) {
            v.put(i, votes(i <= 3 ? "PUZZLE_G1" : "RPG_ADVENTURE_G1", 100));
        }
        List<OwnedGame> games = List.of(played(1, 90, 100), played(2, 90, 100), played(3, 90, 100),
                played(4, 90, 100), played(5, 90, 100), played(6, 90, 100), new OwnedGame(7, "g7", 90, 0, null));

        assertThat(pick(games, v, Map.of()).abandonedGenre().genre()).isEqualTo("RPG_ADVENTURE");
    }

    @Test
    void emptyLibraryPicksNothing() {
        Picks p = pick(List.of(played(1, 0, 10)), Map.of(), Map.of());

        assertThat(p.playNext()).isEmpty();
        assertThat(p.revisit()).isEmpty();
        assertThat(p.abandonedGenre()).isNull();
    }
}
