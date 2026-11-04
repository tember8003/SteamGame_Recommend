package SteamGame.recommend.domain.library.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// 조건부로 장르 판단에서 빼는 태그, 게임에 whenAnyTag 중 하나라도 있으면 그 게임에서만 ignoreTags 표를 세지 않는다
// 배틀로얄 게임에 Survival 표가 많이 붙어 생존 제작으로 가는 문제를 막으려고 둠
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenreConditionalExclude(List<String> whenAnyTag, List<String> ignoreTags) {

    // null은 빈 목록으로 두어 검증기가 누락으로 잡는다
    public GenreConditionalExclude {
        whenAnyTag = whenAnyTag == null ? List.of() : List.copyOf(whenAnyTag);
        ignoreTags = ignoreTags == null ? List.of() : List.copyOf(ignoreTags);
    }
}
