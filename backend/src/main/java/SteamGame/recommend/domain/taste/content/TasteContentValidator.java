package SteamGame.recommend.domain.taste.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 위반을 모두 모아 한 번에 던진다. 기동 시 한 번만 돌려 문구 파일의 구조 오류를 막는다
public final class TasteContentValidator {

    // 점수 계산이 축 순서와 문항 id 순서에 기대므로 고정한다
    static final List<String> AXIS_ORDER = List.of("EG", "NM", "HC", "SP");
    static final int QUESTION_COUNT = 12;
    static final int QUESTIONS_PER_AXIS = 3;
    static final int TYPE_COUNT = 16;
    static final int MIN_TAGS = 5;
    static final int MAX_TAGS = 8;
    static final int MIN_FEATURED = 3;
    static final int MAX_FEATURED = 5;

    private TasteContentValidator() {
    }

    public static void validate(TasteContent c) {
        List<String> errors = new ArrayList<>();
        if (c == null) {
            throw new IllegalStateException("taste content: null");
        }
        if (isBlank(c.version())) {
            errors.add("version is blank");
        }
        Map<String, TasteAxis> axisById = checkAxes(c, errors);
        checkQuestions(c, axisById, errors);
        checkTypes(c, errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException("taste content invalid (" + errors.size() + "): " + String.join("; ", errors));
        }
    }

    private static Map<String, TasteAxis> checkAxes(TasteContent c, List<String> errors) {
        Map<String, TasteAxis> byId = new HashMap<>();
        List<TasteAxis> axes = c.axes();
        if (axes == null || axes.size() != 4) {
            errors.add("axes must be 4, was " + (axes == null ? "null" : axes.size()));
            return byId;
        }
        List<String> axisIds = axes.stream().map(a -> a == null ? null : a.id()).toList();
        if (!axisIds.equals(AXIS_ORDER)) {
            errors.add("axes must be EG, NM, HC, SP in order, was " + axisIds);
        }
        for (TasteAxis a : axes) {
            if (a == null || a.left() == null || a.right() == null || isBlank(a.id())) {
                errors.add("axes has an incomplete entry");
                continue;
            }
            byId.put(a.id(), a);
            if (a.id().length() != 2 || !a.id().substring(0, 1).equals(a.left().code())
                    || !a.id().substring(1, 2).equals(a.right().code())) {
                errors.add("axis " + a.id() + " id must be left code + right code");
            }
            checkText("axis " + a.id() + " left.label", a.left().label(), errors);
            checkText("axis " + a.id() + " right.label", a.right().label(), errors);
        }
        return byId;
    }

    private static void checkQuestions(TasteContent c, Map<String, TasteAxis> axisById, List<String> errors) {
        List<TasteQuestion> qs = c.questions();
        if (qs == null || qs.size() != QUESTION_COUNT) {
            errors.add("questions must be " + QUESTION_COUNT + ", was " + (qs == null ? "null" : qs.size()));
            if (qs == null) {
                return;
            }
        }
        for (int i = 0; i < qs.size(); i++) {
            if (qs.get(i).id() != i + 1) {
                errors.add("question ids must be 1.." + QUESTION_COUNT + " in list order, but position "
                        + (i + 1) + " has id " + qs.get(i).id());
                break;
            }
        }
        Map<String, Integer> perAxis = new HashMap<>();
        Set<Integer> ids = new HashSet<>();
        for (TasteQuestion q : qs) {
            String label = "question " + q.id();
            if (!ids.add(q.id())) {
                errors.add(label + " id duplicated");
            }
            perAxis.merge(q.axis() == null ? "null" : q.axis(), 1, Integer::sum);
            checkText(label + " text", q.text(), errors);
            TasteAxis axis = axisById.get(q.axis());
            if (axis == null) {
                if (!axisById.isEmpty()) {
                    errors.add(label + " axis unknown: " + q.axis());
                }
                continue;
            }
            List<TasteOption> opts = q.options();
            if (opts == null || opts.size() != 2) {
                errors.add(label + " options must be 2");
                continue;
            }
            TasteOption a = opts.get(0);
            TasteOption b = opts.get(1);
            if (!"A".equals(a.key()) || !"B".equals(b.key())) {
                errors.add(label + " option keys must be A, B");
            }
            Set<String> sides = new HashSet<>();
            sides.add(a.side());
            sides.add(b.side());
            if (!sides.equals(Set.of(axis.left().code(), axis.right().code()))) {
                errors.add(label + " option sides must be " + axis.left().code() + " and " + axis.right().code()
                        + ", was " + a.side() + " and " + b.side());
            }
            checkText(label + " option A", a.text(), errors);
            checkText(label + " option B", b.text(), errors);
        }
        for (String axisId : axisById.keySet()) {
            int n = perAxis.getOrDefault(axisId, 0);
            if (n != QUESTIONS_PER_AXIS) {
                errors.add("questions for axis " + axisId + " must be " + QUESTIONS_PER_AXIS + ", was " + n);
            }
        }
    }

