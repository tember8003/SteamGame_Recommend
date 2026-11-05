package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.taste.content.LocalizedText;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static SteamGame.recommend.domain.library.LibraryFixtures.phrase;
import static SteamGame.recommend.domain.library.LibraryFixtures.validContent;
import static SteamGame.recommend.domain.library.LibraryFixtures.withConditionalExcludes;
import static SteamGame.recommend.domain.library.LibraryFixtures.withExcluded;
import static SteamGame.recommend.domain.library.LibraryFixtures.withGenres;
import static SteamGame.recommend.domain.library.LibraryFixtures.withGroup;
import static SteamGame.recommend.domain.library.LibraryFixtures.withPicks;
import static SteamGame.recommend.domain.library.LibraryFixtures.withLabel;
import static SteamGame.recommend.domain.library.LibraryFixtures.withPhrases;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryContentValidatorTest {

    @Test
    void validContent_passes() {
        assertThatCode(() -> LibraryContentValidator.validate(validContent())).doesNotThrowAnyException();
    }

    @Test
    void missingVerdictCode() {
        LibraryContent c = withPhrases(validContent(), "COLLECTOR", null);
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phrases missing code COLLECTOR");
    }

    @Test
    void missingSentenceCode() {
        LibraryContent c = withPhrases(validContent(), "SENTENCE_INNOCENT", null);
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("phrases missing code SENTENCE_INNOCENT");
    }

    @Test
    void zeroCandidates() {
        LibraryContent c = withPhrases(validContent(), "STARTER", List.of());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER must have 2 to 3 candidates, was 0");
    }

    @Test
    void oneCandidate() {
        LibraryContent c = withPhrases(validContent(), "STARTER", List.of(phrase("a")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER must have 2 to 3 candidates, was 1");
    }

    @Test
    void fourCandidates() {
        LibraryContent c = withPhrases(validContent(), "STARTER",
                List.of(phrase("a"), phrase("b"), phrase("c"), phrase("d")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER must have 2 to 3 candidates, was 4");
    }

    @Test
    void blankKorean() {
        LibraryContent c = withPhrases(validContent(), "STARTER",
                List.of(phrase(" ", "ok"), phrase("b")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER[0].ko is blank");
    }

    @Test
    void blankEnglish() {
        LibraryContent c = withPhrases(validContent(), "STARTER",
                List.of(phrase("b"), phrase("ok", "")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER[1].en is blank");
    }

    @Test
    void placeholderNotInParams() {
        LibraryContent c = withPhrases(validContent(), "STARTER",
                List.of(phrase("{zeroCount}개", "{zeroCount} games"), phrase("b")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER[0].ko uses {zeroCount} not in params")
                .hasMessageContaining("STARTER[0].en uses {zeroCount} not in params");
    }

    @Test
    void placeholderMissingInOneLanguage() {
        LibraryContent c = withPhrases(validContent(), "STARTER",
                List.of(phrase("{zeroCount}개", "no number", "zeroCount"), phrase("b")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("STARTER[0].en does not use params entry zeroCount");
    }

    @Test
    void unknownPlaceholderName() {
        LibraryContent c = withPhrases(validContent(), "STARTER",
                List.of(phrase("{foo}", "{foo}", "foo"), phrase("b")));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("unknown placeholder foo");
    }

    @Test
    void duplicateTagInsideGroup() {
        LibraryContent c = withGroup(validContent(), "COOP", List.of("Co-op", "Co-op"));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("group COOP tag duplicated: Co-op");
    }

    @Test
    void tagSharedByTwoGroups() {
        LibraryContent c = withGroup(validContent(), "COOP", List.of("EXPLORATION_A"));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("tag EXPLORATION_A is in both EXPLORATION and COOP");
    }

    @Test
    void emptyGroup() {
        LibraryContent c = withGroup(validContent(), "PUZZLE", List.of());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("group PUZZLE is empty");
    }

    @Test
    void missingGroup() {
        LibraryContent c = withGroup(validContent(), "STORY", null);
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("tagGroups missing group STORY");
    }

    @Test
    void missingGroupLabel() {
        LibraryContent c = withLabel(validContent(), "COOP", null);
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("groupLabels missing group COOP");
    }

    @Test
    void blankGroupLabelKorean() {
        LibraryContent c = withLabel(validContent(), "COOP", new LocalizedText(" ", "Co-op"));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("groupLabels COOP.ko is blank");
    }

    @Test
    void blankGroupLabelEnglish() {
        LibraryContent c = withLabel(validContent(), "COOP", new LocalizedText("협동", null));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("groupLabels COOP.en is blank");
    }

    @Test
    void excludedTagInsideGroup() {
        LibraryContent c = withExcluded(validContent(), List.of("Casual", "STORY_A"));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("excludedTags tag STORY_A is also in group STORY");
    }

    @Test
    void groupPlaceholdersAreKnown() {
        LibraryContent c = withPhrases(validContent(), "GENRE_LOYALIST",
                List.of(phrase("{topGroup} {topGroupRatioPct}", "{topGroup} {topGroupRatioPct}", "topGroup", "topGroupRatioPct"),
                        phrase("b")));
        assertThatCode(() -> LibraryContentValidator.validate(c)).doesNotThrowAnyException();
    }

    @Test
    void blankVersion() {
        LibraryContent v = validContent();
        LibraryContent c = new LibraryContent(" ", v.tagGroups(), v.groupLabels(), v.excludedTags(), v.phrases(),
                v.genres(), v.genreExcludedTags(), v.genreRules(), v.genreConditionalExcludes(), v.picks());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("version is blank");
    }

    @Test
    void reportsAllViolationsAtOnce() {
        LibraryContent c = withGroup(withPhrases(validContent(), "STARTER", List.of()), "PUZZLE", List.of());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("invalid (2)")
                .hasMessageContaining("STARTER")
                .hasMessageContaining("PUZZLE");
    }
    private static Map<String, List<String>> genres(LibraryContent c) {
        return new LinkedHashMap<>(c.genres());
    }

    @Test
    void genreMissing() {
        LibraryContent v = validContent();
        Map<String, List<String>> g = genres(v);
        g.remove("PUZZLE");
        LibraryContent c = withGenres(v, g, v.genreExcludedTags(), v.genreRules());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genres missing genre PUZZLE");
    }

    @Test
    void genreUnknownKey() {
        LibraryContent v = validContent();
        Map<String, List<String>> g = genres(v);
        g.put("SPACE", List.of("Space"));
        LibraryContent c = withGenres(v, g, v.genreExcludedTags(), v.genreRules());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genres has unknown genre SPACE");
    }

    @Test
    void genreEmpty() {
        LibraryContent v = validContent();
        Map<String, List<String>> g = genres(v);
        g.put("PUZZLE", List.of());
        LibraryContent c = withGenres(v, g, v.genreExcludedTags(), v.genreRules());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genres genre PUZZLE is empty");
    }

    // 한 태그가 두 장르에 있으면 게임의 대표 장르가 순서에 따라 우연히 정해진다
    @Test
    void genreTagInTwoGenres() {
        LibraryContent v = validContent();
        Map<String, List<String>> g = genres(v);
        g.put("PUZZLE", List.of("PUZZLE_G1", "RPG_ADVENTURE_G1"));
        LibraryContent c = withGenres(v, g, v.genreExcludedTags(), v.genreRules());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genres tag RPG_ADVENTURE_G1 is in both RPG_ADVENTURE and PUZZLE");
    }

    @Test
    void genreContainsExcludedBroadTag() {
        LibraryContent v = validContent();
        Map<String, List<String>> g = genres(v);
        g.put("SURVIVAL_CRAFTING", List.of("SURVIVAL_CRAFTING_G1", "Open World"));
        LibraryContent c = withGenres(v, g, v.genreExcludedTags(), v.genreRules());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genres tag Open World in SURVIVAL_CRAFTING is in genreExcludedTags");
    }

    @Test
    void genreRulesMissing() {
        LibraryContent v = validContent();
        LibraryContent c = withGenres(v, v.genres(), v.genreExcludedTags(), null);
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genreRules is missing");
    }

    @Test
    void genreRulesOutOfRange() {
        LibraryContent v = validContent();
        LibraryContent c = withGenres(v, v.genres(), v.genreExcludedTags(), new GenreRules(-1, 1.5, Double.NaN, -0.1));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("invalid (4)")
                .hasMessageContaining("minClassifiedHours")
                .hasMessageContaining("minCoverageRatio")
                .hasMessageContaining("mixedGapRatio")
                .hasMessageContaining("secondaryMinRatio");
    }

    @Test
    void genreRulesAtRangeEdgesAreValid() {
        LibraryContent v = validContent();
        LibraryContent c = withGenres(v, v.genres(), v.genreExcludedTags(), new GenreRules(0, 0, 1, 1));
        assertThatCode(() -> LibraryContentValidator.validate(c)).doesNotThrowAnyException();
    }
    @Test
    void genreBlankAndDuplicatedTags() {
        LibraryContent v = validContent();
        Map<String, List<String>> g = genres(v);
        g.put("PUZZLE", java.util.Arrays.asList("PUZZLE_G1", " ", "PUZZLE_G1"));
        LibraryContent c = withGenres(v, g, java.util.Arrays.asList("Open World", " "), v.genreRules());
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genres genre PUZZLE has a blank tag")
                .hasMessageContaining("genres genre PUZZLE tag duplicated: PUZZLE_G1")
                .hasMessageContaining("genreExcludedTags has a blank tag");
    }

    @Test
    void conditionalExcludeValidRulePasses() {
        LibraryContent c = withConditionalExcludes(validContent(), List.of(
                new GenreConditionalExclude(List.of("ACTION_SHOOTER_G1"), List.of("SURVIVAL_CRAFTING_G1"))));
        assertThatCode(() -> LibraryContentValidator.validate(c)).doesNotThrowAnyException();
    }

    @Test
    void conditionalExcludeEmptyListsRejected() {
        LibraryContent c = withConditionalExcludes(validContent(), List.of(
                new GenreConditionalExclude(List.of(), null)));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genreConditionalExcludes[0].whenAnyTag is empty")
                .hasMessageContaining("genreConditionalExcludes[0].ignoreTags is empty");
    }

    // 태그 이름을 잘못 쓰면 규칙이 아무 말 없이 꺼지므로 장르 매핑에 있는 이름만 받는다
    @Test
    void conditionalExcludeUnknownOrBlankTagRejected() {
        LibraryContent c = withConditionalExcludes(validContent(), List.of(
                new GenreConditionalExclude(List.of("Battle Royal"), List.of(" "))));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genreConditionalExcludes[0].whenAnyTag tag Battle Royal is not in genres")
                .hasMessageContaining("genreConditionalExcludes[0].ignoreTags has a blank tag");
    }

    @Test
    void conditionalExcludeSameTagOnBothSidesRejected() {
        LibraryContent c = withConditionalExcludes(validContent(), List.of(
                new GenreConditionalExclude(List.of("ACTION_SHOOTER_G1"), List.of("ACTION_SHOOTER_G1"))));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("genreConditionalExcludes[0] tag ACTION_SHOOTER_G1 is in both whenAnyTag and ignoreTags");
    }

    @Test
    void genreRulesInfiniteHoursRejected() {
        LibraryContent v = validContent();
        LibraryContent c = withGenres(v, v.genres(), v.genreExcludedTags(),
                new GenreRules(Double.POSITIVE_INFINITY, 0.3, 0.1, 0.15));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("minClassifiedHours");
    }
    @Test
    void picksMissing() {
        LibraryContent c = withPicks(validContent(), null);
        assertThatThrownBy(() -> LibraryContentValidator.validate(c)).hasMessageContaining("picks is missing");
    }

    @Test
    void picksOutOfRange() {
        LibraryContent c = withPicks(validContent(), new PickRules(0, 2, 3, -1, 101, 0, -1, 365, 3, 120, 60, 90, 0));
        assertThatThrownBy(() -> LibraryContentValidator.validate(c))
                .hasMessageContaining("picks.tasteTagCount must be 1 or more")
                .hasMessageContaining("picks.maxPlayNext must be 1 or more")
                .hasMessageContaining("picks.abandonedMinGames must be 1 or more")
                .hasMessageContaining("picks.minMatchedTags must not exceed candidateTagCount or tasteTagCount")
                .hasMessageContaining("picks counts and days must be 0 or more")
                .hasMessageContaining("picks.minPositivePct must be between 0 and 100")
                .hasMessageContaining("picks.revisitMinHours")
                .hasMessageContaining("picks.abandonedMaxMinutes must not be less than abandonedMinMinutes");
    }
    @Test
    void picksTasteTagCountLimits() {
        LibraryContent tooMany = withPicks(validContent(), new PickRules(11, 5, 2, 50, 70, 3, 10, 365, 3, 60, 120, 90, 3));
        assertThatThrownBy(() -> LibraryContentValidator.validate(tooMany))
                .hasMessageContaining("picks.tasteTagCount must be 10 or less");
        LibraryContent unreachable = withPicks(validContent(), new PickRules(1, 5, 2, 50, 70, 3, 10, 365, 3, 60, 120, 90, 3));
        assertThatThrownBy(() -> LibraryContentValidator.validate(unreachable))
                .hasMessageContaining("picks.minMatchedTags must not exceed candidateTagCount or tasteTagCount");
    }
}
