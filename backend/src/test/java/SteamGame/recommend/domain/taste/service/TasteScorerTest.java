package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.TasteFixtures;
import SteamGame.recommend.domain.taste.content.TasteContent;
import SteamGame.recommend.domain.taste.content.TasteOption;
import SteamGame.recommend.domain.taste.content.TasteQuestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasteScorerTest {

    private final TasteContent content = TasteFixtures.validContent();

    private static List<String> repeat(String v, int n) {
        return new ArrayList<>(Collections.nCopies(n, v));
    }

    @Test
    void allA_givesLeftLetters() {
        TasteScore score = TasteScorer.score(content, repeat("A", 12));

        assertThat(score.code()).isEqualTo("ENHS");
        assertThat(score.axisScores().keySet()).containsExactly("EG", "NM", "HC", "SP");
        assertThat(score.axisScores().get("EG")).containsEntry("E", 3).containsEntry("G", 0);
        assertThat(score.axisScores().get("NM")).containsEntry("N", 3).containsEntry("M", 0);
        assertThat(score.axisScores().get("HC")).containsEntry("H", 3).containsEntry("C", 0);
        assertThat(score.axisScores().get("SP")).containsEntry("S", 3).containsEntry("P", 0);
    }

    @Test
    void allB_givesRightLetters() {
        TasteScore score = TasteScorer.score(content, repeat("B", 12));

        assertThat(score.code()).isEqualTo("GMCP");
        assertThat(score.axisScores().get("EG")).containsEntry("E", 0).containsEntry("G", 3);
    }

    @Test
    void exhaustive4096_coversAll16AndMatchesRule() {
        Set<String> codes = new HashSet<>();
        for (int mask = 0; mask < 4096; mask++) {
            List<String> answers = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                answers.add(((mask >> i) & 1) == 0 ? "A" : "B");
            }
            String code = TasteScorer.score(content, answers).code();
            assertThat(code).as("mask %d", mask).isEqualTo(expected(answers));
            codes.add(code);
        }
        assertThat(codes).hasSize(16);
    }

    // 축마다 그 축 문항 3개 중 왼쪽 글자 옵션을 고른 수가 2개 이상이면 왼쪽 글자
    private String expected(List<String> answers) {
        StringBuilder sb = new StringBuilder();
        content.axes().forEach(axis -> {
            int left = 0;
            for (TasteQuestion q : content.questions()) {
                if (!q.axis().equals(axis.id())) {
                    continue;
                }
                String picked = answers.get(q.id() - 1);
                TasteOption opt = q.options().stream().filter(o -> o.key().equals(picked)).findFirst().orElseThrow();
                if (opt.side().equals(axis.left().code())) {
                    left++;
                }
            }
            sb.append(left >= 2 ? axis.left().code() : axis.right().code());
        });
        return sb.toString();
    }

    @Test
    void deterministic() {
        List<String> answers = Arrays.asList("A", "B", "A", "B", "B", "A", "A", "A", "B", "B", "A", "B");
        TasteScore first = TasteScorer.score(content, answers);
        TasteScore second = TasteScorer.score(content, answers);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void rejects_lowercase() {
        List<String> answers = repeat("A", 12);
        answers.set(3, "a");
        assertThatThrownBy(() -> TasteScorer.score(content, answers)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_nullElement() {
        List<String> answers = repeat("A", 12);
        answers.set(5, null);
        assertThatThrownBy(() -> TasteScorer.score(content, answers)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_length11() {
        assertThatThrownBy(() -> TasteScorer.score(content, repeat("A", 11))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_length13() {
        assertThatThrownBy(() -> TasteScorer.score(content, repeat("A", 13))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_nullList() {
        assertThatThrownBy(() -> TasteScorer.score(content, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesOptionSide_notPositionOfA() {
        // 1번 문항만 A가 오른쪽 글자, B가 왼쪽 글자인 내용으로 바꾼다
        List<TasteQuestion> questions = new ArrayList<>(content.questions());
        questions.set(0, TasteFixtures.question(1, "EG", "G", "E"));
        TasteContent swapped = new TasteContent(content.version(), content.axes(), questions, content.types());

        TasteScore score = TasteScorer.score(swapped, repeat("A", 12));

        assertThat(score.axisScores().get("EG")).containsEntry("E", 2).containsEntry("G", 1);
        assertThat(score.code()).isEqualTo("ENHS");
    }
}
