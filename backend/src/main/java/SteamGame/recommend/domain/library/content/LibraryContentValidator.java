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
    // 순서가 동점일 때 장르를 고르는 순서다
    public static final List<String> GENRE_KEYS =
            List.of("RPG_ADVENTURE", "STRATEGY_MANAGEMENT", "ACTION_SHOOTER", "SURVIVAL_CRAFTING", "LIFE_FARMING",
                    "HORROR_MYSTERY", "RACING_SPORTS", "PUZZLE", "VISUAL_NOVEL");
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

    // 분석기가 만드는 많이 한 태그 목록(stats.topTags)의 최대 길이, 지금 해볼 게임은 이 안에서만 취향 태그를 고른다
    public static final int MAX_TASTE_TAGS = 10;

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
        checkGenres(c.genres(), c.genreExcludedTags(), errors);
        checkConditionalExcludes(c.genres(), c.genreConditionalExcludes(), errors);
        checkGenreRules(c.genreRules(), errors);
        checkPickRules(c.picks(), errors);
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

    // 한 태그가 두 장르에 있으면 게임 하나가 어느 장르인지 정할 수 없다
    // 장르 판단에서 뺀 태그가 장르에 들어가면 그 장르로 게임이 끌려간다
    private static void checkGenres(Map<String, List<String>> genres, List<String> excluded, List<String> errors) {
        Map<String, String> owner = new HashMap<>();
        for (String key : GENRE_KEYS) {
            List<String> tags = genres.get(key);
            if (tags == null) {
                errors.add("genres missing genre " + key);
                continue;
            }
            if (tags.isEmpty()) {
                errors.add("genres genre " + key + " is empty");
            }
            Set<String> seen = new HashSet<>();
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    errors.add("genres genre " + key + " has a blank tag");
                    continue;
                }
                if (!seen.add(tag)) {
                    errors.add("genres genre " + key + " tag duplicated: " + tag);
                    continue;
                }
                String other = owner.putIfAbsent(tag, key);
                if (other != null) {
                    errors.add("genres tag " + tag + " is in both " + other + " and " + key);
                }
                if (excluded.contains(tag)) {
                    errors.add("genres tag " + tag + " in " + key + " is in genreExcludedTags");
                }
            }
        }
        for (String key : new TreeSet<>(genres.keySet())) {
            if (!GENRE_KEYS.contains(key)) {
                errors.add("genres has unknown genre " + key);
            }
        }
        for (String tag : excluded) {
            if (tag == null || tag.isBlank()) {
                errors.add("genreExcludedTags has a blank tag");
            }
        }
    }

    // 태그 이름을 잘못 쓰면 규칙이 아무 말 없이 꺼지므로 장르 매핑에 있는 이름만 받는다
    // 조건 태그는 붙어 있는지만 보므로 장르 태그일 필요는 없지만 오타를 기동 시점에 잡으려고 함께 제한한다
    // 장르 밖 태그를 조건으로 쓰려면 이 제한을 풀고 운영 태그 목록 대조 테스트로 오타를 잡는다
    // 같은 태그가 양쪽에 있으면 그 태그가 있을 때 그 태그를 빼라는 뜻이 되어 의미가 없다
    private static void checkConditionalExcludes(Map<String, List<String>> genres, List<GenreConditionalExclude> rules,
                                                 List<String> errors) {
        Set<String> known = new HashSet<>();
        genres.values().forEach(known::addAll);
        for (int i = 0; i < rules.size(); i++) {
            GenreConditionalExclude r = rules.get(i);
            String at = "genreConditionalExcludes[" + i + "]";
            checkRuleTags(at + ".whenAnyTag", r.whenAnyTag(), known, errors);
            checkRuleTags(at + ".ignoreTags", r.ignoreTags(), known, errors);
            for (String tag : r.whenAnyTag()) {
                if (r.ignoreTags().contains(tag)) {
                    errors.add(at + " tag " + tag + " is in both whenAnyTag and ignoreTags");
                }
            }
        }
    }

    private static void checkRuleTags(String at, List<String> tags, Set<String> known, List<String> errors) {
        if (tags.isEmpty()) {
            errors.add(at + " is empty");
        }
        for (String tag : tags) {
            if (tag.isBlank()) {
                errors.add(at + " has a blank tag");
            } else if (!known.contains(tag)) {
                errors.add(at + " tag " + tag + " is not in genres");
            }
        }
    }

    private static void checkPickRules(PickRules r, List<String> errors) {
        if (r == null) {
            errors.add("picks is missing");
            return;
        }
        positive("tasteTagCount", r.tasteTagCount(), errors);
        positive("candidateTagCount", r.candidateTagCount(), errors);
        positive("minMatchedTags", r.minMatchedTags(), errors);
        positive("maxPlayNext", r.maxPlayNext(), errors);
        positive("maxRevisit", r.maxRevisit(), errors);
        positive("abandonedMinGames", r.abandonedMinGames(), errors);
        if (r.minMatchedTags() > r.candidateTagCount() || r.minMatchedTags() > r.tasteTagCount()) {
            errors.add("picks.minMatchedTags must not exceed candidateTagCount or tasteTagCount");
        }
        if (r.tasteTagCount() > MAX_TASTE_TAGS) {
            errors.add("picks.tasteTagCount must be " + MAX_TASTE_TAGS + " or less");
        }
        if (r.minReviews() < 0 || r.revisitIdleDays() < 0 || r.abandonedIdleDays() < 0 || r.abandonedMinMinutes() < 0) {
            errors.add("picks counts and days must be 0 or more");
        }
        if (!(r.minPositivePct() >= 0 && r.minPositivePct() <= 100)) {
            errors.add("picks.minPositivePct must be between 0 and 100");
        }
        if (!(r.revisitMinHours() >= 0 && Double.isFinite(r.revisitMinHours()))) {
            errors.add("picks.revisitMinHours must be a finite number, 0 or more");
        }
        if (r.abandonedMaxMinutes() < r.abandonedMinMinutes()) {
            errors.add("picks.abandonedMaxMinutes must not be less than abandonedMinMinutes");
        }
    }

    private static void positive(String name, int value, List<String> errors) {
        if (value < 1) {
            errors.add("picks." + name + " must be 1 or more");
        }
    }

    private static void checkGenreRules(GenreRules r, List<String> errors) {
        if (r == null) {
            errors.add("genreRules is missing");
            return;
        }
        if (!(r.minClassifiedHours() >= 0 && Double.isFinite(r.minClassifiedHours()))) {
            errors.add("genreRules.minClassifiedHours must be a finite number, 0 or more");
        }
        checkRatio("minCoverageRatio", r.minCoverageRatio(), errors);
        checkRatio("mixedGapRatio", r.mixedGapRatio(), errors);
        checkRatio("secondaryMinRatio", r.secondaryMinRatio(), errors);
    }

    // NaN도 걸러지도록 범위 안에 있는지로 비교한다
    private static void checkRatio(String name, double value, List<String> errors) {
        if (!(value >= 0 && value <= 1)) {
            errors.add("genreRules." + name + " must be between 0 and 1");
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
