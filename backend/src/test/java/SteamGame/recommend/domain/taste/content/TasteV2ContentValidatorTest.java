package SteamGame.recommend.domain.taste.content;

import SteamGame.recommend.domain.taste.TasteFixtures;
import SteamGame.recommend.domain.taste.TasteV2Fixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static SteamGame.recommend.domain.taste.TasteV2Fixtures.AXES;
import static SteamGame.recommend.domain.taste.TasteV2Fixtures.option;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasteV2ContentValidatorTest {

    private final TasteV2Content valid = TasteV2Fixtures.validContent();

    private static void invalid(TasteV2Content c, String... messages) {
        var assertion = assertThatThrownBy(() -> TasteV2ContentValidator.validate(c, AXES))
                .isInstanceOf(IllegalStateException.class);
        for (String m : messages) {
            assertion.hasMessageContaining(m);
        }
    }

    private List<TasteV2Question> questions() {
        return new ArrayList<>(valid.questions());
    }

    private TasteV2Question replaceOptions(int id, List<TasteV2Option> options) {
        TasteV2Question q = valid.questions().get(id - 1);
        return new TasteV2Question(q.id(), q.axis(), q.text(), options);
    }

    @Test
    void validContentPasses() {
        assertThatCode(() -> TasteV2ContentValidator.validate(valid, AXES)).doesNotThrowAnyException();
    }

    @Test
    void summaryKeysUseAxisLetters() {
        assertThat(TasteV2ContentValidator.summaryKeys(AXES.get(0))).containsExactly(
                "E_STRONG", "E_LEANING", "E_BALANCED", "EVEN", "G_BALANCED", "G_LEANING", "G_STRONG");
    }

    @Test
    void versionMustBeV2() {
        invalid(new TasteV2Content("v1", valid.questions(), valid.axisSummaries()), "version must be v2");
    }

    @Test
    void questionCountMustBe16() {
        List<TasteV2Question> qs = questions();
        qs.remove(15);
        invalid(TasteV2Fixtures.withQuestions(valid, qs), "questions must be 16, was 15");
    }

    @Test
    void idsMustFollowListOrder() {
        List<TasteV2Question> qs = questions();
        TasteV2Question first = qs.get(0);
        qs.set(0, new TasteV2Question(2, first.axis(), first.text(), first.options()));
        invalid(TasteV2Fixtures.withQuestions(valid, qs), "position 1 has id 2");
    }

    // 4절 표대로 EG는 1, 5, 9, 13번처럼 id에서 축이 정해진다
    @Test
    void axisMustMatchIdPosition() {
        List<TasteV2Question> qs = questions();
        TasteV2Question q1 = qs.get(0);
        qs.set(0, new TasteV2Question(1, "NM", q1.text(), List.of(
                option("A", "N", 3, "M", 0), option("B", "N", 2, "M", 1),
                option("C", "N", 1, "M", 2), option("D", "N", 0, "M", 3))));
        invalid(TasteV2Fixtures.withQuestions(valid, qs), "question 1 axis must be EG, was NM");
    }

    @Test
    void optionsMustBeABCDInOrder() {
        List<TasteV2Question> qs = questions();
        qs.set(0, replaceOptions(1, List.of(
                option("B", "E", 3, "G", 0), option("A", "E", 2, "G", 1),
                option("C", "E", 1, "G", 2), option("D", "E", 0, "G", 3))));
        qs.set(1, replaceOptions(2, List.of(option("A", "N", 3, "M", 0), option("B", "N", 0, "M", 3))));
        invalid(TasteV2Fixtures.withQuestions(valid, qs),
                "question 1 option keys must be A, B, C, D in order",
                "question 2 options must be 4, was 2");
    }

    @Test
    void scoresMustUseOnlyTheAxisLetters() {
        List<TasteV2Question> qs = questions();
        qs.set(0, replaceOptions(1, List.of(
                option("A", "E", 3, "N", 0), option("B", "E", 2, "G", 1),
                option("C", "E", 1, "G", 2), option("D", "E", 0, "G", 3))));
        invalid(TasteV2Fixtures.withQuestions(valid, qs), "question 1 option A scores must use only E and G");
    }

    @Test
    void scoreRangeAndSumAreChecked() {
        List<TasteV2Question> qs = questions();
        qs.set(0, replaceOptions(1, List.of(
                option("A", "E", 4, "G", -1), option("B", "E", 2, "G", 2),
                option("C", "E", 1, "G", 2), option("D", "E", 0, "G", 3))));
        invalid(TasteV2Fixtures.withQuestions(valid, qs),
                "question 1 option A scores must be 0 to 3",
                "question 1 option B scores must sum to 3, was 4");
    }

    // 네 보기는 (3,0), (2,1), (1,2), (0,3)을 하나씩 써야 한다
    @Test
    void fourCombinationsMustEachAppearOnce() {
        List<TasteV2Question> qs = questions();
        qs.set(0, replaceOptions(1, List.of(
                option("A", "E", 3, "G", 0), option("B", "E", 3, "G", 0),
                option("C", "E", 1, "G", 2), option("D", "E", 0, "G", 3))));
        invalid(TasteV2Fixtures.withQuestions(valid, qs),
                "question 1 options must use (3,0), (2,1), (1,2), (0,3) once each");
    }

    @Test
    void blankTextsAreRejected() {
        List<TasteV2Question> qs = questions();
        TasteV2Question q1 = qs.get(0);
        List<TasteV2Option> opts = new ArrayList<>(q1.options());
        TasteV2Option a = opts.get(0);
        opts.set(0, new TasteV2Option("A", a.scores(), new LocalizedText("ok", " ")));
        qs.set(0, new TasteV2Question(1, "EG", new LocalizedText(" ", "en"), opts));
        invalid(TasteV2Fixtures.withQuestions(valid, qs),
                "question 1 text.ko is blank",
                "question 1 option A text.en is blank");
    }

    @Test
    void everyAxisNeedsAllSevenSummaries() {
        Map<String, Map<String, LocalizedText>> summaries = new LinkedHashMap<>(valid.axisSummaries());
        Map<String, LocalizedText> eg = new LinkedHashMap<>(summaries.get("EG"));
        eg.remove("EVEN");
        eg.put("E_WEIRD", TasteFixtures.text("x"));
        summaries.put("EG", eg);
        summaries.remove("SP");
        summaries.put("XY", Map.of());
        invalid(TasteV2Fixtures.withSummaries(valid, summaries),
                "axisSummaries EG missing EVEN",
                "axisSummaries EG has unknown key E_WEIRD",
                "axisSummaries missing axis SP",
                "axisSummaries has unknown axis XY");
    }

    @Test
    void blankSummaryRejected() {
        Map<String, Map<String, LocalizedText>> summaries = new LinkedHashMap<>(valid.axisSummaries());
        Map<String, LocalizedText> nm = new LinkedHashMap<>(summaries.get("NM"));
        nm.put("N_STRONG", new LocalizedText("ko", ""));
        summaries.put("NM", nm);
        invalid(TasteV2Fixtures.withSummaries(valid, summaries), "axisSummaries NM N_STRONG.en is blank");
    }
}
