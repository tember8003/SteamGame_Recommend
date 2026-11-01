package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.taste.content.LocalizedText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 위반을 모두 모아 한 번에 던진다. 기동 시 한 번만 돌려 문구 파일의 구조 오류를 막는다
public final class LibraryContentValidator {

    public static final List<String> TAG_GROUP_KEYS =
            List.of("EXPLORATION", "STRATEGY", "COOP", "STORY", "ACTION", "RELAX", "SIMULATION", "PUZZLE",
                    "RPG", "HORROR", "SPORTS", "COMPETITIVE");
    public static final List<String> LEDGER_CODES =
            List.of("STARTER", "LEGENDARY_COLLECTOR", "COLLECTOR", "EXPLORER", "PRACTICAL", "BALANCED");
    public static final List<String> SIN_CODES =
            List.of("PILE_OF_SHAME", "ONE_GAME_DEVOTION", "GENRE_LOYALIST", "GENRE_NOMAD", "INNOCENT");
    public static final List<String> SENTENCE_CODES = SIN_CODES.stream().map(c -> "SENTENCE_" + c).toList();
    public static final List<String> STAT_LABEL_CODES =
            List.of("STAT_EXPLORATION", "STAT_STRATEGY", "STAT_COOP", "STAT_COLLECTION", "STAT_DIVERSITY");

    // 분석기가 채워 주는 자리표시자 이름
    public static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "ownedCount", "zeroCount", "zeroRatioPct", "totalHours", "topGame", "topGameHours",
            "topRatioPct", "topTag", "topTagRatioPct", "topGroup", "topGroupRatioPct", "shortPlayedRatioPct");

    static final int MIN_CANDIDATES = 2;
    static final int MAX_CANDIDATES = 3;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    private LibraryContentValidator() {
    }

    public static List<String> requiredPhraseCodes() {
        List<String> all = new ArrayList<>(LEDGER_CODES);
        all.addAll(SIN_CODES);
        all.addAll(SENTENCE_CODES);
        all.addAll(STAT_LABEL_CODES);
        return all;
    }

    public static void validate(LibraryContent c) {
        if (c == null) {
            throw new IllegalStateException("library content: null");
        }
        List<String> errors = new ArrayList<>();
        if (c.version() == null || c.version().isBlank()) {
            errors.add("version is blank");
        }
        checkTagGroups(c.tagGroups(), errors);
        checkGroupLabels(c.groupLabels(), errors);
        checkExcludedTags(c.excludedTags(), c.tagGroups(), errors);
        checkPhrases(c.phrases(), errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("library content invalid (" + errors.size() + "): " + String.join("; ", errors));
        }
    }

    private static void checkTagGroups(Map<String, List<String>> groups, List<String> errors) {
        Map<String, String> owner = new HashMap<>();
        for (String key : TAG_GROUP_KEYS) {
            List<String> tags = groups.get(key);
            if (tags == null) {
                errors.add("tagGroups missing group " + key);
                continue;
            }
            if (tags.isEmpty()) {
                errors.add("tagGroups group " + key + " is empty");
            }
            Set<String> seen = new HashSet<>();
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    errors.add("tagGroups group " + key + " has a blank tag");
                } else if (!seen.add(tag)) {
                    errors.add("tagGroups group " + key + " tag duplicated: " + tag);
                } else {
                    // 묶음이 겹치면 다양성 계산에서 같은 시간이 두 번 세어진다
                    String other = owner.putIfAbsent(tag, key);
                    if (other != null) {
                        errors.add("tagGroups tag " + tag + " is in both " + other + " and " + key);
                    }
                }
            }
        }
        for (String key : new TreeSet<>(groups.keySet())) {
            if (!TAG_GROUP_KEYS.contains(key)) {
                errors.add("tagGroups has unknown group " + key);
            }
        }
    }

    private static void checkGroupLabels(Map<String, LocalizedText> labels, List<String> errors) {
        for (String key : TAG_GROUP_KEYS) {
            LocalizedText t = labels.get(key);
            if (t == null) {
                errors.add("groupLabels missing group " + key);
                continue;
            }
            if (t.ko() == null || t.ko().isBlank()) {
                errors.add("groupLabels " + key + ".ko is blank");
            }
            if (t.en() == null || t.en().isBlank()) {
                errors.add("groupLabels " + key + ".en is blank");
            }
        }
        for (String key : new TreeSet<>(labels.keySet())) {
            if (!TAG_GROUP_KEYS.contains(key)) {
                errors.add("groupLabels has unknown group " + key);
            }
        }
    }

    // 제외 태그가 묶음에 들어가면 흔한 태그 때문에 대부분이 한 묶음 편향이 된다
    private static void checkExcludedTags(List<String> excluded, Map<String, List<String>> groups, List<String> errors) {
        for (String tag : excluded) {
            for (String key : TAG_GROUP_KEYS) {
                List<String> tags = groups.get(key);
                if (tags != null && tags.contains(tag)) {
                    errors.add("excludedTags tag " + tag + " is also in group " + key);
                }
            }
        }
    }

    private static void checkPhrases(Map<String, List<Phrase>> phrases, List<String> errors) {
        List<String> required = requiredPhraseCodes();
        for (String code : required) {
            List<Phrase> list = phrases.get(code);
            if (list == null) {
                errors.add("phrases missing code " + code);
                continue;
            }
            if (list.size() < MIN_CANDIDATES || list.size() > MAX_CANDIDATES) {
                errors.add("phrases " + code + " must have " + MIN_CANDIDATES + " to " + MAX_CANDIDATES
                        + " candidates, was " + list.size());
            }
            for (int i = 0; i < list.size(); i++) {
                checkPhrase(code + "[" + i + "]", list.get(i), errors);
            }
        }
        for (String code : new TreeSet<>(phrases.keySet())) {
            if (!required.contains(code)) {
                errors.add("phrases has unknown code " + code);
            }
        }
    }

    private static void checkPhrase(String label, Phrase p, List<String> errors) {
        if (p == null || p.text() == null) {
            errors.add("phrases " + label + " text is missing");
            return;
        }
        LocalizedText t = p.text();
        Set<String> params = new TreeSet<>(p.params());
        if (params.size() != p.params().size()) {
            errors.add("phrases " + label + " params has duplicates");
        }
        for (String name : params) {
            if (!KNOWN_PLACEHOLDERS.contains(name)) {
                errors.add("phrases " + label + " params has unknown placeholder " + name);
            }
        }
        checkSide(label + ".ko", t.ko(), params, errors);
        checkSide(label + ".en", t.en(), params, errors);
    }

    private static void checkSide(String label, String text, Set<String> params, List<String> errors) {
        if (text == null || text.isBlank()) {
            errors.add("phrases " + label + " is blank");
            return;
        }
        Set<String> used = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            used.add(m.group(1));
        }
        for (String name : used) {
            if (!params.contains(name)) {
                errors.add("phrases " + label + " uses {" + name + "} not in params");
            }
        }
        for (String name : params) {
            if (!used.contains(name)) {
                errors.add("phrases " + label + " does not use params entry " + name);
            }
        }
    }
}
