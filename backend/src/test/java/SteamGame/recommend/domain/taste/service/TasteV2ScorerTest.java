package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.TasteV2Fixtures;
import SteamGame.recommend.domain.taste.content.TasteV2Content;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static SteamGame.recommend.domain.taste.TasteV2Fixtures.AXES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasteV2ScorerTest {

    private final TasteV2Content content = TasteV2Fixtures.validContent();
    private static final String KEYS = "ABCD";

    private TasteV2Score score(List<String> answers) {
        return TasteV2Scorer.score(AXES, content, answers);
    }

    // EG 네 문항(1, 5, 9, 13번)에 eg의 네 글자를 넣고 나머지는 모두 A로 채운다
    private static List<String> withEg(String eg) {
        List<String> answers = new ArrayList<>(Collections.nCopies(16, "A"));
        for (int i = 0; i < 4; i++) {
            answers.set(i * 4, String.valueOf(eg.charAt(i)));
        }
        return answers;
    }

    private static int leftScore(String key) {
        return 3 - KEYS.indexOf(key);
    }

    @Test
    void allA_isAllLeftAndStrong() {
        TasteV2Score s = score(Collections.nCopies(16, "A"));
        assertThat(s.code()).isEqualTo("ENHS");
        assertThat(s.axisScores().keySet()).containsExactly("EG", "NM", "HC", "SP");
        assertThat(s.axisScores().get("EG")).containsEntry("E", 12).containsEntry("G", 0);
        AxisDetail eg = s.axisDetails().get(0);
        assertThat(eg).isEqualTo(new AxisDetail("EG", "E", "G", 12, 100, 0, "E", AxisBalance.STRONG, false, "E_STRONG"));
        assertThat(s.axisDetails()).extracting(AxisDetail::id).containsExactly("EG", "NM", "HC", "SP");
    }

    @Test
    void allD_isAllRight() {
        TasteV2Score s = score(Collections.nCopies(16, "D"));
        assertThat(s.code()).isEqualTo("GMCP");
        assertThat(s.axisDetails().get(3)).isEqualTo(
                new AxisDetail("SP", "S", "P", 12, 0, 100, "P", AxisBalance.STRONG, false, "P_STRONG"));
    }

    // 4절 7개 문구 표의 모든 왼쪽 점수를 확인한다
    @ParameterizedTest(name = "EG {0} -> L={1}")
    @CsvSource({
            "AAAA, 12, 100, STRONG, E_STRONG",
            "AAAB, 11, 92, STRONG, E_STRONG",
            "AAAC, 10, 83, STRONG, E_STRONG",
            "AAAD, 9, 75, LEANING, E_LEANING",
            "AACC, 8, 67, LEANING, E_LEANING",
            "AACD, 7, 58, BALANCED, E_BALANCED",
            "ACCD, 5, 42, BALANCED, G_BALANCED",
            "ACDD, 4, 33, LEANING, G_LEANING",
            "ADDD, 3, 25, LEANING, G_LEANING",
            "BDDD, 2, 17, STRONG, G_STRONG",
            "CDDD, 1, 8, STRONG, G_STRONG",
            "DDDD, 0, 0, STRONG, G_STRONG"
    })
    void percentBalanceAndSummaryKey(String eg, int left, int leftPercent, AxisBalance balance, String key) {
        AxisDetail d = score(withEg(eg)).axisDetails().get(0);
        assertThat(score(withEg(eg)).axisScores().get("EG")).containsEntry("E", left).containsEntry("G", 12 - left);
        assertThat(d.leftPercent()).isEqualTo(leftPercent);
        assertThat(d.rightPercent()).isEqualTo(100 - leftPercent);
        assertThat(d.balance()).isEqualTo(balance);
        assertThat(d.summaryKey()).isEqualTo(key);
        assertThat(d.tieBroken()).isFalse();
        assertThat(d.winner()).isEqualTo(left > 6 ? "E" : "G");
    }

    // 설계 문서의 해석 A 고정 예시: 1번 (1,2), 5번 (3,0), 9번 (0,3), 13번 (2,1)
    // 가장 강한 답은 5번과 9번이고 그중 id가 작은 5번 (3,0)이 기준이라 E다
    // 축의 모든 문항에서 id를 비교하면 1번 (1,2) 기준으로 G가 나오므로 그 구현을 막는다
    @Test
    void tieUsesSmallestIdAmongStrongestAnswersOnly() {
        AxisDetail d = score(withEg("CADB")).axisDetails().get(0);
        assertThat(d.leftPercent()).isEqualTo(50);
        assertThat(d.winner()).isEqualTo("E");
        assertThat(d.tieBroken()).isTrue();
        assertThat(d.balance()).isEqualTo(AxisBalance.BALANCED);
        assertThat(d.summaryKey()).isEqualTo("EVEN");
        assertThat(score(withEg("CADB")).code()).startsWith("E");
    }

    @ParameterizedTest(name = "EG {0} -> {1}")
    @CsvSource({
            // 가장 강한 답이 한쪽에만 있음
            "ACCC, E", "DBBB, G",
            // 모두 2:1이나 1:2라 전부 후보, 1번이 기준
            "BCBC, E", "CBCB, G",
            // 3:0과 0:3이 둘 다 있음, 더 앞선 쪽이 기준
            "DABC, G", "ADCB, E"
    })
    void tieBreakCases(String eg, String winner) {
        AxisDetail d = score(withEg(eg)).axisDetails().get(0);
        assertThat(d.leftPercent()).isEqualTo(50);
        assertThat(d.tieBroken()).isTrue();
        assertThat(d.winner()).isEqualTo(winner);
        assertThat(d.summaryKey()).isEqualTo("EVEN");
    }

    // 한 축의 답 조합 4^4 = 256가지를 모두 계산해 합, 비율, 승자, 동점 규칙, 분포를 확인한다
    @Test
    void exhaustive256PerAxis() {
        Map<String, Integer> balanceCount = new TreeMap<>();
        int ties = 0;
        for (int n = 0; n < 256; n++) {
            char[] eg = new char[4];
            for (int i = 0, x = n; i < 4; i++, x /= 4) {
                eg[i] = KEYS.charAt(x % 4);
            }
            String combo = new String(eg);
            AxisDetail d = score(withEg(combo)).axisDetails().get(0);
            int left = 0;
            for (char k : eg) {
                left += leftScore(String.valueOf(k));
            }
            assertThat(d.total()).isEqualTo(12);
            assertThat(d.leftPercent() + d.rightPercent()).isEqualTo(100);
            assertThat(d.leftPercent()).isEqualTo(Math.round(left * 100.0f / 12));
            if (left == 6) {
                ties++;
                assertThat(d.tieBroken()).as(combo).isTrue();
                assertThat(d.winner()).as(combo).isEqualTo(referenceTieWinner(eg));
                assertThat(d.summaryKey()).isEqualTo("EVEN");
            } else {
                assertThat(d.tieBroken()).as(combo).isFalse();
                assertThat(d.winner()).as(combo).isEqualTo(left > 6 ? "E" : "G");
                assertThat(d.summaryKey()).startsWith(d.winner() + "_");
            }
            balanceCount.merge(d.balance().name(), 1, Integer::sum);
        }
        assertThat(ties).isEqualTo(44);
        assertThat(balanceCount).containsEntry("BALANCED", 124).containsEntry("LEANING", 102).containsEntry("STRONG", 30);
    }

    // 해석 A를 테스트 안에서 따로 계산한다: 가장 강한 답들 중 가장 앞선 답의 큰 쪽
    private static String referenceTieWinner(char[] eg) {
        int best = -1;
        int bestLeft = 0;
        for (char k : eg) {
            int l = leftScore(String.valueOf(k));
            int strength = Math.abs(l - (3 - l));
            if (strength > best) {
                best = strength;
                bestLeft = l;
            }
        }
        return bestLeft > 3 - bestLeft ? "E" : "G";
    }

    // 축마다 모두 A나 모두 D를 골라 16유형 모두 나오는지 본다
    @Test
    void all16TypesReachable() {
        Set<String> codes = new HashSet<>();
        for (int mask = 0; mask < 16; mask++) {
            List<String> answers = new ArrayList<>();
            for (int id = 1; id <= 16; id++) {
                int axis = (id - 1) % 4;
                answers.add(((mask >> axis) & 1) == 0 ? "A" : "D");
            }
            codes.add(score(answers).code());
        }
        assertThat(codes).hasSize(16);
    }

    @Test
    void invalidAnswersAreRejected() {
        List<String> fifteen = new ArrayList<>(Collections.nCopies(15, "A"));
        // 17개는 앞의 16개만 읽고 나머지를 버리는 구현을 막는다
        List<String> seventeen = new ArrayList<>(Collections.nCopies(17, "A"));
        List<String> lower = new ArrayList<>(Collections.nCopies(16, "A"));
        lower.set(3, "a");
        List<String> unknown = new ArrayList<>(Collections.nCopies(16, "A"));
        unknown.set(0, "E");
        List<String> withNull = new ArrayList<>(Collections.nCopies(16, "A"));
        withNull.set(5, null);
        for (List<String> bad : Arrays.asList(null, fifteen, seventeen, lower, unknown, withNull)) {
            assertThatThrownBy(() -> score(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
