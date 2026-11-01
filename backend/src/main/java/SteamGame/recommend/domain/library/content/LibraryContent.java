package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.taste.content.LocalizedText;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

// 라이브러리 분석 문구와 태그 묶음 데이터 전체
// groupLabels는 묶음 이름 표기, excludedTags는 거의 모든 게임에 붙어 어느 묶음에도 넣지 않는 태그
// excludedNote 같은 사람이 읽는 설명 필드는 읽지 않고 넘긴다
@JsonIgnoreProperties(ignoreUnknown = true)
public record LibraryContent(
        String version,
        Map<String, List<String>> tagGroups,
        Map<String, LocalizedText> groupLabels,
        List<String> excludedTags,
        Map<String, List<Phrase>> phrases) {

    // 요청마다 같은 맵을 나눠 쓰므로 바꿀 수 없게 복사한다, null은 빈 맵으로 두어 검증기가 누락으로 잡는다
    public LibraryContent {
        tagGroups = tagGroups == null ? Map.of() : Map.copyOf(tagGroups);
        groupLabels = groupLabels == null ? Map.of() : Map.copyOf(groupLabels);
        excludedTags = excludedTags == null ? List.of() : List.copyOf(excludedTags);
        phrases = phrases == null ? Map.of() : Map.copyOf(phrases);
    }
}
