package SteamGame.recommend.domain.taste.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// v2 문구 파일의 구조를 기동할 때 한 번 검사한다, 위반을 모두 모아 한 번에 던진다
// 축은 이미 검증된 v1 축을 받는다, 문항 id에서 축이 정해지므로 축 순서에 기댄다
public final class TasteV2ContentValidator {

    static final String VERSION = "v2";
    static final int QUESTION_COUNT = 16;
    static final List<String> OPTION_KEYS = List.of("A", "B", "C", "D");
    static final int OPTION_TOTAL = 3;
    static final Set<Integer> LEFT_SCORES = Set.of(0, 1, 2, 3);

    private TasteV2ContentValidator() {
    }

    // 점수 계산이 만드는 보조 설명 키와 같은 순서, 같은 이름을 쓴다
    public static List<String> summaryKeys(TasteAxis axis) {
        String l = axis.left().code();
        String r = axis.right().code();
        return List.of(l + "_STRONG", l + "_LEANING", l + "_BALANCED", "EVEN",
                r + "_BALANCED", r + "_LEANING", r + "_STRONG");
    }

    public static void validate(TasteV2Content c, List<TasteAxis> axes) {
        List<String> errors = new ArrayList<>();
        if (!VERSION.equals(c.version())) {
            errors.add("version must be v2, was " + c.version());
        }
        checkQuestions(c.questions(), axes, errors);
        checkSummaries(c.axisSummaries(), axes, errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("taste v2 content invalid (" + errors.size() + "): " + String.join("; ", errors));
        }
    }

    private static void checkQuestions(List<TasteV2Question> qs, List<TasteAxis> axes, List<String> errors) {
        if (qs.size() != QUESTION_COUNT) {
            errors.add("questions must be " + QUESTION_COUNT + ", was " + qs.size());
        }
        for (int i = 0; i < qs.size(); i++) {
            TasteV2Question q = qs.get(i);
            String label = "question " + q.id();
            if (q.id() != i + 1) {
                errors.add("question ids must be 1.." + QUESTION_COUNT + " in list order, but position "
                        + (i + 1) + " has id " + q.id());
                continue;
            }
            TasteAxis axis = axes.get(i % axes.size());
            if (!axis.id().equals(q.axis())) {
                errors.add(label + " axis must be " + axis.id() + ", was " + q.axis());
                continue;
            }
            checkText(label + " text", q.text(), errors);
            checkOptions(label, q.options(), axis, errors);
        }
    }

    private static void checkOptions(String label, List<TasteV2Option> opts, TasteAxis axis, List<String> errors) {
        if (opts.size() != OPTION_KEYS.size()) {
            errors.add(label + " options must be 4, was " + opts.size());
            return;
        }
        if (!opts.stream().map(TasteV2Option::key).toList().equals(OPTION_KEYS)) {
            errors.add(label + " option keys must be A, B, C, D in order");
        }
        String l = axis.left().code();
        String r = axis.right().code();
        Set<Integer> leftScores = new HashSet<>();
        boolean scoresOk = true;
        for (TasteV2Option o : opts) {
            String optLabel = label + " option " + o.key();
            checkText(optLabel + " text", o.text(), errors);
            if (!o.scores().keySet().equals(Set.of(l, r))) {
                errors.add(optLabel + " scores must use only " + l + " and " + r);
                scoresOk = false;
                continue;
            }
            int ls = o.scores().get(l);
            int rs = o.scores().get(r);
            if (ls < 0 || ls > OPTION_TOTAL || rs < 0 || rs > OPTION_TOTAL) {
                errors.add(optLabel + " scores must be 0 to 3");
                scoresOk = false;
                continue;
            }
            if (ls + rs != OPTION_TOTAL) {
                errors.add(optLabel + " scores must sum to 3, was " + (ls + rs));
                scoresOk = false;
                continue;
            }
            leftScores.add(ls);
        }
        if (scoresOk && !leftScores.equals(LEFT_SCORES)) {
            errors.add(label + " options must use (3,0), (2,1), (1,2), (0,3) once each");
        }
    }

    private static void checkSummaries(Map<String, Map<String, LocalizedText>> summaries, List<TasteAxis> axes,
                                       List<String> errors) {
        Set<String> axisIds = new HashSet<>();
        for (TasteAxis axis : axes) {
            axisIds.add(axis.id());
            Map<String, LocalizedText> byKey = summaries.get(axis.id());
            if (byKey == null) {
                errors.add("axisSummaries missing axis " + axis.id());
                continue;
            }
            List<String> keys = summaryKeys(axis);
            for (String key : keys) {
                if (!byKey.containsKey(key)) {
                    errors.add("axisSummaries " + axis.id() + " missing " + key);
                } else {
                    checkText("axisSummaries " + axis.id() + " " + key, byKey.get(key), errors);
                }
            }
            for (String key : new TreeSet<>(byKey.keySet())) {
                if (!keys.contains(key)) {
                    errors.add("axisSummaries " + axis.id() + " has unknown key " + key);
                }
            }
        }
        for (String id : new TreeSet<>(summaries.keySet())) {
            if (!axisIds.contains(id)) {
                errors.add("axisSummaries has unknown axis " + id);
            }
        }
    }

    private static void checkText(String label, LocalizedText t, List<String> errors) {
        if (t == null) {
            errors.add(label + " is missing");
            return;
        }
        if (t.ko() == null || t.ko().isBlank()) {
            errors.add(label + ".ko is blank");
        }
        if (t.en() == null || t.en().isBlank()) {
            errors.add(label + ".en is blank");
        }
    }
}
