package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.content.TasteAxis;
import SteamGame.recommend.domain.taste.content.TasteContent;
import SteamGame.recommend.domain.taste.content.TasteOption;
import SteamGame.recommend.domain.taste.content.TasteQuestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 답 12개를 축별 점수와 4글자 유형 코드로 바꾸는 순수 계산
public final class TasteScorer {

    private static final int ANSWER_COUNT = 12;

    private TasteScorer() {
    }

    public static TasteScore score(TasteContent content, List<String> answers) {
        validate(answers);

        Map<String, Map<String, Integer>> axisScores = new LinkedHashMap<>();
        for (TasteAxis axis : content.axes()) {
            Map<String, Integer> sides = new LinkedHashMap<>();
            sides.put(axis.left().code(), 0);
            sides.put(axis.right().code(), 0);
            axisScores.put(axis.id(), sides);
        }

        // 문항 id 순서가 답의 위치와 같고, 점수는 고른 옵션의 side에 준다
        for (TasteQuestion question : content.questions()) {
            String picked = answers.get(question.id() - 1);
            TasteOption option = question.options().stream()
                    .filter(o -> o.key().equals(picked))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션 없음: 문항 " + question.id()));
            axisScores.get(question.axis()).merge(option.side(), 1, Integer::sum);
        }

        StringBuilder code = new StringBuilder();
        for (TasteAxis axis : content.axes()) {
            Map<String, Integer> sides = axisScores.get(axis.id());
            boolean left = sides.get(axis.left().code()) > sides.get(axis.right().code());
            code.append(left ? axis.left().code() : axis.right().code());
        }
        return new TasteScore(code.toString(), axisScores);
    }

    private static void validate(List<String> answers) {
        if (answers == null || answers.size() != ANSWER_COUNT) {
            throw new IllegalArgumentException("답은 " + ANSWER_COUNT + "개여야 한다");
        }
        for (String answer : answers) {
            if (!"A".equals(answer) && !"B".equals(answer)) {
                throw new IllegalArgumentException("답은 A 또는 B만 가능하다");
            }
        }
    }
}
