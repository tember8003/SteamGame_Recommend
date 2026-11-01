package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.taste.content.LocalizedText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static SteamGame.recommend.domain.library.LibraryFixtures.phrase;
import static SteamGame.recommend.domain.library.LibraryFixtures.validContent;
import static SteamGame.recommend.domain.library.LibraryFixtures.withExcluded;
import static SteamGame.recommend.domain.library.LibraryFixtures.withGroup;
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
        LibraryContent c = new LibraryContent(" ", v.tagGroups(), v.groupLabels(), v.excludedTags(), v.phrases());
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
}
