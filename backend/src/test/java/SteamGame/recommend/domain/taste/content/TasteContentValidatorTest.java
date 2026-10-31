package SteamGame.recommend.domain.taste.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static SteamGame.recommend.domain.taste.TasteFixtures.question;
import static SteamGame.recommend.domain.taste.TasteFixtures.validContent;
import static SteamGame.recommend.domain.taste.TasteFixtures.weights;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasteContentValidatorTest {

    private static TasteContent withQuestions(TasteContent c, List<TasteQuestion> questions) {
        return new TasteContent(c.version(), c.axes(), questions, c.types());
    }

    private static TasteContent withTypes(TasteContent c, List<TasteType> types) {
        return new TasteContent(c.version(), c.axes(), c.questions(), types);
    }

    private static TasteContent mapType(TasteContent c, int index, UnaryOperator<TasteType> f) {
        List<TasteType> types = new ArrayList<>(c.types());
        types.set(index, f.apply(types.get(index)));
        return withTypes(c, types);
    }

    private static TasteType withWeights(TasteType t, List<TagWeight> w) {
        return new TasteType(t.code(), t.name(), t.oneLiner(), t.description(), t.traits(), w, t.featuredAppids(), t.share());
    }

    private static TasteType withAppids(TasteType t, List<Long> ids) {
        return new TasteType(t.code(), t.name(), t.oneLiner(), t.description(), t.traits(), t.tagWeights(), ids, t.share());
    }

    private static TasteType withCode(TasteType t, String code) {
        return new TasteType(code, t.name(), t.oneLiner(), t.description(), t.traits(), t.tagWeights(), t.featuredAppids(), t.share());
    }

    @Test
    void validContent_passes() {
        assertThatCode(() -> TasteContentValidator.validate(validContent())).doesNotThrowAnyException();
    }

    @Test
    void axesOutOfOrder() {
        TasteContent c = validContent();
        List<TasteAxis> axes = new ArrayList<>(c.axes());
        java.util.Collections.swap(axes, 0, 1);
        TasteContent broken = new TasteContent(c.version(), axes, c.questions(), c.types());
        assertThatThrownBy(() -> TasteContentValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("axes must be EG, NM, HC, SP in order");
    }

    @Test
    void questionIdsNotOneToTwelve() {
        TasteContent c = validContent();
        List<TasteQuestion> qs = new ArrayList<>(c.questions());
        TasteQuestion q = qs.get(11);
        qs.set(11, question(13, q.axis(), "S", "P"));
        assertThatThrownBy(() -> TasteContentValidator.validate(withQuestions(c, qs)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("question ids must be 1..12 in list order");
    }

    @Test
    void elevenQuestions_fails() {
        TasteContent c = validContent();
        TasteContent broken = withQuestions(c, c.questions().subList(0, 11));
        assertThatThrownBy(() -> TasteContentValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("questions");
    }

    @Test
    void unevenQuestionsPerAxis_fails() {
        TasteContent c = validContent();
        List<TasteQuestion> qs = new ArrayList<>(c.questions());
        // 문항 2번(NM)을 EG로 옮기면 EG 4개, NM 2개가 된다
        qs.set(1, question(2, "EG", "E", "G"));
        assertThatThrownBy(() -> TasteContentValidator.validate(withQuestions(c, qs)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void optionSideNotInAxis_fails() {
        TasteContent c = validContent();
        List<TasteQuestion> qs = new ArrayList<>(c.questions());
        qs.set(0, question(1, "EG", "E", "N"));
        assertThatThrownBy(() -> TasteContentValidator.validate(withQuestions(c, qs)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sameSideTwice_fails() {
        TasteContent c = validContent();
        List<TasteQuestion> qs = new ArrayList<>(c.questions());
        qs.set(0, question(1, "EG", "E", "E"));
        assertThatThrownBy(() -> TasteContentValidator.validate(withQuestions(c, qs)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fifteenTypes_fails() {
        TasteContent c = validContent();
        assertThatThrownBy(() -> TasteContentValidator.validate(withTypes(c, c.types().subList(0, 15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("types");
    }

    @Test
    void duplicateTypeCode_fails() {
        TasteContent c = validContent();
        TasteContent broken = mapType(c, 1, t -> withCode(t, c.types().get(0).code()));
        assertThatThrownBy(() -> TasteContentValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void typeCodeInWrongAxisOrder_fails() {
        TasteContent c = validContent();
        // 축 순서는 E/G, N/M, H/C, S/P라서 MGHS는 올바른 코드가 아니다
        TasteContent broken = mapType(c, 0, t -> withCode(t, "MGHS"));
        assertThatThrownBy(() -> TasteContentValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MGHS");
    }

    @Test
    void blankEnglishText_fails() {
        TasteContent c = validContent();
        TasteContent broken = mapType(c, 3, t -> new TasteType(t.code(), t.name(),
                new LocalizedText("한 줄", ""), t.description(), t.traits(), t.tagWeights(), t.featuredAppids(), t.share()));
        assertThatThrownBy(() -> TasteContentValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankQuestionTextEn_fails() {
        TasteContent c = validContent();
        List<TasteQuestion> qs = new ArrayList<>(c.questions());
        TasteQuestion q = qs.get(0);
        qs.set(0, new TasteQuestion(q.id(), q.axis(), new LocalizedText("질문", " "), q.options()));
        assertThatThrownBy(() -> TasteContentValidator.validate(withQuestions(c, qs)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void weightBounds() {
        TasteContent c = validContent();
        for (double bad : new double[]{0.0, 1.2}) {
            List<TagWeight> w = new ArrayList<>(weights(6));
            w.set(0, new TagWeight("Tag0", bad));
            assertThatThrownBy(() -> TasteContentValidator.validate(mapType(c, 0, t -> withWeights(t, w))))
                    .as("weight %s", bad)
                    .isInstanceOf(IllegalStateException.class);
        }
        List<TagWeight> ok = new ArrayList<>(weights(6));
        ok.set(0, new TagWeight("Tag0", 1.0));
        assertThatCode(() -> TasteContentValidator.validate(mapType(c, 0, t -> withWeights(t, ok))))
                .doesNotThrowAnyException();
    }

    @Test
    void tagWeightCountMustBeFiveToEight() {
        TasteContent c = validContent();
        for (int n : new int[]{4, 9}) {
            assertThatThrownBy(() -> TasteContentValidator.validate(mapType(c, 0, t -> withWeights(t, weights(n)))))
                    .as("count %s", n)
                    .isInstanceOf(IllegalStateException.class);
        }
        for (int n : new int[]{5, 8}) {
            assertThatCode(() -> TasteContentValidator.validate(mapType(c, 0, t -> withWeights(t, weights(n)))))
                    .as("count %s", n)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void featuredAppidCountMustBeThreeToFive() {
        TasteContent c = validContent();
        for (List<Long> ids : List.of(List.of(1L, 2L), List.of(1L, 2L, 3L, 4L, 5L, 6L))) {
            assertThatThrownBy(() -> TasteContentValidator.validate(mapType(c, 0, t -> withAppids(t, ids))))
                    .as("ids %s", ids)
                    .isInstanceOf(IllegalStateException.class);
        }
        for (List<Long> ids : List.of(List.of(1L, 2L, 3L), List.of(1L, 2L, 3L, 4L, 5L))) {
            assertThatCode(() -> TasteContentValidator.validate(mapType(c, 0, t -> withAppids(t, ids))))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void multipleViolations_allReported() {
        TasteContent c = validContent();
        TasteContent broken = mapType(withQuestions(c, c.questions().subList(0, 11)), 0,
                t -> withAppids(t, List.of(1L)));
        assertThatThrownBy(() -> TasteContentValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("questions")
                .hasMessageContaining("featuredAppids");
    }
}
