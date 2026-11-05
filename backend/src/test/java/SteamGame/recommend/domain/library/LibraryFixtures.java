package SteamGame.recommend.domain.library;

import SteamGame.recommend.domain.library.content.GenreConditionalExclude;
import SteamGame.recommend.domain.library.content.GenreRules;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.LibraryContentValidator;
import SteamGame.recommend.domain.library.content.Phrase;
import SteamGame.recommend.domain.library.content.PickRules;
import SteamGame.recommend.domain.taste.content.LocalizedText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 모든 판정 코드와 태그 묶음을 갖춘 정상 라이브러리 문구 픽스처
public final class LibraryFixtures {

    private LibraryFixtures() {
    }

    public static LibraryContent validContent() {
        Map<String, List<String>> groups = new HashMap<>();
        for (String key : LibraryContentValidator.TAG_GROUP_KEYS) {
            groups.put(key, new ArrayList<>(List.of(key + "_A", key + "_B")));
        }
        Map<String, List<Phrase>> phrases = new HashMap<>();
        for (String code : LibraryContentValidator.requiredPhraseCodes()) {
            phrases.put(code, new ArrayList<>(List.of(phrase(code + " 1"), phrase(code + " 2"))));
        }
        Map<String, LocalizedText> labels = new HashMap<>();
        for (String key : LibraryContentValidator.TAG_GROUP_KEYS) {
            labels.put(key, new LocalizedText(key + " ko", key + " en"));
        }
        Map<String, List<String>> genres = new LinkedHashMap<>();
        for (String key : LibraryContentValidator.GENRE_KEYS) {
            genres.put(key, new ArrayList<>(List.of(key + "_G1", key + "_G2")));
        }
        return new LibraryContent("v1", groups, labels, List.of("Action", "Casual"), phrases,
                genres, List.of("Open World"), RULES, List.of(), PICKS);
    }

    // 운영 내용 파일과 같은 기준값
    public static final GenreRules RULES = new GenreRules(10, 0.3, 0.1, 0.15);
    public static final PickRules PICKS = new PickRules(10, 5, 2, 50, 70, 3, 10, 365, 3, 60, 120, 90, 3);

    public static LibraryContent withPicks(LibraryContent c, PickRules picks) {
        return new LibraryContent(c.version(), c.tagGroups(), c.groupLabels(), c.excludedTags(), c.phrases(),
                c.genres(), c.genreExcludedTags(), c.genreRules(), c.genreConditionalExcludes(), picks);
    }

    public static LibraryContent withGenres(LibraryContent c, Map<String, List<String>> genres,
                                            List<String> excluded, GenreRules rules) {
        return new LibraryContent(c.version(), c.tagGroups(), c.groupLabels(), c.excludedTags(), c.phrases(),
                genres, excluded, rules, c.genreConditionalExcludes(), c.picks());
    }

    public static LibraryContent withConditionalExcludes(LibraryContent c, List<GenreConditionalExclude> rules) {
        return new LibraryContent(c.version(), c.tagGroups(), c.groupLabels(), c.excludedTags(), c.phrases(),
                c.genres(), c.genreExcludedTags(), c.genreRules(), rules, c.picks());
    }

    // 자리표시자 없는 문구
    public static Phrase phrase(String s) {
        return new Phrase(new LocalizedText(s + " ko", s + " en"), List.of());
    }

    public static Phrase phrase(String ko, String en, String... params) {
        return new Phrase(new LocalizedText(ko, en), List.of(params));
    }

    public static LibraryContent withGroup(LibraryContent c, String key, List<String> tags) {
        Map<String, List<String>> groups = new HashMap<>(c.tagGroups());
        if (tags == null) {
            groups.remove(key);
        } else {
            groups.put(key, tags);
        }
        return new LibraryContent(c.version(), groups, c.groupLabels(), c.excludedTags(), c.phrases(), c.genres(), c.genreExcludedTags(), c.genreRules(), c.genreConditionalExcludes(), c.picks());
    }

    public static LibraryContent withPhrases(LibraryContent c, String code, List<Phrase> list) {
        Map<String, List<Phrase>> phrases = new HashMap<>(c.phrases());
        if (list == null) {
            phrases.remove(code);
        } else {
            phrases.put(code, list);
        }
        return new LibraryContent(c.version(), c.tagGroups(), c.groupLabels(), c.excludedTags(), phrases, c.genres(), c.genreExcludedTags(), c.genreRules(), c.genreConditionalExcludes(), c.picks());
    }

    public static LibraryContent withLabel(LibraryContent c, String key, LocalizedText label) {
        Map<String, LocalizedText> labels = new HashMap<>(c.groupLabels());
        if (label == null) {
            labels.remove(key);
        } else {
            labels.put(key, label);
        }
        return new LibraryContent(c.version(), c.tagGroups(), labels, c.excludedTags(), c.phrases(), c.genres(), c.genreExcludedTags(), c.genreRules(), c.genreConditionalExcludes(), c.picks());
    }

    public static LibraryContent withExcluded(LibraryContent c, List<String> excluded) {
        return new LibraryContent(c.version(), c.tagGroups(), c.groupLabels(), excluded, c.phrases(), c.genres(), c.genreExcludedTags(), c.genreRules(), c.genreConditionalExcludes(), c.picks());
    }
}
