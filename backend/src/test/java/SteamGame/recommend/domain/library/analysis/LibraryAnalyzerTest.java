package SteamGame.recommend.domain.library.analysis;

import SteamGame.recommend.domain.library.LibraryFixtures;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GamerStat;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.LibraryContentValidator;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryAnalyzerTest {

    private static final LibraryContent CONTENT = LibraryFixtures.validContent();
    private static final String ID = "76561198000000001";

    private static OwnedGame g(long id, int minutes) {
        return new OwnedGame(id, "game" + id, minutes, 0, null);
    }

    private static OwnedGame recent(long id, int minutes, int twoWeeks) {
        return new OwnedGame(id, "game" + id, minutes, twoWeeks, null);
    }

    // 모든 게임에 같은 태그가 있는 것으로 둔다
    private static Map<Long, List<String>> tagsAll(List<OwnedGame> games, String... tags) {
        Map<Long, List<String>> m = new HashMap<>();
        for (OwnedGame x : games) {
            m.put(x.appid(), List.of(tags));
        }
        return m;
    }

    private static LibraryAnalysis run(List<OwnedGame> games, Map<Long, List<String>> tags) {
        return LibraryAnalyzer.analyze(games, tags, t -> 0.0, CONTENT, ID);
    }

    private static LibraryAnalysis run(List<OwnedGame> games) {
        return run(games, tagsAll(games, "STRATEGY_A"));
    }

    // owned개 중 앞의 zero개가 0분, 나머지는 minutes분
    private static List<OwnedGame> lib(int owned, int zero, int minutes) {
        List<OwnedGame> l = new ArrayList<>();
        for (int i = 0; i < owned; i++) {
            l.add(g(i + 1, i < zero ? 0 : minutes));
        }
        return l;
    }

    private static String ledger(List<OwnedGame> games) {
        return run(games).ledger().code();
    }

    private static String crime(List<OwnedGame> games) {
        return run(games).verdict().crime().code();
    }

    private static Integer stat(LibraryAnalysis a, StatCode c) {
        GamerStat found = a.gamerStats().stream().filter(s -> s.code() == c).findFirst().orElseThrow();
        return found.value();
    }

    // ---- 장부

    @Test
    void ledger_starter_boundary() {
        assertThat(ledger(lib(9, 0, 1000))).isEqualTo("STARTER");
        assertThat(ledger(lib(10, 0, 1000))).isNotEqualTo("STARTER");
    }

    @Test
    void ledger_legendaryCollector_boundary() {
        assertThat(ledger(lib(300, 150, 1000))).isEqualTo("LEGENDARY_COLLECTOR");
        assertThat(ledger(lib(300, 149, 1000))).isEqualTo("COLLECTOR");
        assertThat(ledger(lib(299, 200, 1000))).isEqualTo("COLLECTOR");
    }

    @Test
    void ledger_collector_boundary() {
        assertThat(ledger(lib(100, 40, 1000))).isEqualTo("COLLECTOR");
        assertThat(ledger(lib(100, 39, 1000))).isNotEqualTo("COLLECTOR");
    }

    @Test
    void ledger_explorer_boundary() {
        // 플레이한 10개 중 4개가 1~5시간이면 40%
        List<OwnedGame> in = new ArrayList<>(lib(6, 0, 1000));
        for (int i = 0; i < 4; i++) {
            in.add(g(100 + i, 120));
        }
        assertThat(ledger(in)).isEqualTo("EXPLORER");
        List<OwnedGame> out = new ArrayList<>(lib(7, 0, 1000));
        for (int i = 0; i < 3; i++) {
            out.add(g(100 + i, 120));
        }
        assertThat(ledger(out)).isNotEqualTo("EXPLORER");
    }

    @Test
    void ledger_explorer_edgesOfShortRange() {
        // 60분과 300분은 안, 59분과 301분은 밖
        List<OwnedGame> in = new ArrayList<>(lib(6, 0, 1000));
        in.add(g(100, 60));
        in.add(g(101, 60));
        in.add(g(102, 300));
        in.add(g(103, 300));
        assertThat(ledger(in)).isEqualTo("EXPLORER");
        List<OwnedGame> out = new ArrayList<>(lib(6, 0, 1000));
        out.add(g(100, 59));
        out.add(g(101, 59));
        out.add(g(102, 301));
        out.add(g(103, 301));
        assertThat(ledger(out)).isNotEqualTo("EXPLORER");
    }

    @Test
    void ledger_explorer_ratioIsOverPlayedGamesNotOwned() {
        // 보유 10개 중 0분 3개(30%), 플레이한 7개 중 3개가 짧음 = 42.9%
        List<OwnedGame> l = new ArrayList<>(lib(10, 3, 1000));
        l.set(3, g(4, 120));
        l.set(4, g(5, 120));
        l.set(5, g(6, 120));
        assertThat(ledger(l)).isEqualTo("EXPLORER");
    }

    @Test
    void ledger_practical_boundary() {
        assertThat(ledger(lib(100, 14, 1000))).isEqualTo("PRACTICAL");
        assertThat(ledger(lib(100, 15, 1000))).isEqualTo("BALANCED");
    }

    @Test
    void ledger_balanced_otherwise() {
        assertThat(ledger(lib(100, 20, 1000))).isEqualTo("BALANCED");
    }

    // ---- 죄목

    @Test
    void crime_pileOfShame_needsTenZeroGames() {
        // 0분 비율 50%여도 0분 게임이 9개면 해당 없음
        assertThat(crime(lib(18, 9, 1000))).isNotEqualTo("PILE_OF_SHAME");
        assertThat(crime(lib(20, 10, 1000))).isEqualTo("PILE_OF_SHAME");
    }

    @Test
    void crime_pileOfShame_ratioBoundary() {
        assertThat(crime(lib(25, 10, 1000))).isEqualTo("PILE_OF_SHAME");
        assertThat(crime(lib(26, 10, 1000))).isNotEqualTo("PILE_OF_SHAME");
    }

    @Test
    void crime_oneGameDevotion_boundary() {
        // 총 100시간, 최다 50시간 = 50%
        List<OwnedGame> in = List.of(g(1, 3000), g(2, 1000), g(3, 1000), g(4, 1000));
        assertThat(crime(in)).isEqualTo("ONE_GAME_DEVOTION");
        List<OwnedGame> ratioOut = List.of(g(1, 2990), g(2, 1010), g(3, 1000), g(4, 1000));
        assertThat(crime(ratioOut)).isNotEqualTo("ONE_GAME_DEVOTION");
        // 총 99시간 59분이면 아님
        List<OwnedGame> hoursOut = List.of(g(1, 5999));
        assertThat(crime(hoursOut)).isNotEqualTo("ONE_GAME_DEVOTION");
        assertThat(crime(List.of(g(1, 6000)))).isEqualTo("ONE_GAME_DEVOTION");
    }

    @Test
    void crime_genreLoyalist_boundary() {
        // 게임 10개, 같은 시간, 6개는 STRATEGY 태그만, 4개는 STORY 태그만 = 60%
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            l.add(g(i, 100));
            tags.put((long) i, List.of(i <= 6 ? "STRATEGY_A" : "STORY_A"));
        }
        LibraryAnalysis a = run(l, tags);
        assertThat(a.verdict().crime().code()).isEqualTo("GENRE_LOYALIST");

        tags.put(6L, List.of("STORY_A"));
        assertThat(run(l, tags).verdict().crime().code()).isNotEqualTo("GENRE_LOYALIST");
    }

    @Test
    void crime_genreLoyalist_params() {
        LibraryContent c = LibraryFixtures.withPhrases(CONTENT, "GENRE_LOYALIST", List.of(
                LibraryFixtures.phrase("{topGroup} {topGroupRatioPct}", "{topGroup} {topGroupRatioPct}",
                        "topGroup", "topGroupRatioPct"),
                LibraryFixtures.phrase("{topGroup} {topGroupRatioPct}", "{topGroup} {topGroupRatioPct}",
                        "topGroup", "topGroupRatioPct")));
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            l.add(g(i, 100));
            tags.put((long) i, List.of(i <= 6 ? "STRATEGY_A" : "STORY_A"));
        }
        var crime = LibraryAnalyzer.analyze(l, tags, t -> 0.0, c, ID).verdict().crime();
        assertThat(crime.params()).containsOnlyKeys("topGroup", "topGroupRatioPct")
                .containsEntry("topGroup", "STRATEGY").containsEntry("topGroupRatioPct", 60);
    }

    @Test
    void crime_genreNomad_andInnocent() {
        List<String> keys = LibraryContentValidator.TAG_GROUP_KEYS;
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            l.add(g(i + 1, 100));
            tags.put((long) i + 1, List.of(keys.get(i) + "_A"));
        }
        assertThat(run(l, tags).verdict().crime().code()).isEqualTo("GENRE_NOMAD");
        // 두 묶음에만 퍼지면 다양성이 낮고 한 묶음 비율도 60% 미만이라 무죄
        List<OwnedGame> two = new ArrayList<>();
        Map<Long, List<String>> t2 = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            two.add(g(i, 100));
            t2.put((long) i, List.of(i <= 4 ? "STRATEGY_A" : "STORY_A"));
        }
        assertThat(run(two, t2).verdict().crime().code()).isEqualTo("INNOCENT");
    }

    @Test
    void sentenceCode_followsCrime() {
        LibraryAnalysis a = run(lib(20, 10, 1000));
        assertThat(a.verdict().sentence().code()).isEqualTo("SENTENCE_" + a.verdict().crime().code());
    }

    @Test
    void noContradiction_exhaustive() {
        int[] owned = {5, 10, 50, 299, 300, 800};
        double[] zero = {0, 0.14, 0.15, 0.39, 0.4, 0.5, 0.9};
        double[] top = {0.2, 0.5, 0.8};
        int checked = 0;
        for (int n : owned) {
            for (double z : zero) {
                for (double t : top) {
                  for (int zeros : new int[] {(int) Math.floor(z * n), (int) Math.ceil(z * n)}) {
                    int played = n - zeros;
                    if (played < 2) {
                        continue;
                    }
                    List<OwnedGame> l = new ArrayList<>();
                    for (int i = 0; i < zeros; i++) {
                        l.add(g(i + 1, 0));
                    }
                    int others = played - 1;
                    int otherMinutes = 600;
                    int topMinutes = (int) Math.round(t / (1 - t) * others * otherMinutes);
                    l.add(g(1000, topMinutes));
                    for (int i = 0; i < others; i++) {
                        l.add(g(2000 + i, otherMinutes));
                    }
                    LibraryAnalysis a = run(l);
                    boolean bad = a.ledger().code().equals("PRACTICAL")
                            && a.verdict().crime().code().equals("PILE_OF_SHAME");
                    assertThat(bad).as("n=%d z=%s t=%s", n, z, t).isFalse();
                    checked++;
                  }
                }
            }
        }
        assertThat(checked).isGreaterThan(60);
    }

    // ---- 지표

    // 같은 시간 게임들, exploration개는 EXPLORATION 묶음, 나머지는 STORY 묶음
    private static LibraryAnalysis groupRun(int exploration, int other) {
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 1; i <= exploration + other; i++) {
            l.add(g(i, 100));
            tags.put((long) i, List.of(i <= exploration ? "EXPLORATION_A" : "STORY_A"));
        }
        return run(l, tags);
    }

    @Test
    void stat_groupShareDoubledAndCapped() {
        assertThat(stat(groupRun(1, 3), StatCode.EXPLORATION)).isEqualTo(50);
        assertThat(stat(groupRun(3, 2), StatCode.EXPLORATION)).isEqualTo(100);
        assertThat(stat(groupRun(1, 1), StatCode.EXPLORATION)).isEqualTo(100);
        assertThat(stat(groupRun(1, 3), StatCode.STRATEGY)).isEqualTo(0);
        assertThat(stat(groupRun(1, 3), StatCode.COOP)).isEqualTo(0);
    }

    @Test
    void stat_gameSplitsAcrossGroupedTagsOnly() {
        // 묶음에 든 태그가 하나라 시간 전부가 EXPLORATION
        List<OwnedGame> l = List.of(g(1, 100));
        Map<Long, List<String>> tags = Map.of(1L, List.of("EXPLORATION_A", "Nowhere1", "Nowhere2"));
        assertThat(stat(run(l, tags), StatCode.EXPLORATION)).isEqualTo(100);
        // 두 묶음 태그면 반씩 = 각 50% -> 100, 전략은 0
        LibraryAnalysis a = run(l, Map.of(1L, List.of("EXPLORATION_A", "STORY_A")));
        assertThat(stat(a, StatCode.EXPLORATION)).isEqualTo(100);
        assertThat(stat(a, StatCode.STRATEGY)).isEqualTo(0);
    }

    @Test
    void stat_groupedMinutesIgnoreGamesWithoutGroupedTags() {
        List<OwnedGame> l = List.of(g(1, 100), g(2, 900));
        Map<Long, List<String>> tags = Map.of(1L, List.of("EXPLORATION_A"), 2L, List.of("Nowhere"));
        assertThat(stat(run(l, tags), StatCode.EXPLORATION)).isEqualTo(100);
    }

    @Test
    void stat_noMatches_nullsExceptCollection() {
        LibraryAnalysis a = run(lib(20, 5, 1000), Map.of());
        assertThat(stat(a, StatCode.EXPLORATION)).isNull();
        assertThat(stat(a, StatCode.STRATEGY)).isNull();
        assertThat(stat(a, StatCode.COOP)).isNull();
        assertThat(stat(a, StatCode.DIVERSITY)).isNull();
        assertThat(stat(a, StatCode.COLLECTION)).isNotNull();
        assertThat(a.diagnosis().favoriteTag()).isNull();
        assertThat(a.diagnosis().hiddenTaste()).isNull();
        assertThat(a.limitedData()).isTrue();
    }

    @Test
    void stat_collectionFormula() {
        // 보유 100, 0분 25: round(60*0.25 + 40*0.2) = 23
        assertThat(stat(run(lib(100, 25, 1000)), StatCode.COLLECTION)).isEqualTo(23);
        // 보유 800은 500에서 자름, 0분 0 -> 40
        assertThat(stat(run(lib(800, 0, 1000)), StatCode.COLLECTION)).isEqualTo(40);
        // 0분 전부, 보유 500 -> 100
        assertThat(stat(run(lib(500, 500, 0)), StatCode.COLLECTION)).isEqualTo(100);
    }

    @Test
    void stat_diversity_extremes() {
        assertThat(stat(run(lib(6, 0, 100)), StatCode.DIVERSITY)).isEqualTo(0);

        List<String> keys = LibraryContentValidator.TAG_GROUP_KEYS;
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            l.add(g(i + 1, 100));
            tags.put((long) i + 1, List.of(keys.get(i) + "_A"));
        }
        assertThat(stat(run(l, tags), StatCode.DIVERSITY)).isEqualTo(100);
    }

    @Test
    void stat_diversity_usesAllContentGroups() {
        // 두 묶음 균등은 ln2 / ln12 = 0.279 -> 28
        assertThat(stat(groupRun(1, 1), StatCode.DIVERSITY)).isEqualTo(28);
    }

    // ---- 기본 통계

    @Test
    void stats_basics() {
        List<OwnedGame> l = List.of(
                recent(1, 600, 120), g(2, 0), recent(3, 300, 60), g(4, 0), g(5, 100));
        LibraryAnalysis a = run(l);
        assertThat(a.stats().ownedCount()).isEqualTo(5);
        assertThat(a.stats().totalHours()).isEqualTo(16.7);
        assertThat(a.stats().zeroPlayCount()).isEqualTo(2);
        assertThat(a.stats().zeroPlayRatio()).isEqualTo(0.4);
        assertThat(a.stats().mostPlayed().appid()).isEqualTo(1L);
        assertThat(a.stats().mostPlayed().hours()).isEqualTo(10.0);
        assertThat(a.stats().mostPlayed().ratio()).isEqualTo(0.6);
        assertThat(a.stats().recentGameCount()).isEqualTo(2);
        assertThat(a.stats().recentHours()).isEqualTo(3.0);
        assertThat(a.stats().matchedCount()).isEqualTo(5);
    }

    @Test
    void stats_mostPlayedNullWhenNoPlaytime() {
        LibraryAnalysis a = run(lib(6, 6, 0));
        assertThat(a.stats().mostPlayed()).isNull();
        assertThat(a.limitedData()).isTrue();
        assertThat(a.diagnosis().concentratedGames()).isEmpty();
    }

    @Test
    void stats_topTags_excludeExcludedAndSplitEvenly() {
        // Action은 제외 태그라 나눗셈 분모에도 들어가지 않는다
        List<OwnedGame> l = List.of(g(1, 120), g(2, 60));
        Map<Long, List<String>> tags = Map.of(
                1L, List.of("Action", "A", "B"),
                2L, List.of("A"));
        LibraryAnalysis x = run(l, tags);
        assertThat(x.stats().topTags()).extracting(LibraryAnalysis.TagShare::tag).containsExactly("A", "B");
        assertThat(x.stats().topTags().get(0).hours()).isEqualTo(2.0);
        assertThat(x.stats().topTags().get(0).ratio()).isEqualTo(0.667);
        assertThat(x.diagnosis().favoriteTag()).isEqualTo("A");
    }

    @Test
    void stats_topTags_limitedToTen() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add("T" + i);
        }
        assertThat(run(List.of(g(1, 1200)), Map.of(1L, many)).stats().topTags()).hasSize(10);
    }

    @Test
    void diagnosis_concentratedTop3() {
        List<OwnedGame> l = List.of(g(1, 100), g(2, 400), g(3, 300), g(4, 200));
        assertThat(run(l).diagnosis().concentratedGames()).extracting(LibraryAnalysis.TopGame::appid)
                .containsExactly(2L, 3L, 4L);
    }

    // ---- 숨은 취향

    @Test
    void hiddenTaste_recentPath() {
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        // 전체 상위 5는 T1~T5, 최근에는 NEW가 뜬다
        for (int i = 1; i <= 5; i++) {
            l.add(g(i, 1000 * (7 - i)));
            tags.put((long) i, List.of("T" + i));
        }
        l.add(recent(6, 10, 500));
        tags.put(6L, List.of("NEW"));
        assertThat(run(l, tags).diagnosis().hiddenTaste()).isEqualTo("NEW");
    }

    @Test
    void hiddenTaste_recentTagsAlreadyInTop5_fallsBackToRarity() {
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 1; i <= 7; i++) {
            l.add(recent(i, 1000 * (8 - i), i == 1 ? 10 : 0));
            tags.put((long) i, List.of("T" + i));
        }
        // 최근 태그는 T1뿐이고 전체 상위 5에 있다, 희소도 최고는 T1이지만 순위 6~10인 T6, T7 중 T7을 고른다
        LibraryAnalysis a = LibraryAnalyzer.analyze(l, tags,
                t -> t.equals("T1") ? 99.0 : t.equals("T7") ? 9.0 : 1.0, CONTENT, ID);
        assertThat(a.diagnosis().hiddenTaste()).isEqualTo("T7");
        assertThat(a.diagnosis().hiddenTaste()).isNotEqualTo(a.diagnosis().favoriteTag());
    }

    @Test
    void hiddenTaste_rarityPath_nullWhenNoTagsBeyondTop5() {
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            l.add(recent(i, 1000 * (8 - i), i == 1 ? 10 : 0));
            tags.put((long) i, List.of("T" + i));
        }
        assertThat(LibraryAnalyzer.analyze(l, tags, t -> 9.0, CONTENT, ID).diagnosis().hiddenTaste()).isNull();
    }

    @Test
    void hiddenTaste_nullWithoutEvidence() {
        assertThat(run(lib(6, 0, 100), Map.of()).diagnosis().hiddenTaste()).isNull();
    }

    // ---- 데이터 부족

    @Test
    void limitedData_rules() {
        List<OwnedGame> five = lib(5, 0, 100);
        assertThat(run(five, tagsAll(five, "A")).limitedData()).isFalse();
        Map<Long, List<String>> four = new HashMap<>(tagsAll(five, "A"));
        four.remove(5L);
        assertThat(run(five, four).limitedData()).isTrue();
        List<OwnedGame> zero = lib(5, 5, 0);
        assertThat(run(zero, tagsAll(zero, "A")).limitedData()).isTrue();
    }

    // ---- 문구 고르기

    @Test
    void phraseIndex_isDeterministicAndMatchesFormula() {
        List<OwnedGame> l = lib(20, 10, 1000);
        LibraryAnalysis a = run(l);
        LibraryAnalysis b = run(l);
        assertThat(a.ledger().phraseIndex()).isEqualTo(b.ledger().phraseIndex());
        assertThat(a.verdict().sentence().phraseIndex()).isEqualTo(b.verdict().sentence().phraseIndex());
        String code = a.ledger().code();
        assertThat(a.ledger().phraseIndex()).isEqualTo(Math.floorMod((ID + ":" + code).hashCode(), 2));
        String sc = a.verdict().sentence().code();
        assertThat(a.verdict().sentence().phraseIndex()).isEqualTo(Math.floorMod((ID + ":" + sc).hashCode(), 2));
    }

    @Test
    void params_onlyThoseListedInChosenPhrase() {
        var p = LibraryFixtures.phrase("{ownedCount}", "{ownedCount}", "ownedCount");
        LibraryContent c = LibraryFixtures.withPhrases(CONTENT, "STARTER", List.of(p, p));
        List<OwnedGame> l = lib(6, 2, 600);
        LibraryAnalysis a = LibraryAnalyzer.analyze(l, tagsAll(l, "A"), t -> 0.0, c, ID);
        assertThat(a.ledger().params()).containsOnlyKeys("ownedCount").containsEntry("ownedCount", 6);
    }

    @Test
    void params_values() {
        String[] names = LibraryContentValidator.KNOWN_PLACEHOLDERS.toArray(new String[0]);
        var p = LibraryFixtures.phrase("x", "x", names);
        LibraryContent c = LibraryFixtures.withPhrases(CONTENT, "STARTER", List.of(p, p));
        // 보유 4개, 0분 1개, 최다 게임 3시간이 총 5시간의 60%
        List<OwnedGame> l = List.of(
                new OwnedGame(7, "Big", 180, 0, null), g(2, 60), g(3, 60), g(4, 0));
        Map<Long, List<String>> tags = Map.of(7L, List.of("STRATEGY_A"), 2L, List.of("STRATEGY_A"),
                3L, List.of("STORY_A"));
        var params = LibraryAnalyzer.analyze(l, tags, t -> 0.0, c, ID).ledger().params();
        assertThat(params).containsOnlyKeys(names);
        assertThat(params).containsEntry("ownedCount", 4).containsEntry("zeroCount", 1)
                .containsEntry("zeroRatioPct", 25).containsEntry("totalHours", 5.0)
                .containsEntry("topGame", "Big").containsEntry("topGameHours", 3.0)
                .containsEntry("topRatioPct", 60).containsEntry("topTag", "STRATEGY_A")
                .containsEntry("topTagRatioPct", 80).containsEntry("topGroup", "STRATEGY")
                .containsEntry("topGroupRatioPct", 80).containsEntry("shortPlayedRatioPct", 100);
    }

    @Test
    void params_fallbackWhenNothingToShow() {
        String[] names = LibraryContentValidator.KNOWN_PLACEHOLDERS.toArray(new String[0]);
        var p = LibraryFixtures.phrase("x", "x", names);
        LibraryContent c = LibraryFixtures.withPhrases(CONTENT, "STARTER", List.of(p, p));
        var params = LibraryAnalyzer.analyze(lib(3, 3, 0), Map.of(), t -> 0.0, c, ID).ledger().params();
        assertThat(params).containsEntry("topGame", "").containsEntry("topTag", "").containsEntry("topGroup", "")
                .containsEntry("topRatioPct", 0).containsEntry("topGroupRatioPct", 0);
    }

    // ---- 입력

    @Test
    void emptyGames_throws() {
        assertThatThrownBy(() -> run(List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 리뷰 반영

    private static LibraryAnalysis perGroup(int topMinutes) {
        // 12개 묶음에 게임 하나씩, 첫 묶음만 topMinutes 나머지는 100분
        List<String> keys = LibraryContentValidator.TAG_GROUP_KEYS;
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            l.add(g(i + 1, i == 0 ? topMinutes : 100));
            tags.put((long) i + 1, List.of(keys.get(i) + "_A"));
        }
        return run(l, tags);
    }

    @Test
    void crime_genreNomad_boundaryAt80() {
        // 930/(930+1100) 분포는 정규화 엔트로피 80.04, 960은 79.33
        LibraryAnalysis eighty = perGroup(930);
        assertThat(stat(eighty, StatCode.DIVERSITY)).isEqualTo(80);
        assertThat(eighty.verdict().crime().code()).isEqualTo("GENRE_NOMAD");
        LibraryAnalysis seventyNine = perGroup(960);
        assertThat(stat(seventyNine, StatCode.DIVERSITY)).isEqualTo(79);
        assertThat(seventyNine.verdict().crime().code()).isEqualTo("INNOCENT");
    }

    @Test
    void matched_requiresAtLeastOneNonExcludedTag() {
        List<OwnedGame> l = lib(5, 0, 100);
        Map<Long, List<String>> tags = new HashMap<>(tagsAll(l, "A"));
        tags.put(1L, List.of());
        tags.put(2L, List.of("Action", "Casual"));
        LibraryAnalysis a = run(l, tags);
        assertThat(a.stats().matchedCount()).isEqualTo(3);
        assertThat(a.limitedData()).isTrue();
    }

    @Test
    void blankName_fallsBackToAppLabel() {
        String[] names = {"topGame"};
        var p = LibraryFixtures.phrase("x", "x", names);
        LibraryContent c = LibraryFixtures.withPhrases(CONTENT, "ONE_GAME_DEVOTION", List.of(p, p));
        List<OwnedGame> l = List.of(new OwnedGame(42, "  ", 6000, 0, null), g(2, 100));
        LibraryAnalysis a = LibraryAnalyzer.analyze(l, tagsAll(l, "A"), t -> 0.0, c, ID);
        assertThat(a.verdict().crime().code()).isEqualTo("ONE_GAME_DEVOTION");
        assertThat(a.verdict().crime().params()).containsEntry("topGame", "App 42");
        assertThat(a.stats().mostPlayed().name()).isEqualTo("App 42");
        assertThat(a.diagnosis().concentratedGames().get(0).name()).isEqualTo("App 42");
    }

    @Test
    void pctParams_useRawRatios() {
        // 최다 게임 1245/10000 = 12.45%, 소수 셋째 자리로 먼저 반올림하면 0.125라 13이 되어 버린다
        String[] names = {"topRatioPct", "topTagRatioPct"};
        var p = LibraryFixtures.phrase("x", "x", names);
        LibraryContent c = LibraryFixtures.withPhrases(CONTENT, "PRACTICAL", List.of(p, p));
        List<OwnedGame> l = new ArrayList<>();
        Map<Long, List<String>> tags = new HashMap<>();
        l.add(g(1, 1245));
        for (int i = 2; i <= 9; i++) {
            l.add(g(i, 973));
        }
        l.add(g(10, 971));
        for (OwnedGame x : l) {
            tags.put(x.appid(), List.of("T" + x.appid()));
        }
        LibraryAnalysis a = LibraryAnalyzer.analyze(l, tags, t -> 0.0, c, ID);
        assertThat(a.ledger().code()).isEqualTo("PRACTICAL");
        assertThat(a.ledger().params()).containsEntry("topRatioPct", 12).containsEntry("topTagRatioPct", 12);
    }

    @Test
    void ledger_collectorWinsOverExplorer() {
        // 0분 40개(40%), 플레이한 60개 중 30개가 1~5시간(50%)
        List<OwnedGame> l = new ArrayList<>(lib(70, 40, 1000));
        for (int i = 0; i < 30; i++) {
            l.add(g(500 + i, 120));
        }
        assertThat(ledger(l)).isEqualTo("COLLECTOR");
    }

    @Test
    void crime_pileOfShameWinsOverOneGameDevotion() {
        List<OwnedGame> l = new ArrayList<>(lib(20, 20, 0));
        l.add(g(900, 9000));
        l.add(g(901, 100));
        assertThat(crime(l)).isEqualTo("PILE_OF_SHAME");
    }
}
