package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.content.TasteAxis;
import SteamGame.recommend.domain.taste.content.TasteV2Content;
import SteamGame.recommend.domain.taste.content.TasteV2Option;
import SteamGame.recommend.domain.taste.content.TasteV2Question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// v2 답 16개를 축별 점수, 유형 코드, 축별 비율과 보조 설명 키로 바꾸는 순수 계산을 한다
// 6:6이면 그 축에서 가장 강한 답들만 후보로 두고 그중 문항 id가 가장 작은 답의 큰 쪽을 고른다
// 기동 때 검증된 콘텐츠만 받는다고 가정한다
public final class TasteV2Scorer {

    private static final int ANSWER_COUNT = 16;
    private static final Set<String> KEYS = Set.of("A", "B", "C", "D");

    private TasteV2Scorer() {
    }

    public static TasteV2Score score(List<TasteAxis> axes, TasteV2Content content, List<String> answers) {
        validate(answers);
        Map<String, Map<String, Integer>> axisScores = new LinkedHashMap<>();
        List<AxisDetail> details = new ArrayList<>();
        StringBuilder code = new StringBuilder();
        for (TasteAxis axis : axes) {
            AxisResult res = scoreAxis(axis, content, answers);
            Map<String, Integer> sides = new LinkedHashMap<>();
            sides.put(axis.left().code(), res.left());
            sides.put(axis.right().code(), res.right());
            axisScores.put(axis.id(), sides);
            details.add(res.detail());
            code.append(res.detail().winner());
        }
        return new TasteV2Score(code.toString(), axisScores, details);
    }

    // 축 하나의 원점수와 계산 결과를 담는다
    private record AxisResult(int left, int right, AxisDetail detail) {
    }

    private static AxisResult scoreAxis(TasteAxis axis, TasteV2Content content, List<String> answers) {
        String l = axis.left().code();
        String r = axis.right().code();
        int left = 0;
        int right = 0;
        // 문항은 id 순서라 같은 강도면 먼저 본 답이 남는다
        TasteV2Option strongest = null;
        int strongestGap = -1;
        for (TasteV2Question q : content.questions()) {
            if (!q.axis().equals(axis.id())) {
                continue;
            }
            TasteV2Option o = picked(q, answers);
            int ls = o.scores().get(l);
            int rs = o.scores().get(r);
            left += ls;
            right += rs;
            int gap = Math.abs(ls - rs);
            if (gap > strongestGap) {
                strongestGap = gap;
                strongest = o;
            }
        }
        int total = left + right;
        int leftPercent = Math.round(left * 100.0f / total);
        int rightPercent = 100 - leftPercent;
        boolean tie = left == right;
        String winner;
        if (tie) {
            winner = strongest.scores().get(l) > strongest.scores().get(r) ? l : r;
        } else {
            winner = left > right ? l : r;
        }
        AxisBalance balance = AxisBalance.of(Math.max(leftPercent, rightPercent));
        String summaryKey = tie ? "EVEN" : winner + "_" + balance.name();
        return new AxisResult(left, right,
                new AxisDetail(axis.id(), l, r, total, leftPercent, rightPercent, winner, balance, tie, summaryKey));
    }

    private static TasteV2Option picked(TasteV2Question q, List<String> answers) {
        String key = answers.get(q.id() - 1);
        return q.options().stream()
                .filter(o -> o.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("옵션 없음: 문항 " + q.id()));
    }

    private static void validate(List<String> answers) {
        if (answers == null || answers.size() != ANSWER_COUNT) {
            throw new IllegalArgumentException("답은 " + ANSWER_COUNT + "개여야 한다");
        }
        for (String answer : answers) {
            if (answer == null || !KEYS.contains(answer)) {
                throw new IllegalArgumentException("답은 A, B, C, D만 가능하다");
            }
        }
    }
}
