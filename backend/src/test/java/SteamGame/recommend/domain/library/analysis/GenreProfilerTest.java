package SteamGame.recommend.domain.library.analysis;

import SteamGame.recommend.domain.library.LibraryFixtures;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GenreProfile;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GenreShare;
import SteamGame.recommend.domain.library.content.GenreRules;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GenreProfilerTest {

    private static final LibraryContent REAL = new LibraryContentLoader(new ObjectMapper()).content();
    private static final LibraryContent FIXTURE = LibraryFixtures.validContent();

    // 2026-11-02 운영 DB에서 읽은 실제 득표수 (장르 판단에 관계있는 태그 위주)
    private static final Map<Long, Map<String, Integer>> REAL_VOTES = Map.ofEntries(
            Map.entry(105600L, votes("Open World Survival Craft", 906, "Sandbox", 890, "Survival", 713,
                    "Adventure", 640, "Crafting", 587, "Building", 561, "Exploration", 498, "Open World", 427)),
            Map.entry(413150L, votes("Farming Sim", 859, "Life Sim", 767, "Relaxing", 684, "RPG", 684,
                    "Agriculture", 548, "Crafting", 472, "Sandbox", 472, "Building", 387)),
            Map.entry(1086940L, votes("RPG", 954, "Choices Matter", 708, "Story Rich", 678, "CRPG", 527,
                    "Adventure", 493)),
            Map.entry(1551360L, votes("Racing", 1129, "Open World", 900, "Driving", 824, "Automobile Sim", 652,
                    "Adventure", 495, "Exploration", 417)),
            Map.entry(730L, votes("FPS", 1160, "Shooter", 834, "Tactical", 526)),
            Map.entry(570L, votes("MOBA", 1019, "Strategy", 723)),
            Map.entry(1245620L, votes("Souls-like", 1079, "Open World", 791, "RPG", 720, "Action RPG", 549)),
            Map.entry(2358720L, votes("Action RPG", 952, "Souls-like", 840, "RPG", 781)),
            Map.entry(271590L, votes("Open World", 32676, "Automobile Sim", 18788, "Shooter", 14971,
                    "Adventure", 13658)),
            Map.entry(292030L, votes("Open World", 1156, "RPG", 973, "Story Rich", 904, "Adventure", 584,
                    "Choices Matter", 522)),
            Map.entry(1174180L, votes("Open World", 1046, "Story Rich", 837, "Adventure", 697, "Shooter", 495)),
            Map.entry(264710L, votes("Open World Survival Craft", 817, "Survival", 814, "Horror", 745,
                    "Open World", 677, "Exploration", 666, "Crafting", 559, "Base Building", 550)));

    private static Map<String, Integer> votes(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    private static OwnedGame game(long appid, int minutes) {
        return new OwnedGame(appid, "g" + appid, minutes, 0, null);
    }

    // 사람이 봐도 납득되는 거점으로 가는지 실제 내용 파일과 실제 득표수로 확인한다
    // GTA V는 Open World와 Automobile Sim을 장르 판단에서 빼서 레이싱이 아니라 Shooter로 간다
    // 레드 데드 2는 Open World, Adventure, Story Rich를 빼서 Shooter로 간다
    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource({
            "Terraria, 105600, SURVIVAL_CRAFTING",
            "Stardew Valley, 413150, LIFE_FARMING",
            "Baldur's Gate 3, 1086940, RPG_ADVENTURE",
            "Forza Horizon 5, 1551360, RACING_SPORTS",
            "Counter-Strike 2, 730, ACTION_SHOOTER",
            "Dota 2, 570, STRATEGY_MANAGEMENT",
            "ELDEN RING, 1245620, ACTION_SHOOTER",
            "Black Myth Wukong, 2358720, RPG_ADVENTURE",
            "GTA V, 271590, ACTION_SHOOTER",
            "The Witcher 3, 292030, RPG_ADVENTURE",
            "Red Dead Redemption 2, 1174180, ACTION_SHOOTER",
            "Subnautica, 264710, SURVIVAL_CRAFTING"
    })
    void realGameGoesToExpectedGenre(String name, long appid, String expected) {
        GenreProfile p = GenreProfiler.profile(List.of(game(appid, 6000)), REAL_VOTES, REAL);

        assertThat(p.primaryGenre()).isEqualTo(expected);
        assertThat(p.state()).isEqualTo(GenreState.READY);
        assertThat(p.distribution()).containsExactly(new GenreShare(expected, 1.0));
    }

    @Test
    void oneGamesTimeGoesToOneGenreOnly() {
        // 테라리아 300시간, 발더스 게이트 3 100시간: 테라리아는 RPG 태그가 있어도 시간을 나누지 않는다
        GenreProfile p = GenreProfiler.profile(List.of(game(105600, 18000), game(1086940, 6000)), REAL_VOTES, REAL);

        assertThat(p.distribution()).containsExactly(
                new GenreShare("SURVIVAL_CRAFTING", 0.75), new GenreShare("RPG_ADVENTURE", 0.25));
        assertThat(p.primaryGenre()).isEqualTo("SURVIVAL_CRAFTING");
        assertThat(p.secondaryGenre()).isEqualTo("RPG_ADVENTURE");
        assertThat(p.coverageRatio()).isEqualTo(1.0);
        assertThat(p.state()).isEqualTo(GenreState.READY);
    }

    @Test
    void sameVotesPicksEarlierGenreInKeyOrder() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("PUZZLE_G1", 50, "RPG_ADVENTURE_G1", 50));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 6000)), v, FIXTURE);

        assertThat(p.primaryGenre()).isEqualTo("RPG_ADVENTURE");
    }

    @Test
    void genreScoreIsHighestTagNotSumOfTags() {
        // 액션 태그 셋의 합(90)이 퍼즐 하나(40)보다 커도 장르 점수는 가장 높은 태그 하나로 비교한다
        Map<Long, Map<String, Integer>> v = Map.of(1L,
                votes("ACTION_SHOOTER_G1", 30, "ACTION_SHOOTER_G2", 30, "PUZZLE_G1", 40));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 6000)), v, FIXTURE);

        assertThat(p.primaryGenre()).isEqualTo("PUZZLE");
    }

    @Test
    void excludedBroadTagsDoNotClassifyAGame() {
        // Open World, Adventure, Exploration만 있는 게임은 분류되지 않아 분석 범위를 낮춘다
        Map<Long, Map<String, Integer>> v = Map.of(
                1L, votes("Open World", 900, "Adventure", 800, "Exploration", 700),
                2L, votes("RPG", 500));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 6000), game(2, 6000)), v, REAL);

        assertThat(p.coverageRatio()).isEqualTo(0.5);
        assertThat(p.distribution()).containsExactly(new GenreShare("RPG_ADVENTURE", 1.0));
    }

    @Test
    void gameMissingFromCatalogLowersCoverage() {
        GenreProfile p = GenreProfiler.profile(List.of(game(105600, 6000), game(999, 18000)), REAL_VOTES, REAL);

        assertThat(p.coverageRatio()).isEqualTo(0.25);
        assertThat(p.state()).isEqualTo(GenreState.INSUFFICIENT);
    }

    @Test
    void puzzleAndVisualNovelCanBePrimary() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("Puzzle", 500), 2L, votes("Visual Novel", 500));

        assertThat(GenreProfiler.profile(List.of(game(1, 6000)), v, REAL).primaryGenre()).isEqualTo("PUZZLE");
        assertThat(GenreProfiler.profile(List.of(game(2, 6000)), v, REAL).primaryGenre()).isEqualTo("VISUAL_NOVEL");
    }

    @Test
    void noPlaytimeIsInsufficientWithEmptyDistribution() {
        GenreProfile p = GenreProfiler.profile(List.of(game(105600, 0), game(1086940, 0)), REAL_VOTES, REAL);

        assertThat(p.state()).isEqualTo(GenreState.INSUFFICIENT);
        assertThat(p.primaryGenre()).isNull();
        assertThat(p.secondaryGenre()).isNull();
        assertThat(p.coverageRatio()).isEqualTo(0.0);
        assertThat(p.distribution()).isEmpty();
    }

    // 기준값을 모두 0으로 낮춰도 분류된 시간이 없으면 1위 장르를 꺼내지 않고 기록 부족으로 끝난다
    @Test
    void nothingClassifiedIsInsufficientEvenWithZeroThresholds() {
        LibraryContent zeroRules = LibraryFixtures.withGenres(FIXTURE, FIXTURE.genres(), FIXTURE.genreExcludedTags(),
                new GenreRules(0, 0, 0.1, 0.15));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 6000)), Map.of(), zeroRules);

        assertThat(p.state()).isEqualTo(GenreState.INSUFFICIENT);
        assertThat(p.coverageRatio()).isEqualTo(0.0);
        assertThat(p.distribution()).isEmpty();
    }

    // 분류된 시간 10시간(600분)이 기준이다, 기준과 같으면 충분한 것으로 본다
    @ParameterizedTest(name = "{0}분 -> {1}")
    @CsvSource({"599, INSUFFICIENT", "600, READY"})
    void classifiedHoursBoundary(int minutes, GenreState expected) {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("RPG_ADVENTURE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, minutes)), v, FIXTURE);

        assertThat(p.state()).isEqualTo(expected);
        if (expected == GenreState.INSUFFICIENT) {
            assertThat(p.primaryGenre()).isNull();
            assertThat(p.distribution()).containsExactly(new GenreShare("RPG_ADVENTURE", 1.0));
        }
    }

    // 분석 범위 30%가 기준이다, 분류 3000분과 미분류 7000분이면 정확히 30%
    @ParameterizedTest(name = "분류 {0}분, 미분류 {1}분 -> {2}")
    @CsvSource({"3000, 7000, READY", "2999, 7001, INSUFFICIENT"})
    void coverageBoundary(int classified, int unclassified, GenreState expected) {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("RPG_ADVENTURE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, classified), game(2, unclassified)), v, FIXTURE);

        assertThat(p.state()).isEqualTo(expected);
    }

    // 1위와 2위 차이 10%p가 기준이다, 차이가 기준보다 작을 때만 MIXED
    @ParameterizedTest(name = "1위 {0}분, 2위 {1}분, 나머지 {2}분 -> {3}")
    @CsvSource({
            "4000, 3000, 3000, READY",
            "3990, 3010, 3000, MIXED",
            "5000, 5000, 0, MIXED"
    })
    void mixedBoundary(int first, int second, int rest, GenreState expected) {
        Map<Long, Map<String, Integer>> v = Map.of(
                1L, votes("RPG_ADVENTURE_G1", 10),
                2L, votes("PUZZLE_G1", 10),
                3L, votes("VISUAL_NOVEL_G1", 10));
        List<OwnedGame> games = new ArrayList<>(List.of(game(1, first), game(2, second)));
        if (rest > 0) {
            games.add(game(3, rest));
        }

        GenreProfile p = GenreProfiler.profile(games, v, FIXTURE);

        assertThat(p.state()).isEqualTo(expected);
        assertThat(p.primaryGenre()).isEqualTo("RPG_ADVENTURE");
    }

    // 2위 비율 15%가 기준이다, 기준과 같으면 보조 장르로 둔다
    @ParameterizedTest(name = "2위 {1}분 -> 보조 장르 {2}")
    @CsvSource({"8500, 1500, PUZZLE", "8510, 1490, ''"})
    void secondaryBoundary(int first, int second, String expected) {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("RPG_ADVENTURE_G1", 10), 2L, votes("PUZZLE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, first), game(2, second)), v, FIXTURE);

        assertThat(p.secondaryGenre()).isEqualTo(expected.isEmpty() ? null : expected);
        assertThat(p.distribution()).extracting(GenreShare::code).containsExactly("RPG_ADVENTURE", "PUZZLE");
    }

    @Test
    void singleGenreHasNoSecondaryAndIsReady() {
        Map<Long, Map<String, Integer>> v = new HashMap<>();
        v.put(1L, votes("RPG_ADVENTURE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 6000)), v, FIXTURE);

        assertThat(p.secondaryGenre()).isNull();
        assertThat(p.state()).isEqualTo(GenreState.READY);
    }
    // 9개 장르를 똑같이 하면 각 11%라 1위와 2위 차이는 없지만 2위가 15% 미만이라 보조 장르는 비어 있다
    @Test
    void evenlySpreadIsMixedWithoutSecondary() {
        Map<Long, Map<String, Integer>> v = new HashMap<>();
        List<OwnedGame> games = new ArrayList<>();
        long id = 1;
        for (String genre : FIXTURE.genres().keySet()) {
            v.put(id, votes(genre + "_G1", 10));
            games.add(game(id, 1000));
            id++;
        }

        GenreProfile p = GenreProfiler.profile(games, v, FIXTURE);

        assertThat(p.state()).isEqualTo(GenreState.MIXED);
        assertThat(p.primaryGenre()).isEqualTo("RPG_ADVENTURE");
        assertThat(p.secondaryGenre()).isNull();
        assertThat(p.distribution()).hasSize(9).allSatisfy(s -> assertThat(s.ratio()).isEqualTo(0.111));
    }

    // 2위 실제 비율 0.1499는 반올림하면 0.15지만 기준 비교는 반올림 전 값으로 한다
    @Test
    void secondaryUsesRawRatioNotRounded() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("RPG_ADVENTURE_G1", 10), 2L, votes("PUZZLE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 8501), game(2, 1499)), v, FIXTURE);

        assertThat(p.distribution()).containsExactly(
                new GenreShare("RPG_ADVENTURE", 0.85), new GenreShare("PUZZLE", 0.15));
        assertThat(p.secondaryGenre()).isNull();
    }

    // 분석 범위 실제 비율 0.2996은 응답에 0.3으로 나가지만 판정은 기록 부족이다
    @Test
    void coverageUsesRawRatioAndIsRoundedInResponse() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("RPG_ADVENTURE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 2996), game(2, 7004)), v, FIXTURE);

        assertThat(p.coverageRatio()).isEqualTo(0.3);
        assertThat(p.state()).isEqualTo(GenreState.INSUFFICIENT);
    }

    @Test
    void coverageIsRoundedToThreeDecimals() {
        Map<Long, Map<String, Integer>> v = Map.of(1L, votes("RPG_ADVENTURE_G1", 10));

        GenreProfile p = GenreProfiler.profile(List.of(game(1, 6000), game(2, 12000)), v, FIXTURE);

        assertThat(p.coverageRatio()).isEqualTo(0.333);
        assertThat(p.state()).isEqualTo(GenreState.READY);
    }
}
