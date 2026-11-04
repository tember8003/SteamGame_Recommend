package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.taste.content.LocalizedText;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 라이브러리 분석 문구와 태그 묶음 데이터 전체
// groupLabels는 묶음 이름 표기, excludedTags는 거의 모든 게임에 붙어 어느 묶음에도 넣지 않는 태그
// genres는 거점 배경용 장르별 태그, genreExcludedTags는 장르 판단에 쓰지 않는 태그, genreRules는 state 기준값
// genreConditionalExcludes는 특정 태그가 있는 게임에서만 장르 판단에서 빼는 태그
// excludedNote 같은 사람이 읽는 설명 필드는 읽지 않고 넘긴다
@JsonIgnoreProperties(ignoreUnknown = true)
public record LibraryContent(
        String version,
        Map<String, List<String>> tagGroups,
        Map<String, LocalizedText> groupLabels,
        List<String> excludedTags,
        Map<String, List<Phrase>> phrases,
        Map<String, List<String>> genres,
        List<String> genreExcludedTags,
        GenreRules genreRules,
        List<GenreConditionalExclude> genreConditionalExcludes) {

    // 요청마다 같은 객체를 나눠 쓰므로 안쪽 목록까지 바꿀 수 없게 복사한다, null은 빈 값으로 두어 검증기가 누락으로 잡는다
    // genreRules는 null 그대로 두고 검증기가 잡는다
    public LibraryContent {
        tagGroups = tagGroups == null ? Map.of() : copyLists(tagGroups);
        groupLabels = groupLabels == null ? Map.of() : Map.copyOf(groupLabels);
        excludedTags = excludedTags == null ? List.of() : List.copyOf(excludedTags);
        phrases = phrases == null ? Map.of() : copyLists(phrases);
        genres = genres == null ? Map.of() : copyLists(genres);
        genreExcludedTags = genreExcludedTags == null ? List.of() : List.copyOf(genreExcludedTags);
        genreConditionalExcludes = genreConditionalExcludes == null ? List.of() : List.copyOf(genreConditionalExcludes);
    }

    // 값 목록이 null이면 검증기가 누락으로 잡도록 빈 목록으로 둔다
    private static <V> Map<String, List<V>> copyLists(Map<String, List<V>> source) {
        Map<String, List<V>> copy = new LinkedHashMap<>();
        source.forEach((key, list) -> copy.put(key, list == null ? List.of() : List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }
}