    private static void checkTypes(TasteContent c, List<String> errors) {
        List<TasteType> types = c.types();
        if (types == null || types.size() != TYPE_COUNT) {
            errors.add("types must be " + TYPE_COUNT + ", was " + (types == null ? "null" : types.size()));
            if (types == null) {
                return;
            }
        }
        Set<String> expected = expectedCodes(c.axes());
        Set<String> seen = new HashSet<>();
        for (TasteType t : types) {
            String label = "type " + t.code();
            if (!seen.add(t.code())) {
                errors.add(label + " code duplicated");
            } else if (!expected.isEmpty() && !expected.contains(t.code())) {
                errors.add(label + " code is not a combination of the axes in axis order");
            }
            checkText(label + " name", t.name(), errors);
            checkText(label + " work", t.work(), errors);
            if (t.workAppid() <= 0) {
                errors.add(label + " workAppid must be positive, was " + t.workAppid());
            }
            checkText(label + " oneLiner", t.oneLiner(), errors);
            checkText(label + " description", t.description(), errors);
            checkText(label + " traits", t.traits(), errors);
            if (t.share() == null) {
                errors.add(label + " share is missing");
            } else {
                checkText(label + " share.title", t.share().title(), errors);
                checkText(label + " share.text", t.share().text(), errors);
            }
            List<TagWeight> tw = t.tagWeights();
            if (tw == null || tw.size() < MIN_TAGS || tw.size() > MAX_TAGS) {
                errors.add(label + " tagWeights must be " + MIN_TAGS + " to " + MAX_TAGS + ", was "
                        + (tw == null ? "null" : tw.size()));
            }
            if (tw != null) {
                Set<String> tags = new HashSet<>();
                for (TagWeight w : tw) {
                    if (isBlank(w.tag())) {
                        errors.add(label + " has a blank tag");
                    } else if (!tags.add(w.tag())) {
                        errors.add(label + " tag duplicated: " + w.tag());
                    }
                    if (!(w.weight() > 0.0 && w.weight() <= 1.0)) {
                        errors.add(label + " weight of " + w.tag() + " must be in (0, 1], was " + w.weight());
                    }
                }
            }
            List<Long> ids = t.featuredAppids();
            if (ids == null || ids.size() < MIN_FEATURED || ids.size() > MAX_FEATURED) {
                errors.add(label + " featuredAppids must be " + MIN_FEATURED + " to " + MAX_FEATURED + ", was "
                        + (ids == null ? "null" : ids.size()));
            } else if (new HashSet<>(ids).size() != ids.size()) {
                errors.add(label + " featuredAppids has duplicates");
            }
            if (ids != null) {
                for (Long id : ids) {
                    if (id == null || id <= 0) {
                        errors.add(label + " featuredAppids has an invalid appid: " + id);
                    }
                }
            }
        }
    }

    private static Set<String> expectedCodes(List<TasteAxis> axes) {
        Set<String> codes = new HashSet<>();
        if (axes == null || axes.size() != 4 || axes.stream().anyMatch(a -> a == null || a.left() == null || a.right() == null)) {
            return codes;
        }
        for (String a : sides(axes.get(0))) {
            for (String b : sides(axes.get(1))) {
                for (String c : sides(axes.get(2))) {
                    for (String d : sides(axes.get(3))) {
                        codes.add(a + b + c + d);
                    }
                }
            }
        }
        return codes;
    }

    private static List<String> sides(TasteAxis a) {
        return List.of(a.left().code(), a.right().code());
    }

    private static void checkText(String label, LocalizedText t, List<String> errors) {
        if (t == null || isBlank(t.ko())) {
            errors.add(label + ".ko is blank");
        }
        if (t == null || isBlank(t.en())) {
            errors.add(label + ".en is blank");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
